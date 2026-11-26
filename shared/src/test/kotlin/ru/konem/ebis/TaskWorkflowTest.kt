package ru.konem.ebis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.konem.ebis.data.ChecklistItem
import ru.konem.ebis.data.ImportBackup
import ru.konem.ebis.data.OverdueAction
import ru.konem.ebis.data.Repeat
import ru.konem.ebis.data.Tag
import ru.konem.ebis.data.TagColors
import ru.konem.ebis.data.TagOps
import ru.konem.ebis.data.Task
import ru.konem.ebis.data.TaskData
import ru.konem.ebis.data.TaskJson
import ru.konem.ebis.data.TaskOps
import ru.konem.ebis.data.importTasks
import ru.konem.ebis.data.mergeTasks
import ru.konem.ebis.ui.QuickParse
import ru.konem.ebis.ui.TaskSearch
import java.time.LocalDate

class TaskWorkflowTest {
    @get:Rule val directory = TemporaryFolder()
    private val today = LocalDate.of(2026, 11, 26)
    private val home = Tag(10, "Дом", TagColors.presets[0], 100)
    private val project = Tag(11, "Новый проект", TagColors.presets[1], 100)
    private val task = Task(1, "Счёт", "Оплатить электричество", today.minusDays(2), updatedAt = 100)

    @Test
    fun quickInputAddsKnownTagsAlongsideDateAndTime() {
        val tags = listOf(home, project)
        val completion = QuickParse.completeTag("Оплатить завтра в 15:00 #дом #нов", project)
        val result = QuickParse.parse(completion, today, tags)
        assertEquals("Оплатить", result.title)
        assertEquals(today.plusDays(1), result.date)
        assertEquals(15 * 60, result.time)
        assertEquals(setOf(home.id, project.id), result.tagIds)
        assertEquals(listOf(project), QuickParse.tagSuggestions("Оплатить #НОВ", tags))
        assertEquals(listOf(project), QuickParse.tagSuggestions("Оплатить #\"Новый проект", tags))
        val unknown = QuickParse.parse("Оплатить #срочно завтра", today, tags)
        assertEquals("Оплатить #срочно", unknown.title)
        assertFalse(unknown.hot)
        val quoted = project.copy(name = "Дом \"А\"")
        val quotedInput = QuickParse.completeTag("Оплатить #", quoted)
        assertEquals(setOf(quoted.id), QuickParse.parse(quotedInput, today, listOf(quoted)).tagIds)
    }

    @Test
    fun searchFindsCompletedTasksByDescriptionAndTags() {
        val tagged = task.copy(done = true, tagIds = setOf(home.id))
        val other = task.copy(id = 2, title = "Купить", desc = "Оплатить заказ")
        assertEquals(listOf(tagged), TaskSearch.find(listOf(other, tagged), listOf(home), "ОПЛАТИТЬ #дом"))
        assertEquals(listOf(tagged), TaskSearch.find(listOf(other, tagged), listOf(home), "счет"))
    }

    @Test
    fun concurrentDateAndTagEditsConvergeAfterReadingSyncFiles() {
        val data = TaskData(listOf(task), tags = listOf(home))
        val phone = TaskOps.stamp(data, listOf(task.copy(tagIds = setOf(home.id))), now = 120)
        val pc = TaskOps.stamp(data, listOf(task.copy(due = today.plusDays(2))), now = 130)
        val a = TaskJson.parseData(TaskJson.pretty(phone))
        val b = TaskJson.parseData(TaskJson.pretty(pc))
        val merged = mergeTasks(a, b)
        assertEquals(merged, mergeTasks(b, a))
        assertEquals(today.plusDays(2), merged.tasks.single().due)
        assertEquals(setOf(home.id), merged.tasks.single().tagIds)
        assertEquals(merged, mergeTasks(merged, a))
    }

    @Test
    fun editingOpenFormDoesNotOverwriteAnIncomingChange() {
        val incoming = task.copy(tagIds = setOf(home.id))
        val edited = TaskOps.edit(
            listOf(incoming), task.id, "Новый заголовок", task.desc, task.due,
            task.time, task.repeat, task.hot, tagIds = task.tagIds, original = task,
        ).single()
        assertEquals("Новый заголовок", edited.title)
        assertEquals(setOf(home.id), edited.tagIds)
    }

