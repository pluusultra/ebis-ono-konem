package ru.konem.ebis.notify

import ru.konem.ebis.data.taskIntId
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import ru.konem.ebis.MainActivity
import ru.konem.ebis.data.Task
import ru.konem.ebis.data.TaskRepo
import java.time.LocalDate
import java.time.ZonedDateTime

enum class Slot(val code: Int) { Morning(1), Nag1(2), Nag2(3), Nag3(4), Shame(5), Evening(6) }

/** Будильники на каждый слот. После срабатывания слот сам переставляется на завтра. */
object Scheduler {

    fun times(p: Prefs): Map<Slot, Int?> {
        val nags = Prefs.nagTimes(p.nagCount)
        return mapOf(
            Slot.Morning to p.morningMin,
            Slot.Nag1 to nags.getOrNull(0),
            Slot.Nag2 to nags.getOrNull(1),
            Slot.Nag3 to nags.getOrNull(2),
            Slot.Shame to if (p.shameOn) Prefs.SHAME_MIN else null,
            Slot.Evening to if (p.eveningOn) p.eveningMin else null,
        )
    }

    fun scheduleAll(context: Context) {
        TaskRepo.init(context)
        syncTasks(context, TaskRepo.tasks)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        times(Prefs(context)).forEach { (slot, min) ->
            val pi = pending(context, slot)
            if (min == null) {
                am.cancel(pi)
                return@forEach
            }
            setAlarm(am, nextTrigger(min), pi)
        }
    }

    /** Как будильник в часах: точно в любом режиме экономии, со значком будильника в строке состояния. */
    private fun setAlarmClock(context: Context, am: AlarmManager, at: Long, pi: PendingIntent) {
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        } catch (e: SecurityException) {
            setAlarm(am, at, pi)
        }
    }

    private fun setAlarm(am: AlarmManager, at: Long, pi: PendingIntent) {
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /**
     * Будильники для задач со временем: ставим на будущие несделанные, снимаем с остальных.
     * Какие уже стоят — помним в настройках, чтобы снять лишние после удаления задачи.
     */
    fun syncTasks(context: Context, tasks: List<Task>) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = Prefs(context)
        val alarmOn = prefs.alarmOn
        val now = ZonedDateTime.now()
        val wanted = mutableSetOf<String>()
        tasks.forEach { t ->
            val min = t.time ?: return@forEach
            if (t.done) return@forEach
            val at = t.due.atStartOfDay(now.zone).plusMinutes(min.toLong())
            if (!at.isAfter(now)) return@forEach
            val pi = taskPending(context, t.id, snooze = false)
            val ms = at.toInstant().toEpochMilli()
            if (t.hot && alarmOn) setAlarmClock(context, am, ms, pi) else setAlarm(am, ms, pi)
            wanted += t.id.toString()
        }
        (prefs.taskAlarms - wanted).forEach { id ->
            id.toLongOrNull()?.let { am.cancel(taskPending(context, it, snooze = false)) }
        }
        prefs.taskAlarms = wanted
    }

    /** «Через час» из напоминалки или «Потом» из будильника ([alarm] — снова заорать будильником). */
    fun snooze(context: Context, taskId: Long, minutes: Long = 60, alarm: Boolean = false) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val at = System.currentTimeMillis() + minutes * 60_000
        val pi = taskPending(context, taskId, snooze = true)
        if (alarm) setAlarmClock(context, am, at, pi) else setAlarm(am, at, pi)
    }

    private fun taskPending(context: Context, taskId: Long, snooze: Boolean): PendingIntent {
        val kind = if (snooze) "SNOOZE_" else ""
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction("ru.konem.ebis.REMIND_" + kind + taskId)
            .putExtra(AlarmReceiver.EXTRA_TASK, taskId)
        return PendingIntent.getBroadcast(
            context, taskIntId(taskId), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun nextTrigger(minuteOfDay: Int): Long {
        val now = ZonedDateTime.now()
        var at = LocalDate.now().atStartOfDay(now.zone).plusMinutes(minuteOfDay.toLong())
        if (!at.isAfter(now.plusSeconds(5))) at = at.plusDays(1)
        return at.toInstant().toEpochMilli()
    }

    private fun pending(context: Context, slot: Slot): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction("ru.konem.ebis.ALARM_${slot.name}")
            .putExtra(AlarmReceiver.EXTRA_SLOT, slot.name)
        return PendingIntent.getBroadcast(
            context, slot.code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
