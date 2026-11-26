package ru.konem.ebis.data

/**
 * Слияние двух версий списка (телефон и файл с ПК) по задачам, а не целиком:
 * независимые правки задачи сливаются по отметкам отдельных полей,
 * удаление побеждает, если оно случилось не раньше последней правки задачи.
 * Теги сливаются по тем же правилам.
 */
fun mergeTasks(local: TaskData, remote: TaskData): TaskData {
    val (tasks, deleted) = mergeById(local.tasks, remote.tasks, local.deleted, remote.deleted, { it.id }, { it.updatedAt }, ::mergeTask)
    val (tags, deletedTags) = mergeById(local.tags, remote.tags, local.deletedTags, remote.deletedTags, { it.id }, { it.updatedAt })
    return TaskData(
        tasks, deleted, tags = tags, deletedTags = deletedTags,
        trashedTasks = mergeTrash(local.trashedTasks, remote.trashedTasks, deleted, local.deleted, remote.deleted),
        trashedTags = mergeTrash(local.trashedTags, remote.trashedTags, deletedTags, local.deletedTags, remote.deletedTags),
    )
}

private fun mergeTask(local: Task, remote: Task): Task {
    if (local.fieldTimes.isEmpty() && remote.fieldTimes.isEmpty()) {
        return if (remote.updatedAt > local.updatedAt) remote else local
    }
    var merged = local
    val times = TaskField.entries.associateWith { field ->
        // Старый клиент не записывает чек-листы. Его обычная правка задачи не должна их затереть.
        val left = if (field == TaskField.Checklist && field !in local.fieldTimes && local.checklist.isEmpty()) -1 else field.timestamp(local)
        val right = if (field == TaskField.Checklist && field !in remote.fieldTimes && remote.checklist.isEmpty()) -1 else field.timestamp(remote)
        if (right > left || (right == left && field.value(remote).toString() > field.value(local).toString())) {
            merged = field.copyTo(merged, remote)
        }
        maxOf(0, left, right)
    }
    return merged.copy(updatedAt = maxOf(local.updatedAt, remote.updatedAt), fieldTimes = times)
}

private fun <T> mergeTrash(
    local: Map<Long, T>, remote: Map<Long, T>, deleted: Map<Long, Long>,
    localDeleted: Map<Long, Long>, remoteDeleted: Map<Long, Long>,
): Map<Long, T> = buildMap {
    deleted.forEach { (id, at) ->
        val item = local[id]?.takeIf { localDeleted[id] == at }
            ?: remote[id]?.takeIf { remoteDeleted[id] == at }
            ?: local[id] ?: remote[id]
        if (item != null) put(id, item)
    }
}

private fun <T> mergeById(
    localItems: List<T>, remoteItems: List<T>,
    localDeleted: Map<Long, Long>, remoteDeleted: Map<Long, Long>,
    id: (T) -> Long, updatedAt: (T) -> Long,
    merge: (T, T) -> T = { local, remote -> if (updatedAt(remote) > updatedAt(local)) remote else local },
): Pair<List<T>, Map<Long, Long>> {
    val deleted = (localDeleted.keys + remoteDeleted.keys).associateWith { k ->
        maxOf(localDeleted[k] ?: 0L, remoteDeleted[k] ?: 0L)
    }
    val localById = localItems.associateBy(id)
    val remoteById = remoteItems.associateBy(id)
    // Порядок: сначала как на телефоне, новые из файла — в конец.
    val ids = (localItems.map(id) + remoteItems.map(id)).distinct()
    val items = ids.mapNotNull { k ->
        val l = localById[k]
        val r = remoteById[k]
        val winner = when {
            l == null -> r
            r == null -> l
            else -> merge(l, r)
        } ?: return@mapNotNull null
        val gone = deleted[k]
        if (gone != null && gone >= updatedAt(winner)) null else winner
    }
    val alive = items.map(id).toSet()
    return items to deleted.filterKeys { it !in alive }
}

/**
 * Копия, которую Syncthing оставляет, когда телефон и ПК поменяли файл одновременно:
 * «ebis-tasks.sync-conflict-20261019-145835-ZSCIDOQ.json». Её задачи вливаются слиянием, а сама копия удаляется.
 */
fun isSyncConflict(name: String): Boolean = name.startsWith("ebis-tasks.sync-conflict-") && name.endsWith(".json")
