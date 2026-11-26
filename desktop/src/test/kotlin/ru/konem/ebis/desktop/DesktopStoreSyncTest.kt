package ru.konem.ebis.desktop

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import ru.konem.ebis.data.Tag
import ru.konem.ebis.data.TagOps
import ru.konem.ebis.data.Task
import ru.konem.ebis.data.TaskData
import ru.konem.ebis.data.TaskJson
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.util.prefs.Preferences

/** Синхронизация через общую папку: запись не затирает чужие правки, конфликтные копии Syncthing вливаются. */
class DesktopStoreSyncTest {
    private val node = "ru/konem/ebis-desktop-sync-test"
    private val dir = Files.createTempDirectory("ebis-sync").toFile()
    private val store = DesktopStore(node).apply { folder = dir }
    private val day = LocalDate.of(2026, 10, 19)
    private fun t(id: Long, title: String, at: Long) = Task(id, title, "", day, updatedAt = at)
    private val file get() = File(dir, DesktopStore.FILE_NAME)

    @After
    fun cleanup() {
        Preferences.userRoot().node(node).removeNode()
        dir.deleteRecursively()
    }

    @Test
    fun saveKeepsChangesThatArrivedFromThePhone() {
        store.save(TaskData(listOf(t(1, "ПК", 10))))
        // Телефон успел записать свою версию с новой задачей.
        Thread.sleep(20)
        file.writeText(TaskJson.pretty(TaskData(listOf(t(1, "ПК", 10), t(2, "С телефона", 20)))))
        file.setLastModified(System.currentTimeMillis() + 2000)

        val written = store.save(TaskData(listOf(t(1, "ПК правка", 30))))

        assertEquals(listOf("ПК правка", "С телефона"), written.tasks.map { it.title })
        assertEquals(written.tasks, TaskJson.parseData(file.readText()).tasks)
    }

    @Test
    fun conflictCopiesAreMergedAndRemoved() {
        file.writeText(TaskJson.pretty(TaskData(listOf(t(1, "Основной", 10)))))
        val state = AppState(store)
        val copy = File(dir, "ebis-tasks.sync-conflict-20261019-145835-ZSCIDOQ.json")
        copy.writeText(TaskJson.pretty(TaskData(listOf(t(1, "Старый", 5), t(3, "Только в копии", 15)))))

        // Так делает окно раз в полторы секунды.
        store.readIfChanged()?.let { state.pull(it) }

        assertFalse(copy.exists())
        assertEquals(listOf("Основной", "Только в копии"), state.tasks.map { it.title })
        assertEquals(state.tasks, TaskJson.parseData(file.readText()).tasks)
    }

    @Test
    fun syncingTagDeletionRemovesItFromTheActiveFilter() {
        val first = Tag(10, "Работа", 0, 100)
        val second = Tag(11, "Дом", 0, 100)
        val local = TaskData(listOf(t(1, "Задача", 10)), tags = listOf(first, second))
        store.save(local)
        val state = AppState(store)
        state.toggleTagFilter(first.id)
        state.toggleTagFilter(second.id)

        val afterFirstDeletion = TagOps.delete(local, first.id)
        state.pull(afterFirstDeletion)
        assertEquals(setOf(second.id), state.tagFilter)

        state.pull(TagOps.delete(afterFirstDeletion, second.id))
        assertEquals(emptySet<Long>(), state.tagFilter)
    }
}