    @Test
    fun undoRevertsOnlyTheLocalChangeAfterMerging() {
        val data = TaskData(listOf(task))
        val local = TaskOps.stamp(data, listOf(task.copy(due = today)), now = 120)
        val incoming = TaskOps.stamp(data, listOf(task.copy(tagIds = setOf(home.id))), now = 130)
        val merged = mergeTasks(local, incoming)
        val undone = TaskOps.undo(merged.tasks, data.tasks, local.tasks).single()
        assertEquals(task.due, undone.due)
        assertEquals(setOf(home.id), undone.tagIds)
    }

    @Test
    fun legacyEditDoesNotEraseChecklist() {
        val data = TaskData(listOf(task))
        val items = listOf(ChecklistItem("Проверить"))
        val phone = TaskOps.stamp(data, listOf(task.copy(checklist = items)), now = 110)
        val legacy = TaskData(listOf(task.copy(title = "Новое название", updatedAt = 120)))
        val merged = mergeTasks(phone, legacy).tasks.single()
        assertEquals("Новое название", merged.title)
        assertEquals(items, merged.checklist)
    }

    @Test
    fun batchTargetsOnlyOverdueAndResetsNextChecklistOccurrence() {
        val recurring = task.copy(repeat = Repeat.Daily, checklist = listOf(ChecklistItem("Проверить", true)))
        val dueToday = task.copy(id = 2, due = today)
        val tasks = listOf(recurring, dueToday)
        val moved = TaskOps.applyToOverdue(tasks, setOf(1, 2), OverdueAction.Tomorrow, today = today)
        assertEquals(today.plusDays(1), moved[0].due)
        assertEquals(dueToday, moved[1])
        val tagged = TaskOps.applyToOverdue(tasks, setOf(1, 2), OverdueAction.AddTags, setOf(home.id), today)
        assertEquals(setOf(home.id), tagged[0].tagIds)
        val finished = TaskOps.applyToOverdue(tagged, setOf(1, 2), OverdueAction.Complete, today = today)
        assertTrue(finished[0].done)
        assertEquals(dueToday, finished[1])
        assertEquals(listOf(ChecklistItem("Проверить")), finished.last().checklist)
        assertEquals(setOf(home.id), finished.last().tagIds)
        assertEquals(finished, TaskJson.parse(TaskJson.pretty(TaskData(finished))))
    }

    @Test
    fun trashSurvivesSyncAndRestoresOriginalIdsAndTagLinks() {
        val tagged = task.copy(tagIds = setOf(home.id))
        val initial = TaskData(listOf(tagged), tags = listOf(home))
        val deleted = TagOps.delete(TaskOps.stamp(initial, emptyList()), home.id)
        val reloaded = TaskJson.parseData(TaskJson.pretty(deleted))
        val synced = mergeTasks(initial, reloaded)
        assertEquals(listOf(tagged), synced.recoverableTasks)
        assertEquals(listOf(home), synced.recoverableTags)
        val restored = TagOps.restoreDeleted(TaskOps.restoreDeleted(synced, task.id), home.id)
        val otherDevice = mergeTasks(reloaded, restored)
        assertEquals(setOf(home.id), otherDevice.tasks.single().tagIds)
        assertEquals(home.id, otherDevice.tags.single().id)
        assertTrue(otherDevice.recoverableTasks.isEmpty())
        assertTrue(otherDevice.recoverableTags.isEmpty())
    }

    @Test
    fun backupBeforeImportCanRecoverThePreviousTasksAndTags() {
        val backup = ImportBackup(directory.root.resolve("before-import.json"))
        val before = TaskData(listOf(task), tags = listOf(home))
        backup.save(before)
        val imported = importTasks(before, TaskData(listOf(task.copy(id = 2, title = "Из файла")), tags = listOf(project)))
        val restored = importTasks(imported, backup.load(), restore = true)
        assertEquals(listOf(task.id), restored.tasks.map { it.id })
        assertEquals(listOf(home.id), restored.tags.map { it.id })
        assertEquals(listOf("Из файла"), restored.recoverableTasks.map { it.title })
    }
}
