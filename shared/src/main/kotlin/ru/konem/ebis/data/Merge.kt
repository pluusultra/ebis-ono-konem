package ru.konem.ebis.data

/**
 * Слияние двух версий списка (телефон и файл с ПК) по задачам, а не целиком:
 * у каждой задачи побеждает версия с более поздним [Task.updatedAt],
 * удаление побеждает, если оно случилось не раньше последней правки задачи.
 * Теги сливаются по тем же правилам.
 */
fun mergeTasks(local: TaskData, remote: TaskData): TaskData {
    val (tasks, deleted) = mergeById(local.tasks, remote.tasks, local.deleted, remote.deleted, { it.id }, { it.updatedAt })
    val (tags, deletedTags) = mergeById(local.tags, remote.tags, local.deletedTags, remote.deletedTags, { it.id }, { it.updatedAt })
    return TaskData(tasks, deleted, tags = tags, deletedTags = deletedTags)
}

private fun <T> mergeById(
    localItems: List<T>, remoteItems: List<T>,
    localDeleted: Map<Long, Long>, remoteDeleted: Map<Long, Long>,
    id: (T) -> Long, updatedAt: (T) -> Long,
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
            updatedAt(r) > updatedAt(l) -> r
            else -> l
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
