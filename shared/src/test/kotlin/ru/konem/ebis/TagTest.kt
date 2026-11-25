package ru.konem.ebis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.konem.ebis.data.Tag
import ru.konem.ebis.data.TagColors
import ru.konem.ebis.data.TagOps
import ru.konem.ebis.data.Task
import ru.konem.ebis.data.TaskData
import ru.konem.ebis.data.TaskJson
import ru.konem.ebis.data.TaskOps
import ru.konem.ebis.data.mergeTasks
import java.time.LocalDate

class TagTest {
    private val day = LocalDate.of(2026, 11, 25)
    private val work = Tag(10, "Работа", 0xFF4DA3FF.toInt(), 100)
    private val home = Tag(11, "Дом", 0xFFFFD447.toInt(), 100)

    @Test
    fun tagsAndTaskTagsSurviveJson() {
        val data = TaskData(
            listOf(Task(1, "a", "", day, tagIds = setOf(10, 11)), Task(2, "b", "", day)),
            tags = listOf(work, home),
            deletedTags = mapOf(12L to System.currentTimeMillis()),
        )
        val back = TaskJson.parseData(TaskJson.pretty(data))
        assertEquals(data.tasks, back.tasks)
        assertEquals(data.tags, back.tags)
        assertEquals(data.deletedTags, back.deletedTags)
    }

    @Test
    fun oldFileWithoutTagsParses() {
        val back = TaskJson.parseData("""{"tasks":[{"id":1,"title":"x","due":0}]}""")
        assertTrue(back.tags.isEmpty())
        assertTrue(back.tasks.single().tagIds.isEmpty())
    }

    @Test
    fun mergeTakesNewerTagAndRespectsDeletion() {
        val phone = TaskData(emptyList(), tags = listOf(work.copy(name = "Работка", updatedAt = 200), home))
        val pc = TaskData(emptyList(), tags = listOf(work), deletedTags = mapOf(11L to 150L))
        val m = mergeTasks(phone, pc)
        assertEquals(listOf("Работка"), m.tags.map { it.name })
        assertEquals(setOf(11L), m.deletedTags.keys)
    }

    @Test
    fun stampKeepsTags() {
        val data = TaskData(listOf(Task(1, "a", "", day)), tags = listOf(work))
        val next = TaskOps.stamp(data, data.tasks + Task(2, "b", "", day))
        assertEquals(listOf(work), next.tags)
    }

    @Test
    fun editKeepsTagsUnlessGiven() {
        val tasks = listOf(Task(1, "a", "", day, tagIds = setOf(10)))
        assertEquals(setOf(10L), TaskOps.edit(tasks, 1, "b", "", day, null, ru.konem.ebis.data.Repeat.None, false).single().tagIds)
        assertEquals(emptySet<Long>(), TaskOps.edit(tasks, 1, "b", "", day, null, ru.konem.ebis.data.Repeat.None, false, tagIds = emptySet()).single().tagIds)
    }

    @Test
    fun tagOps() {
        val (added, id) = TagOps.add(TaskData(emptyList()), "  Спорт ", TagColors.presets[2])
        assertEquals("Спорт", added.tags.single().name)
        assertNotNull(TagOps.problem(added.tags, "спорт"))
        assertNull(TagOps.problem(added.tags, "спорт", exceptId = id))
        assertNotNull(TagOps.problem(added.tags, "   "))
        val edited = TagOps.edit(added, id, "Зал", TagColors.presets[3])
        assertEquals("Зал", edited.tags.single().name)
        val gone = TagOps.delete(edited, id)
        assertTrue(gone.tags.isEmpty())
        assertTrue(id in gone.deletedTags)
    }

    @Test
    fun filterMatchesAnySelectedTag() {
        val t = Task(1, "a", "", day, tagIds = setOf(10))
        assertTrue(TagOps.matches(t, emptySet()))
        assertTrue(TagOps.matches(t, setOf(10, 11)))
        assertFalse(TagOps.matches(t, setOf(11)))
        assertEquals(listOf(work), TagOps.of(t.copy(tagIds = setOf(10, 99)), listOf(work, home)))
    }

    @Test
    fun textColorFollowsBackground() {
        assertTrue(TagColors.isLight(0xFFFFD447.toInt()))
        assertFalse(TagColors.isLight(0xFF1F1F1F.toInt()))
        assertEquals(0xFF4DA3FF.toInt(), TagColors.fromHex(TagColors.toHex(0xFF4DA3FF.toInt())))
    }
}
