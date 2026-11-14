package ru.konem.ebis.notify

import ru.konem.ebis.data.taskIntId
import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import ru.konem.ebis.AlarmActivity
import ru.konem.ebis.MainActivity
import ru.konem.ebis.R
import ru.konem.ebis.data.DaySnapshot
import ru.konem.ebis.data.TaskRepo
import java.time.temporal.ChronoUnit

object Notifier {
    private const val CH_MORNING = "morning"
    private const val CH_NAG = "nag"
    private const val CH_SHAME = "shame"
    private const val CH_EVENING = "evening"
    private const val CH_CELEBRATE = "celebrate"
    private const val CH_REMIND = "remind"
    private const val CH_ALARM = "alarm"

    const val ID_MORNING = 1
    const val ID_NAG = 2
    const val ID_SHAME = 3
    const val ID_EVENING = 4
    const val ID_CELEBRATE = 5
    /** Будильник «Горит»: одно уведомление службы, которое орёт. */
    const val ID_ALARM = 7
    /** Напоминалки по задачам: 100000 + id задачи. */
    private const val ID_REMIND_BASE = 100_000

    private const val EXTRA_TASK = "ebis.taskId"

    private enum class Act(val action: String, val label: String) {
        Done(ActionReceiver.ACTION_DONE, NotifCopy.actionDone),
        Tomorrow(ActionReceiver.ACTION_TOMORROW, NotifCopy.actionTomorrow),
        Delete(ActionReceiver.ACTION_DELETE, NotifCopy.actionDelete),
        Mute(ActionReceiver.ACTION_MUTE, NotifCopy.actionMute),
        Snooze(ActionReceiver.ACTION_SNOOZE, NotifCopy.actionSnooze),
    }

