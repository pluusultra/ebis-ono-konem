package ru.konem.ebis.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Всё, что лежит в файле задач.
 * [deleted] — «надгробия»: id удалённой задачи → когда удалили. Без них при слиянии с файлом
 * с другого устройства удалённая задача воскресала бы. [seeded] — в списке только обучалки первого запуска.
 */
data class TaskData(
    val tasks: List<Task>,
    val deleted: Map<Long, Long> = emptyMap(),
    val seeded: Boolean = false,
)

/** Формат файла задач — один на телефон и ПК. org.json на Android встроен, на ПК подключается библиотекой. */
object TaskJson {
    /** Надгробия старше этого срока выкидываем: все устройства к тому времени точно синхронизировались. */
    private const val TOMBSTONE_TTL_MS = 60L * 24 * 60 * 60 * 1000

    private fun tasksJson(tasks: List<Task>): JSONArray {
        val arr = JSONArray()
        tasks.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("title", t.title)
                    .put("desc", t.desc)
                    .put("due", t.due.toEpochDay())
                    .put("done", t.done)
                    .put("doneAt", t.doneAt)
                    .put("time", t.time ?: -1)
                    .put("repeat", t.repeat.name)
                    .put("hot", t.hot)
                    .put("order", t.order)
                    .put("nextId", t.nextId)
                    .put("updatedAt", t.updatedAt)
            )
        }
        return arr
    }

    fun toJson(data: TaskData): JSONObject {
        val now = System.currentTimeMillis()
        val deleted = JSONArray()
        data.deleted.filterValues { now - it < TOMBSTONE_TTL_MS }.forEach { (id, at) ->
            deleted.put(JSONObject().put("id", id).put("at", at))
        }
        return JSONObject()
            .put("app", "ebis-ono-konem")
            .put("version", 3)
            .put("tasks", tasksJson(data.tasks))
            .put("deleted", deleted)
            .apply { if (data.seeded) put("seeded", true) }
    }

    /** Понимает голый массив (старый формат), бэкап {"tasks": [...]} и новый с "deleted". Бросает исключение на мусоре. */
    fun parseData(text: String): TaskData {
        val trimmed = text.trim()
        val obj = if (trimmed.startsWith("{")) JSONObject(trimmed) else null
        val arr = obj?.getJSONArray("tasks") ?: JSONArray(trimmed)
        val tasks = List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val id = o.getLong("id")
            Task(
                id = id,
                title = o.getString("title"),
                desc = o.optString("desc", ""),
                due = LocalDate.ofEpochDay(o.getLong("due")),
                done = o.optBoolean("done", false),
                doneAt = o.optLong("doneAt", 0L),
                time = o.optInt("time", -1).takeIf { it >= 0 },
                repeat = runCatching { Repeat.valueOf(o.optString("repeat", "None")) }.getOrDefault(Repeat.None),
                hot = o.optBoolean("hot", false),
                order = o.optLong("order", id),
                nextId = o.optLong("nextId", 0L),
                updatedAt = o.optLong("updatedAt", 0L),
            )
        }
        val deleted = buildMap {
            val d = obj?.optJSONArray("deleted") ?: return@buildMap
            for (i in 0 until d.length()) {
                val o = d.getJSONObject(i)
                put(o.getLong("id"), o.getLong("at"))
            }
        }
        return TaskData(tasks, deleted, seeded = obj?.optBoolean("seeded", false) ?: false)
    }

    fun parse(text: String): List<Task> = parseData(text).tasks

    /** Читаемый человеком JSON с отступами — для файла синхронизации и бэкапа. */
    fun pretty(data: TaskData): String = toJson(data).toString(2)
}
