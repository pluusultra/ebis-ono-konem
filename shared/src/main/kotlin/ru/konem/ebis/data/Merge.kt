package ru.konem.ebis.data

/**
 * Слияние двух версий списка (телефон и файл с ПК) по задачам, а не целиком:
 * у каждой задачи побеждает версия с более поздним [Task.updatedAt],
 * удаление побеждает, если оно случилось не раньше последней правки задачи.
 */
fun mergeTasks(local: TaskData, remote: TaskData): TaskData {
    val deleted = (local.deleted.keys + remote.deleted.keys).associateWith { id ->
        maxOf(local.deleted[id] ?: 0L, remote.deleted[id] ?: 0L)
    }
    val localById = local.tasks.associateBy { it.id }
    val remoteById = remote.tasks.associateBy { it.id }
    // Порядок: сначала как на телефоне, новые из файла — в конец.
    val ids = (local.tasks.map { it.id } + remote.tasks.map { it.id }).distinct()
    val tasks = ids.mapNotNull { id ->
        val l = localById[id]
        val r = remoteById[id]
        val winner = when {
            l == null -> r
            r == null -> l
            r.updatedAt > l.updatedAt -> r
            else -> l
        } ?: return@mapNotNull null
        val gone = deleted[id]
        if (gone != null && gone >= winner.updatedAt) null else winner
    }
    val alive = tasks.map { it.id }.toSet()
    return TaskData(tasks, deleted.filterKeys { it !in alive })
}

/**
 * Копия, которую Syncthing оставляет, когда телефон и ПК поменяли файл одновременно:
 * «ebis-tasks.sync-conflict-20261019-145835-ZSCIDOQ.json». Её задачи вливаются слиянием, а сама копия удаляется.
 */
fun isSyncConflict(name: String): Boolean = name.startsWith("ebis-tasks.sync-conflict-") && name.endsWith(".json")
