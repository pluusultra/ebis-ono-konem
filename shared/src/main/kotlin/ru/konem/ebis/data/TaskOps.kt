package ru.konem.ebis.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Все действия над списком задач — чистые функции: на вход список, на выход новый.
 * Одни и те же правила на телефоне и на ПК, поэтому файл синхронизации они понимают одинаково.
 */
object TaskOps {
    /** От текущего времени: задачи, созданные на телефоне и на ПК, не получат одинаковый id. */
    fun nextId(tasks: List<Task>) = maxOf((tasks.maxOfOrNull { it.id } ?: 0L) + 1, System.currentTimeMillis())

    fun nextOrder(tasks: List<Task>) = (tasks.maxOfOrNull { it.order } ?: 0L) + 1

    fun add(
        tasks: List<Task>, title: String, desc: String, due: LocalDate,
        time: Int? = null, repeat: Repeat = Repeat.None, hot: Boolean = false,
        repeatDays: Set<DayOfWeek> = emptySet(), tagIds: Set<Long> = emptySet(),
    ): Pair<List<Task>, Long> {
        val id = nextId(tasks)
        val task = Task(
            id = id, title = title, desc = desc, due = repeat.firstDue(due, repeatDays), time = time,
            repeat = repeat, hot = hot, order = nextOrder(tasks), repeatDays = repeatDays, tagIds = tagIds,
        )
        return tasks + task to id
    }

    /** Правка: меняются только поля, которые есть в форме. [tagIds] = null — теги не трогаем. */
    fun edit(
        tasks: List<Task>, id: Long, title: String, desc: String, due: LocalDate,
        time: Int?, repeat: Repeat, hot: Boolean, repeatDays: Set<DayOfWeek> = emptySet(),
        tagIds: Set<Long>? = null,
    ) = tasks.map {
        if (it.id != id) return@map it
        val scheduleChanged = due != it.due || repeat != it.repeat || repeatDays != it.repeatDays
        it.copy(
            title = title, desc = desc, due = if (scheduleChanged) repeat.firstDue(due, repeatDays) else due,
            time = time, repeat = repeat, hot = hot, repeatDays = repeatDays, tagIds = tagIds ?: it.tagIds,
        )
    }

    /** Второй элемент — новое состояние: true, если задача теперь сделана. */
    fun toggle(tasks: List<Task>, id: Long, today: LocalDate = LocalDate.now()): Pair<List<Task>, Boolean> {
        val t = tasks.firstOrNull { it.id == id } ?: return tasks to false
        if (!t.done) return complete(tasks, t, today) to true
        // Возвращаем: следующая копия повторяющейся задачи больше не нужна, если её ещё не трогали.
        val drop = tasks.firstOrNull { it.id == t.nextId && !it.done }?.id
        return tasks.filterNot { it.id == drop }.map {
            if (it.id == id) it.copy(done = false, doneAt = 0L, nextId = 0L) else it
        } to false
    }

    fun setDone(tasks: List<Task>, id: Long, today: LocalDate = LocalDate.now()): List<Task> =
        tasks.firstOrNull { it.id == id && !it.done }?.let { complete(tasks, it, today) } ?: tasks

    /** Сделано. У повторяющейся задачи сразу появляется следующая копия на новую дату. */
    private fun complete(tasks: List<Task>, t: Task, today: LocalDate): List<Task> {
        val now = System.currentTimeMillis()
        if (t.repeat == Repeat.None) return tasks.map { if (it.id == t.id) it.copy(done = true, doneAt = now) else it }
        val newId = nextId(tasks)
        val next = t.copy(id = newId, due = t.repeat.next(t.due, today, t.repeatDays), done = false, doneAt = 0L, nextId = 0L)
        return tasks.map { if (it.id == t.id) it.copy(done = true, doneAt = now, nextId = newId) else it } + next
    }

    /** «Завтра, честно»: просроченное и сегодняшнее — на завтра, будущее — ещё на день. */
    fun postpone(tasks: List<Task>, id: Long, today: LocalDate = LocalDate.now()) = tasks.map {
        if (it.id == id) it.copy(due = (if (it.due.isAfter(today)) it.due else today).plusDays(1)) else it
    }

    fun delete(tasks: List<Task>, id: Long) = tasks.filterNot { it.id == id }

    /** Новый ручной порядок: переданные задачи получают порядок по списку, остальные не трогаем. */
    fun reorder(tasks: List<Task>, ids: List<Long>): List<Task> {
        val orders = ids.mapNotNull { id -> tasks.firstOrNull { it.id == id }?.order }.sorted()
        val newOrder = ids.zip(orders).toMap()
        return tasks.map { t -> newOrder[t.id]?.let { t.copy(order = it) } ?: t }
    }

    /**
     * Переход old → new: изменённые и новые задачи получают updatedAt = сейчас, пропавшие — надгробие,
     * вернувшиеся (через «Верни») — снимают его.
     */
    fun stamp(old: List<Task>, new: List<Task>, deleted: Map<Long, Long>, now: Long = System.currentTimeMillis()): TaskData {
        val before = old.associateBy { it.id }
        val stamped = new.map { t ->
            val o = before[t.id]
            if (o != null && o.copy(updatedAt = 0) == t.copy(updatedAt = 0)) t else t.copy(updatedAt = now)
        }
        val alive = stamped.map { it.id }.toSet()
        return TaskData(stamped, deleted.filterKeys { it !in alive } + before.keys.filter { it !in alive }.associateWith { now })
    }

    /** То же, но теги и их надгробия переезжают из [data] как есть. */
    fun stamp(data: TaskData, new: List<Task>, now: Long = System.currentTimeMillis()): TaskData =
        stamp(data.tasks, new, data.deleted, now).copy(tags = data.tags, deletedTags = data.deletedTags)
}

/** Срез задач на конкретный день: всё, что нужно уведомлениям и подписи в шапке. */
class DaySnapshot(tasks: List<Task>, val today: LocalDate = LocalDate.now()) {
    /** Не сделано и срок сегодня или раньше. Самые старые первыми. */
    val remaining = tasks.filter { !it.done && !it.due.isAfter(today) }.sortedWith(compareBy({ it.due }, { it.id }))
    val overdue = remaining.filter { it.due.isBefore(today) }
    val doneToday = tasks.count { it.done && it.doneAt > 0 && it.doneAt.toLocalDate() == today }
    val tomorrow = tasks.count { !it.done && it.due == today.plusDays(1) }
}

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
