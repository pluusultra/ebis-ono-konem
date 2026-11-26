package ru.konem.ebis.data

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** Снимок перед импортом хранится рядом с локальными данными приложения. */
class ImportBackup(private val file: File) {
    val available get() = file.isFile

    fun save(data: TaskData) {
        file.parentFile?.mkdirs()
        val temp = Files.createTempFile(file.parentFile.toPath(), "before-import-", ".tmp")
        try {
            Files.write(temp, TaskJson.pretty(data).toByteArray(Charsets.UTF_8))
            try {
                Files.move(temp, file.toPath(), REPLACE_EXISTING, ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, file.toPath(), REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    fun load(): TaskData = TaskJson.parseData(file.readText())
}

fun importTasks(current: TaskData, incoming: TaskData, restore: Boolean = false): TaskData {
    val now = System.currentTimeMillis()
    var next = if (restore) {
        val keep = incoming.tags.map { it.id }.toSet()
        current.tags.filterNot { it.id in keep }.fold(current) { data, tag -> TagOps.delete(data, tag.id, now) }
    } else current
    val existing = next.tags.associateBy { it.id }
    val changed = incoming.tags.filter { tag ->
        val local = existing[tag.id]
        local == null || (restore && (local.name != tag.name || local.color != tag.color))
    }.associateBy { it.id }
    val updatedTags = changed.values.map {
        it.copy(updatedAt = maxOf(now, (next.deletedTags[it.id] ?: 0) + 1, (existing[it.id]?.updatedAt ?: 0) + 1))
    }
    next = next.copy(
        tags = next.tags.filterNot { it.id in changed } + updatedTags,
        deletedTags = next.deletedTags - changed.keys,
        trashedTags = next.trashedTags - changed.keys,
    )
    val imported = TaskOps.stamp(next, incoming.tasks.distinctBy { it.id }, now)
    val activeTasks = imported.tasks.map { it.id }.toSet()
    val activeTags = imported.tags.map { it.id }.toSet()
    val incomingTrash = incoming.copy(
        tasks = emptyList(), tags = emptyList(),
        deleted = incoming.deleted - activeTasks, deletedTags = incoming.deletedTags - activeTags,
    )
    return mergeTasks(imported, incomingTrash)
}
