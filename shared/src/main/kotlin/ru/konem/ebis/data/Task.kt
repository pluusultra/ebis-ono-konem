package ru.konem.ebis.data

import java.time.DayOfWeek
import java.time.LocalDate

data class Task(
    val id: Long,
    val title: String,
    val desc: String,
    val due: LocalDate,
    val done: Boolean = false,
    val doneAt: Long = 0L,
    /** Напоминание в минутах от полуночи, null — без времени. */
    val time: Int? = null,
    val repeat: Repeat = Repeat.None,
    /** «Горит»: всегда наверху списка. */
    val hot: Boolean = false,
    /** Ручной порядок внутри списка. */
    val order: Long = id,
    /** Для сделанной повторяющейся задачи — id следующей копии, чтобы «вернуть» её убрало. */
    val nextId: Long = 0L,
    /** Когда задачу последний раз меняли (мс). По нему слияние с ПК решает, чья версия новее. */
    val updatedAt: Long = 0L,
    val repeatDays: Set<DayOfWeek> = emptySet(),
    /** id тегов из [TaskData.tags]. Незнакомые id (тег удалили на другом устройстве) просто не показываются. */
    val tagIds: Set<Long> = emptySet(),
)

/**
 * id задачи для уведомлений и будильников, которым нужен Int. Новые id строятся от времени и в Int не влезают,
 * поэтому сворачиваем Long и уводим в диапазон от 2^30 — подальше от служебных номеров уведомлений.
 */
fun taskIntId(taskId: Long): Int = ((taskId xor (taskId ushr 32)).toInt() and 0x3FFFFFFF) or 0x40000000

enum class Repeat(val label: String, val short: String) {
    None("Не повторять", ""),
    Daily("Каждый день", "каждый день"),
    Weekdays("По будням", "по будням"),
    Weekly("Каждую неделю", "каждую неделю"),
    Monthly("Каждый месяц", "каждый месяц"),
    SelectedDays("По дням недели", "по дням недели");

    private fun step(d: LocalDate): LocalDate = when (this) {
        None -> d
        Daily -> d.plusDays(1)
        Weekdays -> {
            var n = d.plusDays(1)
            while (n.dayOfWeek == DayOfWeek.SATURDAY || n.dayOfWeek == DayOfWeek.SUNDAY) n = n.plusDays(1)
            n
        }
        Weekly, SelectedDays -> d.plusWeeks(1)
        Monthly -> d.plusMonths(1)
    }

    fun firstDue(date: LocalDate, days: Set<DayOfWeek>): LocalDate {
        if (this != SelectedDays) return date
        val offset = days.minOfOrNull { (it.value - date.dayOfWeek.value + 7) % 7 } ?: 0
        return date.plusDays(offset.toLong())
    }

    /** Следующая дата строго после сегодняшнего дня. */
    fun next(due: LocalDate, today: LocalDate, days: Set<DayOfWeek> = emptySet()): LocalDate {
        if (this == SelectedDays && days.isNotEmpty()) {
            return firstDue(maxOf(due, today).plusDays(1), days)
        }
        var d = step(due)
        while (!d.isAfter(today)) d = step(d)
        return d
    }
}
