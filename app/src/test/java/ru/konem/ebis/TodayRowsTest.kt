package ru.konem.ebis

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.konem.ebis.data.Task
import ru.konem.ebis.data.TaskOps
import ru.konem.ebis.widget.TICK_GRACE_MS
import ru.konem.ebis.widget.todayRows
import java.time.LocalDate

class TodayRowsTest {
    private val today = LocalDate.now()

    private fun tasks() = listOf(
        Task(1, "a", "", today, order = 1),
        Task(2, "b", "", today, order = 2),
        Task(3, "c", "", today, order = 3, hot = true),
        Task(4, "d", "", today.minusDays(2), order = 4),
        Task(5, "e", "", today.plusDays(1), order = 5),
        Task(6, "old done", "", today.minusDays(3), done = true, doneAt = 1_000L, order = 6),
    )

    private fun ids(ts: List<Task>, now: Long = System.currentTimeMillis()) = todayRows(ts, today, newFirst = false, now = now).map { it.id }

    @Test
    fun tickedTaskStaysInPlaceThenLeaves() {
        assertEquals(listOf(3L, 1L, 2L, 4L), ids(tasks()))

        val (ticked, done) = TaskOps.toggle(tasks(), 2L, today)
        assertEquals(true, done)
        // Сразу после галочки — на своём месте, зачёркнутая.
        assertEquals(listOf(3L, 1L, 2L, 4L), ids(ticked))
        // Через пару секунд уходит, остальные не переставляются.
        assertEquals(listOf(3L, 1L, 4L), ids(ticked, now = System.currentTimeMillis() + TICK_GRACE_MS + 10))

        // Передумал в пределах паузы — задача снова висит на том же месте.
        val (back, _) = TaskOps.toggle(ticked, 2L, today)
        assertEquals(listOf(3L, 1L, 2L, 4L), ids(back))
    }
}
