package ru.konem.ebis.data

import android.content.Context
import java.io.File

/** Хранит задачи в files/tasks.json. Запись через временный файл, чтобы не побить данные. Формат — [TaskJson]. */
class TaskStore(context: Context) {
    private val file = File(context.filesDir, "tasks.json")
    private val tmp = File(context.filesDir, "tasks.json.tmp")
    val importBackup = ImportBackup(File(context.filesDir, "before-import.json"))
    val sync = SyncFile(context)

    /** null, если файла ещё нет (первый запуск). */
    fun load(): TaskData? {
        if (!file.exists()) return null
        return try {
            TaskJson.parseData(file.readText())
        } catch (e: Exception) {
            TaskData(emptyList())
        }
    }

    /** Сохранить. Файл синхронизации сначала вливает чужие правки — возвращается итог, он же пишется к себе. */
    @Synchronized
    fun save(data: TaskData): TaskData {
        val out = sync.write(data)
        tmp.writeText(TaskJson.toJson(out).toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
        return out
    }

    companion object {
        fun parseData(text: String) = TaskJson.parseData(text)

        fun parse(text: String) = TaskJson.parse(text)

        fun backup(data: TaskData): String = TaskJson.pretty(data)

        fun backup(tasks: List<Task>): String = backup(TaskData(tasks))
    }
}
