package ru.konem.ebis

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import ru.konem.ebis.data.Repeat
import ru.konem.ebis.data.Task
import ru.konem.ebis.data.TaskRepo
import java.time.DayOfWeek
import java.time.LocalDate

class TasksViewModel(app: Application) : AndroidViewModel(app) {
    init {
        TaskRepo.init(app)
    }

    val tasks: List<Task> get() = TaskRepo.tasks

    fun add(
        title: String, desc: String, due: LocalDate, time: Int?, repeat: Repeat, hot: Boolean,
        repeatDays: Set<DayOfWeek>, tagIds: Set<Long>,
    ) = TaskRepo.add(title, desc, due, time, repeat, hot, repeatDays, tagIds)

    fun edit(
        id: Long, title: String, desc: String, due: LocalDate, time: Int?, repeat: Repeat, hot: Boolean,
        repeatDays: Set<DayOfWeek>, tagIds: Set<Long>,
    ) = TaskRepo.edit(id, title, desc, due, time, repeat, hot, repeatDays, tagIds)

    fun toggle(id: Long): Boolean = TaskRepo.toggle(id)
    fun postpone(id: Long) = TaskRepo.postpone(id)
    fun delete(id: Long) = TaskRepo.delete(id)
    fun reorder(ids: List<Long>) = TaskRepo.reorder(ids)
    fun snapshot() = TaskRepo.snapshot()
    fun restore(s: List<Task>) = TaskRepo.restore(s)
}
