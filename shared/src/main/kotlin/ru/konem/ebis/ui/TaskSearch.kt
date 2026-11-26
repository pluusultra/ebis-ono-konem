package ru.konem.ebis.ui

import ru.konem.ebis.data.Tag
import ru.konem.ebis.data.Task
import java.util.Locale

object TaskSearch {
    fun find(tasks: List<Task>, tags: List<Tag>, query: String): List<Task> {
        val words = query.trim().split(Regex("\\s+")).filter(String::isNotEmpty).map(::normalize)
        if (words.isEmpty()) return emptyList()
        val tagsById = tags.associate { it.id to normalize(it.name) }
        return tasks.filter { task ->
            val content = normalize(task.title + "\n" + task.desc)
            val names = task.tagIds.mapNotNull(tagsById::get)
            words.all { word ->
                if (word.startsWith('#')) word.length > 1 && names.any { word.drop(1) in it }
                else word in content || names.any { word in it }
            }
        }.sortedWith(compareBy({ it.done }, { it.due }, { it.order }))
    }

    private fun normalize(text: String) = text.lowercase(Locale.ROOT).replace('ё', 'е')
}

const val UPDATES_URL = "https://github.com/pluusultra/ebis-ono-konem/releases/latest"