    /** Итог закрытия последней задачи на сегодня. */
    class AllDone(val streak: Int, val record: Boolean)

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        listOf(
            NotificationChannel(CH_MORNING, "Утренний пинок", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Сколько херни на сегодня" },
            NotificationChannel(CH_NAG, "Дневное нытьё", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Напоминалки в течение дня" },
            NotificationChannel(CH_SHAME, "Позорный столб", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Про самую протухшую задачу" },
            NotificationChannel(CH_EVENING, "Вечерний итог", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Что сделал, что проебал" },
            NotificationChannel(CH_CELEBRATE, "Поздравлялки", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Когда всё закрыл" },
            NotificationChannel(CH_REMIND, "Напоминалки по задачам", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Задачи со временем: орёт ровно в назначенную минуту" },
            // Звук и вибрацию будильника делает сама служба: так громкость нарастает и уважается беззвучный режим.
            NotificationChannel(CH_ALARM, "Будильник «Горит»", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Горящие задачи со временем: орёт мелодией, пока не ответишь"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        ).forEach(nm::createNotificationChannel)
    }

    fun canPost(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** force = true — для кнопки «проверить» в настройках: игнорирует «отъебись» и пустые списки. */
    fun fire(context: Context, slot: Slot, force: Boolean = false) {
        TaskRepo.init(context)
        val prefs = Prefs(context)
        val s = DaySnapshot(TaskRepo.tasks)
        if (!force && prefs.mutedDay == s.today.toEpochDay()) return

        when (slot) {
            Slot.Morning -> {
                val m = NotifCopy.morning(s.remaining.size, s.overdue.size)
                post(context, ID_MORNING, CH_MORNING, m, actions = listOf(Act.Mute))
            }
            Slot.Nag1, Slot.Nag2, Slot.Nag3 -> {
                val task = s.remaining.firstOrNull()
                if (task == null) {
                    if (force) post(context, ID_NAG, CH_NAG, NotifCopy.nag("Пример задачи", 1, 3))
                    return
                }
                val m = NotifCopy.nag(task.title, s.doneToday, s.remaining.size)
                post(context, ID_NAG, CH_NAG, m, task.id, listOf(Act.Done, Act.Tomorrow, Act.Mute))
            }
            Slot.Shame -> {
                val task = s.overdue.firstOrNull()
                if (task == null) {
                    if (force) post(context, ID_SHAME, CH_SHAME, NotifCopy.shame("Дописать диплом", 214, 2))
                    return
                }
                val days = ChronoUnit.DAYS.between(task.due, s.today).toInt()
                val m = NotifCopy.shame(task.title, days, s.overdue.size - 1)
                post(context, ID_SHAME, CH_SHAME, m, task.id, listOf(Act.Done, Act.Tomorrow, Act.Delete))
            }
            Slot.Evening -> {
                val m = NotifCopy.evening(s.doneToday, s.remaining.size, s.tomorrow)
                post(context, ID_EVENING, CH_EVENING, m)
            }
        }
    }

    /** Напоминалка про задачу со временем. Тишина «на сегодня» её не глушит: время ставил сам. */
    fun remind(context: Context, taskId: Long) {
        TaskRepo.init(context)
        val task = TaskRepo.tasks.firstOrNull { it.id == taskId && !it.done } ?: return
        val id = taskIntId(taskId)
        post(context, id, CH_REMIND, NotifCopy.remind(task.title), task.id, listOf(Act.Done, Act.Snooze, Act.Tomorrow))
    }

    /**
     * Если только что закрыта последняя задача на сегодня — итог с серией (один раз в день), иначе null.
     * Заодно убирает висящее нытьё: ныть больше не о чем.
     */
    fun checkAllDone(context: Context): AllDone? {
        val prefs = Prefs(context)
        val s = DaySnapshot(TaskRepo.tasks)
        if (s.remaining.isNotEmpty() || s.doneToday == 0) return null
        val today = s.today.toEpochDay()
        if (prefs.celebratedDay == today) return null
        prefs.celebratedDay = today
        cancel(context, ID_NAG)

        val streak = if (prefs.streakDay == today - 1) prefs.streak + 1 else 1
        val record = streak > prefs.bestStreak
        prefs.streak = streak
        prefs.streakDay = today
        if (record) prefs.bestStreak = streak
        return AllDone(streak, record)
    }

    fun celebrate(context: Context) {
        if (Prefs(context).mutedDay == java.time.LocalDate.now().toEpochDay()) return
        post(context, ID_CELEBRATE, CH_CELEBRATE, NotifCopy.celebrate)
    }

    /** Уведомление будильника: на заблокированном экране открывает тревогу на весь экран. */
    fun alarm(context: Context, r: AlarmService.Ringing): android.app.Notification {
        val screen = PendingIntent.getActivity(
            context, ID_ALARM,
            Intent(context, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        fun act(action: String, n: Int, label: String): NotificationCompat.Action {
            val i = Intent(context, ActionReceiver::class.java).setAction(action).putExtra(ActionReceiver.EXTRA_TASK, r.id)
            val pi = PendingIntent.getBroadcast(context, ID_ALARM * 10 + n, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return NotificationCompat.Action(0, label, pi)
        }
        val msg = NotifCopy.alarm(r.at, r.snoozes)
        return NotificationCompat.Builder(context, CH_ALARM)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setColor(0xFFE5383B.toInt())
            .setLargeIcon(ContextCompat.getDrawable(context, R.drawable.horse_lol)?.toBitmap(192, 192))
            .setContentTitle("🔥 ${msg.title}: ${r.title}")
            .setContentText(msg.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg.text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(screen, true)
            .setContentIntent(screen)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(act(ActionReceiver.ACTION_ALARM_DONE, 0, NotifCopy.actionDone))
            .addAction(act(ActionReceiver.ACTION_ALARM_SNOOZE, 1, NotifCopy.alarmSnooze(r.snoozeMin, r.snoozes)))
            .addAction(act(ActionReceiver.ACTION_ALARM_TOMORROW, 2, NotifCopy.actionTomorrow))
            .build()
    }

    /** Android 14+: выскочить на весь экран можно, только если человек разрешил. */
    fun canFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() != false

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    fun cancelAll(context: Context) = NotificationManagerCompat.from(context).cancelAll()

    /** Убирает уведомления, которые говорят про эту задачу. */
    fun cancelForTask(context: Context, taskId: Long) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.activeNotifications
            .filter { it.notification.extras.getLong(EXTRA_TASK, -1) == taskId }
            .forEach { nm.cancel(it.id) }
    }

    @SuppressLint("MissingPermission")
    private fun post(
        context: Context,
        id: Int,
        channel: String,
        msg: NotifCopy.Msg,
        taskId: Long? = null,
        actions: List<Act> = emptyList(),
    ) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val horse = ContextCompat.getDrawable(context, R.drawable.horse_lol)?.toBitmap(192, 192)

        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setColor(0xFFB4582A.toInt())
            .setLargeIcon(horse)
            .setContentTitle(msg.title)
            .setContentText(msg.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg.text))
            .setContentIntent(open)
            .setAutoCancel(true)
        if (taskId != null) b.addExtras(android.os.Bundle().apply { putLong(EXTRA_TASK, taskId) })

        actions.forEach { a ->
            val i = Intent(context, ActionReceiver::class.java)
                .setAction(a.action)
                .putExtra(ActionReceiver.EXTRA_TASK, taskId ?: -1L)
                .putExtra(ActionReceiver.EXTRA_NOTIF, id)
            val pi = PendingIntent.getBroadcast(
                context, id * 10 + a.ordinal, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            b.addAction(0, a.label, pi)
        }
        NotificationManagerCompat.from(context).notify(id, b.build())
    }
}
