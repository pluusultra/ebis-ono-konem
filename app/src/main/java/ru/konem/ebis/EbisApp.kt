package ru.konem.ebis

import android.app.Application
import java.util.concurrent.Executors
import ru.konem.ebis.data.TaskRepo
import ru.konem.ebis.notify.AlarmService
import ru.konem.ebis.notify.Notifier
import ru.konem.ebis.notify.Scheduler
import ru.konem.ebis.widget.Widgets

class EbisApp : Application() {
    private val background = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        TaskRepo.init(this)
        // Напоминания перевыставляются в фоне: это десятки вызовов AlarmManager,
        // а onChanged зовётся прямо из нажатия на виджет или кнопки в уведомлении.
        TaskRepo.onChanged = { tasks ->
            background.execute { Scheduler.syncTasks(this, tasks) }
            AlarmService.onTasksChanged(tasks)
            Widgets.refresh(this)
        }
        Notifier.createChannels(this)
        Scheduler.scheduleAll(this)
    }
}
