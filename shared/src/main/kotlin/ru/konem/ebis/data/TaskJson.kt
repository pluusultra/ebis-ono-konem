package ru.konem.ebis.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
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
    val tags: List<Tag> = emptyList(),
    /** Надгробия удалённых тегов: id → когда удалили. */
    val deletedTags: Map<Long, Long> = emptyMap(),
    val trashedTasks: Map<Long, Task> = emptyMap(),
    val trashedTags: Map<Long, Tag> = emptyMap(),
) {
    val recoverableTasks get() = trashedTasks.values.filter { TaskJson.canRestore(deleted[it.id]) }
    val recoverableTags get() = trashedTags.values.filter { TaskJson.canRestore(deletedTags[it.id]) }
}

/** Формат файла задач — один на телефон и ПК. org.json на Android встроен, на ПК подключается библиотекой. */
object TaskJson {
    /** Надгробия старше этого срока выкидываем: все устройства к тому времени точно синхронизировались. */
    private const val TOMBSTONE_TTL_MS = 60L * 24 * 60 * 60 * 1000
    fun canRestore(at: Long?, now: Long = System.currentTimeMillis()) = at != null && now - at < TOMBSTONE_TTL_MS

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
                    .apply {
                        if (t.repeat == Repeat.SelectedDays) {
                            put("repeatDays", JSONArray(t.repeatDays.sorted().map { it.value }))
                        }
                        if (t.tagIds.isNotEmpty()) put("tags", JSONArray(t.tagIds.toList()))
                        if (t.checklist.isNotEmpty()) {
                            put("checklist", JSONArray(t.checklist.map { JSONObject().put("text", it.text).put("done", it.done) }))
                        }
                        if (t.fieldTimes.isNotEmpty()) {
                            put("fieldTimes", JSONObject(t.fieldTimes.mapKeys { it.key.name }))
                        }
                    }
            )
        }
        return arr
    }

    private fun tombstonesJson(deleted: Map<Long, Long>, now: Long): JSONArray {
        val arr = JSONArray()
        deleted.filterValues { canRestore(it, now) }.forEach { (id, at) ->
            arr.put(JSONObject().put("id", id).put("at", at))
        }
        return arr
    }

    private fun tombstones(arr: JSONArray?): Map<Long, Long> = buildMap {
        arr ?: return@buildMap
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            put(o.getLong("id"), o.getLong("at"))
        }
    }

    private fun tagsJson(tags: List<Tag>): JSONArray {
        val arr = JSONArray()
        tags.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("name", t.name)
                    .put("color", TagColors.toHex(t.color))
                    .put("updatedAt", t.updatedAt)
            )
        }
        return arr
    }

    private fun parseTags(arr: JSONArray?): List<Tag> {
        arr ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name", "").trim()
            if (!o.has("id") || name.isEmpty()) return@mapNotNull null
            Tag(
                id = o.getLong("id"),
                name = name,
                color = TagColors.fromHex(o.optString("color", "")) ?: TagColors.presets.first(),
                updatedAt = o.optLong("updatedAt", 0L),
            )
        }
    }

    fun toJson(data: TaskData): JSONObject {
        val now = System.currentTimeMillis()
        return JSONObject()
            .put("app", "ebis-ono-konem")
            .put("version", 4)
            .put("tasks", tasksJson(data.tasks))
            .put("deleted", tombstonesJson(data.deleted, now))
            .apply {
                if (data.tags.isNotEmpty()) put("tags", tagsJson(data.tags))
                if (data.deletedTags.isNotEmpty()) put("deletedTags", tombstonesJson(data.deletedTags, now))
                if (data.trashedTasks.isNotEmpty()) put("trashedTasks", tasksJson(data.recoverableTasks))
                if (data.trashedTags.isNotEmpty()) put("trashedTags", tagsJson(data.recoverableTags))
                if (data.seeded) put("seeded", true)
            }
    }

    private fun parseTasks(arr: JSONArray): List<Task> =
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val id = o.getLong("id")
            val repeat = runCatching { Repeat.valueOf(o.optString("repeat", "None")) }.getOrDefault(Repeat.None)
            val repeatDays = buildSet {
                if (repeat == Repeat.SelectedDays) {
                    val days = o.optJSONArray("repeatDays") ?: return@buildSet
                    for (index in 0 until days.length()) {
                        val day = days.optInt(index)
                        if (day in 1..7) add(DayOfWeek.of(day))
                    }
                }
            }
            Task(
                id = id,
                title = o.getString("title"),
                desc = o.optString("desc", ""),
                due = LocalDate.ofEpochDay(o.getLong("due")),
                done = o.optBoolean("done", false),
                doneAt = o.optLong("doneAt", 0L),
                time = o.optInt("time", -1).takeIf { it >= 0 },
                repeat = if (repeat == Repeat.SelectedDays && repeatDays.isEmpty()) Repeat.None else repeat,
                hot = o.optBoolean("hot", false),
                order = o.optLong("order", id),
                nextId = o.optLong("nextId", 0L),
                updatedAt = o.optLong("updatedAt", 0L),
                repeatDays = repeatDays,
                tagIds = buildSet {
                    val ids = o.optJSONArray("tags") ?: return@buildSet
                    for (index in 0 until ids.length()) ids.optLong(index, 0L).takeIf { it != 0L }?.let(::add)
                },
                checklist = buildList {
                    val items = o.optJSONArray("checklist") ?: return@buildList
                    for (index in 0 until items.length()) {
                        val item = items.optJSONObject(index) ?: continue
                        val label = item.optString("text", "").trim()
                        if (label.isNotEmpty()) add(ChecklistItem(label, item.optBoolean("done", false)))
                    }
                },
                fieldTimes = buildMap {
                    val times = o.optJSONObject("fieldTimes") ?: return@buildMap
                    TaskField.entries.forEach { field ->
                        times.optLong(field.name, -1).takeIf { it >= 0 }?.let { put(field, it) }
                    }
                },
            )
        }

    /** Читает старые массивы задач и новые файлы со сведениями о слиянии и корзиной. */
    fun parseData(text: String): TaskData {
        val trimmed = text.trim()
        val obj = if (trimmed.startsWith("{")) JSONObject(trimmed) else null
        val arr = obj?.getJSONArray("tasks") ?: JSONArray(trimmed)
        val deleted = tombstones(obj?.optJSONArray("deleted"))
        val deletedTags = tombstones(obj?.optJSONArray("deletedTags"))
        return TaskData(
            parseTasks(arr),
            deleted,
            seeded = obj?.optBoolean("seeded", false) ?: false,
            tags = parseTags(obj?.optJSONArray("tags")),
            deletedTags = deletedTags,
            trashedTasks = obj?.optJSONArray("trashedTasks")?.let(::parseTasks).orEmpty()
                .filter { canRestore(deleted[it.id]) }.associateBy { it.id },
            trashedTags = parseTags(obj?.optJSONArray("trashedTags"))
                .filter { canRestore(deletedTags[it.id]) }.associateBy { it.id },
        )
    }

    fun parse(text: String): List<Task> = parseData(text).tasks

    /** Читаемый человеком JSON с отступами — для файла синхронизации и бэкапа. */
    fun pretty(data: TaskData): String = toJson(data).toString(2)
}
