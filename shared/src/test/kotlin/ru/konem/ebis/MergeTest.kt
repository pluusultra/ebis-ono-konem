package ru.konem.ebis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.konem.ebis.data.Task
import ru.konem.ebis.data.TaskData
import ru.konem.ebis.data.isSyncConflict
import ru.konem.ebis.data.mergeTasks
import java.time.LocalDate

class MergeTest {
    private val day = LocalDate.of(2026, 10, 17)
    private fun t(id: Long, title: String, at: Long) = Task(id, title, "", day, updatedAt = at)

    @Test
    fun newerVersionWinsPerTask() {
        val phone = TaskData(listOf(t(1, "phone edit", 200), t(2, "old", 100)))
        val pc = TaskData(listOf(t(1, "pc edit", 150), t(2, "pc edit", 300)))
        val m = mergeTasks(phone, pc)
        assertEquals(listOf("phone edit", "pc edit"), m.tasks.map { it.title })
    }

    @Test
    fun newTasksFromBothSidesAreKept() {
        val m = mergeTasks(TaskData(listOf(t(1, "a", 10))), TaskData(listOf(t(2, "b", 20))))
        assertEquals(listOf(1L, 2L), m.tasks.map { it.id })
    }

    @Test
    fun deletionBeatsOlderEditButNotNewer() {
        val phone = TaskData(emptyList(), deleted = mapOf(1L to 500L, 2L to 500L))
        val pc = TaskData(listOf(t(1, "edited before delete", 400), t(2, "edited after delete", 600)))
        val m = mergeTasks(phone, pc)
        assertEquals(listOf(2L), m.tasks.map { it.id })
        assertEquals(setOf(1L), m.deleted.keys)
    }

    @Test
    fun recognisesSyncthingConflictCopies() {
        assertTrue(isSyncConflict("ebis-tasks.sync-conflict-20261019-145835-ZSCIDOQ.json"))
        assertFalse(isSyncConflict("ebis-tasks.json"))
        assertFalse(isSyncConflict("ebis-backup-2026-10-17.json"))
        assertFalse(isSyncConflict("notes.sync-conflict-20261019-145835-ZSCIDOQ.json"))
    }
}
