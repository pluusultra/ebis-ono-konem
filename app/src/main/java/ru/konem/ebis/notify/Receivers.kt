package ru.konem.ebis.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import ru.konem.ebis.data.TaskRepo
import java.time.LocalDate

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK, -1)
        if (taskId >= 0) {
            // Горит и со временем — орёт будильником, остальные — обычная напоминалка.
            TaskRepo.init(context)
            val t = TaskRepo.tasks.firstOrNull { it.id == taskId && !it.done }
            val alarm = t != null && t.hot && Prefs(context).alarmOn
            if (!alarm || !AlarmService.start(context, taskId)) Notifier.remind(context, taskId)
            return
        }
        val slot = intent.getStringExtra(EXTRA_SLOT)?.let { runCatching { Slot.valueOf(it) }.getOrNull() }
        if (slot != null) Notifier.fire(context, slot)
        Scheduler.scheduleAll(context)
    }

    companion object {
        const val EXTRA_SLOT = "slot"
        const val EXTRA_TASK = "remindTask"
    }
}

/** Кнопки в уведомлениях. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TaskRepo.init(context)
        val taskId = intent.getLongExtra(EXTRA_TASK, -1)
        val notifId = intent.getIntExtra(EXTRA_NOTIF, -1)
        if (notifId >= 0) Notifier.cancel(context, notifId)

        val msg = when (intent.action) {
            ACTION_ALARM_DONE -> AlarmService.answer(context, taskId, AlarmService.Answer.Done)
            ACTION_ALARM_SNOOZE -> AlarmService.answer(context, taskId, AlarmService.Answer.Snooze)
            ACTION_ALARM_TOMORROW -> AlarmService.answer(context, taskId, AlarmService.Answer.Tomorrow)
            ACTION_DONE -> {
                TaskRepo.setDone(taskId, sync = true)
                Prefs(context).resetSnoozes(taskId)
                Notifier.cancelForTask(context, taskId)
                if (Notifier.checkAllDone(context) != null) {
                    Notifier.celebrate(context)
                    null
                } else NotifCopy.toastDone
            }
            ACTION_TOMORROW -> {
                TaskRepo.postpone(taskId, sync = true)
                Prefs(context).resetSnoozes(taskId)
                Notifier.cancelForTask(context, taskId)
                NotifCopy.toastTomorrow
            }
            ACTION_DELETE -> {
                TaskRepo.delete(taskId, sync = true)
                Notifier.cancelForTask(context, taskId)
                NotifCopy.toastDelete
            }
            ACTION_SNOOZE -> {
                Scheduler.snooze(context, taskId)
                NotifCopy.toastSnooze
            }
            ACTION_MUTE -> {
                Prefs(context).mutedDay = LocalDate.now().toEpochDay()
                Notifier.cancelAll(context)
                NotifCopy.toastMute
            }
            else -> null
        }
        msg?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        const val ACTION_DONE = "ru.konem.ebis.DONE"
        const val ACTION_TOMORROW = "ru.konem.ebis.TOMORROW"
        const val ACTION_DELETE = "ru.konem.ebis.DELETE"
        const val ACTION_MUTE = "ru.konem.ebis.MUTE"
        const val ACTION_SNOOZE = "ru.konem.ebis.SNOOZE"
        const val ACTION_ALARM_DONE = "ru.konem.ebis.ALARM_DONE"
        const val ACTION_ALARM_SNOOZE = "ru.konem.ebis.ALARM_SNOOZE"
        const val ACTION_ALARM_TOMORROW = "ru.konem.ebis.ALARM_TOMORROW"
        const val EXTRA_TASK = "taskId"
        const val EXTRA_NOTIF = "notifId"
    }
}

/** После перезагрузки, обновления и смены времени будильники надо выставить заново. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.scheduleAll(context)
    }
}
