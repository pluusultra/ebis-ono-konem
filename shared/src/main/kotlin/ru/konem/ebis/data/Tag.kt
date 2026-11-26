package ru.konem.ebis.data

/**
 * Тег: название и цвет фона плашки. Теги заводятся заранее (или прямо из формы задачи),
 * у задачи — набор id тегов ([Task.tagIds]). Лежат в том же файле, что и задачи, и так же синхронизируются.
 */
data class Tag(
    val id: Long,
    val name: String,
    /** Цвет фона в ARGB. Текст поверх — чёрный или белый, см. [TagColors.isLight]. */
    val color: Int,
    /** Когда тег последний раз меняли (мс): по нему слияние решает, чья версия новее. */
    val updatedAt: Long = 0L,
)

object TagColors {
    /** Готовые цвета фона. Первый — для нового тега по умолчанию. */
    val presets: List<Int> = listOf(
        0xFFFFD447, 0xFFFF9F43, 0xFFE5383B, 0xFFFF7EB6, 0xFFB06CFF, 0xFF4DA3FF,
        0xFF34C6C6, 0xFF2FA866, 0xFFA3D65C, 0xFF9C6B4E, 0xFF8A8F98, 0xFF1F1F1F,
    ).map { it.toInt() }

    /** Светлый фон — пишем чёрным, тёмный — белым. */
    fun isLight(color: Int): Boolean {
        fun ch(shift: Int): Double {
            val c = ((color shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        val l = 0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)
        // Порог, при котором чёрный текст контрастнее белого.
        return l > 0.179
    }

    fun toHex(color: Int): String = "#%06X".format(color and 0xFFFFFF)

    /** «#RRGGBB» → ARGB с полной непрозрачностью; ерунда → null. */
    fun fromHex(s: String): Int? {
        val h = s.trim().removePrefix("#")
        if (h.length != 6) return null
        return h.toIntOrNull(16)?.let { it or 0xFF000000.toInt() }
    }
}

/** Действия над тегами. Как и [TaskOps] — чистые функции над [TaskData]; время правки ставится сразу. */
object TagOps {
    const val MAX_NAME = 24

    private fun nextId(data: TaskData) =
        maxOf((data.tags.maxOfOrNull { it.id } ?: 0L) + 1, (data.deletedTags.keys.maxOrNull() ?: 0L) + 1, System.currentTimeMillis())

    /** Что не так с названием, или null, если всё хорошо. [exceptId] — тег, который сейчас правим. */
    fun problem(tags: List<Tag>, name: String, exceptId: Long? = null): String? {
        val n = name.trim()
        return when {
            n.isEmpty() -> "Тег без названия — это просто цветная клякса"
            n.length > MAX_NAME -> "Покороче, до $MAX_NAME букв"
            tags.any { it.id != exceptId && it.name.equals(n, ignoreCase = true) } -> "Такой тег уже есть"
            else -> null
        }
    }

    fun add(data: TaskData, name: String, color: Int, now: Long = System.currentTimeMillis()): Pair<TaskData, Long> {
        val id = nextId(data)
        return data.copy(tags = data.tags + Tag(id, name.trim(), color, now)) to id
    }

    fun edit(data: TaskData, id: Long, name: String, color: Int, now: Long = System.currentTimeMillis()): TaskData =
        data.copy(tags = data.tags.map {
            if (it.id == id) it.copy(name = name.trim(), color = color, updatedAt = maxOf(now, it.updatedAt + 1)) else it
        })

    /**
     * Тег пропадает из списка; надгробие не даёт ему воскреснуть при слиянии. Задачи не трогаем:
     * id тегов не переиспользуются, а незнакомые id нигде не показываются.
     */
    fun delete(data: TaskData, id: Long, now: Long = System.currentTimeMillis()): TaskData {
        val tag = data.tags.firstOrNull { it.id == id } ?: return data
        return data.copy(
            tags = data.tags.filterNot { it.id == id },
            deletedTags = data.deletedTags + (id to maxOf(now, tag.updatedAt + 1)),
            trashedTags = data.trashedTags + (id to tag),
        )
    }

    fun restoreDeleted(data: TaskData, id: Long, now: Long = System.currentTimeMillis()): TaskData {
        val tag = data.trashedTags[id] ?: return data
        val deletedAt = data.deletedTags[id] ?: return data
        if (!TaskJson.canRestore(deletedAt, now) || problem(data.tags, tag.name) != null) return data
        return data.copy(
            tags = data.tags + tag.copy(updatedAt = maxOf(now, deletedAt + 1)),
            deletedTags = data.deletedTags - id,
            trashedTags = data.trashedTags - id,
        )
    }

    /** Пустой фильтр — показываем всё; иначе задачи хотя бы с одним из выбранных тегов. */
    fun matches(task: Task, filter: Set<Long>): Boolean = filter.isEmpty() || task.tagIds.any { it in filter }

    /** Теги задачи по порядку списка тегов; удалённые и незнакомые пропускаются. */
    fun of(task: Task, tags: List<Tag>): List<Tag> =
        if (task.tagIds.isEmpty()) emptyList() else tags.filter { it.id in task.tagIds }
}
