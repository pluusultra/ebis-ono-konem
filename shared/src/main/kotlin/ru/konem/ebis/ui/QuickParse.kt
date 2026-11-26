package ru.konem.ebis.ui

import ru.konem.ebis.data.Repeat
import ru.konem.ebis.data.Tag
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Разбор даты, времени и повтора прямо из названия:
 * «скинуть Стасу завтра в 15:00», «отчёт в пт», «через 3 дня», «25 окт», «каждый понедельник», «срочно».
 * Найденное вырезается из названия.
 */
object QuickParse {
    data class Result(
        val title: String,
        val date: LocalDate? = null,
        val time: Int? = null,
        val repeat: Repeat? = null,
        val hot: Boolean = false,
        val labels: List<String> = emptyList(),
        val tagIds: Set<Long> = emptySet(),
    ) {
        val found get() = labels.isNotEmpty()
    }

    // Слово целиком: \b в Java не понимает кириллицу без флагов, поэтому явные проверки на букву.
    private const val L = "(?<![\\p{L}\\d])"
    private const val R = "(?![\\p{L}\\d])"
    private val OPTS = setOf(RegexOption.IGNORE_CASE)
    private val tagToken = Regex("""(?<!\S)#(?:"((?:\\["\\]|[^"\\\r\n])*)"|([\p{L}\p{N}_-]+))""")
    private val unfinishedTag = Regex("""(?<!\S)#(?:"((?:\\["\\]|[^"\\\r\n])*)|([\p{L}\p{N}_-]*))$""")
    private val simpleTag = Regex("""[\p{L}\p{N}_-]+""")
    private val escapedTagCharacter = Regex("""\\(["\\])""")

    fun tagSuggestions(input: String, tags: List<Tag>): List<Tag> {
        val match = unfinishedTag.find(input) ?: return emptyList()
        val quoted = match.groups[1] != null
        val prefix = tagName(match)
        return tags.filter {
            '\n' !in it.name && '\r' !in it.name && it.name.startsWith(prefix, ignoreCase = true) &&
                (quoted || !it.name.equals(prefix, ignoreCase = true))
        }.take(5)
    }

    fun completeTag(input: String, tag: Tag): String {
        val match = unfinishedTag.find(input) ?: return input
        val name = if (simpleTag.matches(tag.name)) tag.name else "\"" + tag.name.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        return input.replaceRange(match.range, "#$name ")
    }

    private fun tagName(token: MatchResult): String =
        token.groups[1]?.value?.let { name -> escapedTagCharacter.replace(name) { it.groupValues[1] } }
            ?: token.groupValues[2]

    private val weekdays = listOf(
        DayOfWeek.MONDAY to "понедельник|понедельника|понедельникам|пн",
        DayOfWeek.TUESDAY to "вторник|вторника|вторникам|вт",
        DayOfWeek.WEDNESDAY to "среду|среда|средам|ср",
        DayOfWeek.THURSDAY to "четверг|четверга|четвергам|чт",
        DayOfWeek.FRIDAY to "пятницу|пятница|пятницам|пт",
        DayOfWeek.SATURDAY to "субботу|суббота|субботам|сб",
        DayOfWeek.SUNDAY to "воскресенье|воскресеньям|вс",
    )

    private val months = listOf("янв", "фев", "мар", "апр", "ма[йя]", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

    private val numberWords = mapOf(
        "один" to 1, "одну" to 1, "два" to 2, "две" to 2, "три" to 3, "четыре" to 4, "пять" to 5,
        "шесть" to 6, "семь" to 7, "десять" to 10, "пару" to 2,
    )

    fun parse(input: String, today: LocalDate = LocalDate.now(), tags: List<Tag> = emptyList()): Result {
        val selectedTags = linkedSetOf<Tag>()
        val literals = mutableMapOf<String, String>()
        var text = tagToken.replace(input) { token ->
            val name = tagName(token)
            val tag = tags.firstOrNull { it.name.equals(name, ignoreCase = true) }
            if (tag == null) {
                val marker = "\uE000" + literals.size + "\uE001"
                literals[marker] = token.value
                marker
            } else {
                selectedTags += tag
                " "
            }
        }
        var date: LocalDate? = null
        var time: Int? = null
        var repeat: Repeat? = null
        var hot = false
        val labels = mutableListOf<String>()

        /** Ищет шаблон, при успехе вырезает его из текста. */
        fun take(pattern: String, block: (MatchResult) -> Boolean) {
            val m = Regex(pattern, OPTS).find(text) ?: return
            if (block(m)) text = text.removeRange(m.range).let { " $it " }
        }

        // ── Повтор ──
        take("$L(каждый день|ежедневно)$R") { repeat = Repeat.Daily; true }
        if (repeat == null) take("$L(по будням|каждый будний день)$R") { repeat = Repeat.Weekdays; true }
        if (repeat == null) take("$L(каждую неделю|еженедельно|раз в неделю)$R") { repeat = Repeat.Weekly; true }
        if (repeat == null) take("$L(каждый месяц|ежемесячно|раз в месяц)$R") { repeat = Repeat.Monthly; true }
        if (repeat == null) {
            for ((dow, names) in weekdays) {
                take("$L(?:кажд(?:ый|ую|ое)|по)\\s+(?:$names)$R") {
                    repeat = Repeat.Weekly
                    date = if (today.dayOfWeek == dow) today else today.with(TemporalAdjusters.next(dow))
                    true
                }
                if (repeat != null) break
            }
        }

        // ── Срочность ──
        take("$L(срочно|горит)$R") { hot = true; true }
        if (!hot) take("!+\\s*$") { hot = true; true }

        // ── Время ──
        // «15:00» — всегда время; «15.00» — только с «в»/«к», иначе это дата вроде «10.11».
        val clock: (MatchResult) -> Boolean = { m ->
            val h = m.groupValues[1].toInt()
            val mm = m.groupValues[2].toInt()
            if (h in 0..23 && mm in 0..59) { time = h * 60 + mm; true } else false
        }
        take("$L(?:в|к)\\s*(\\d{1,2})[:.](\\d{2})$R", clock)
        if (time == null) take("$L(\\d{1,2}):(\\d{2})$R", clock)
        fun hour(raw: String, partOfDay: String): Boolean {
            var h = raw.toInt()
            when (partOfDay.lowercase()) {
                "дня", "вечера" -> if (h < 12) h += 12
                "ночи" -> if (h == 12) h = 0
            }
            if (h !in 0..23) return false
            time = h * 60
            return true
        }
        // «в 15 часов», «в 7 вечера», «в 9 ч. утра» — где угодно во фразе: единица сама говорит, что это время.
        if (time == null) take("$L(?:в|к)\\s+(\\d{1,2})\\s*(?:(?:час(?:а|ов)?|ч\\.?)(?:\\s+(утра|дня|вечера|ночи))?|(утра|дня|вечера|ночи))$R") { m ->
            hour(m.groupValues[1], m.groupValues[2].ifEmpty { m.groupValues[3] })
        }
        // Голое «в 9» — только в конце фразы, иначе «купить в 3 магазинах» станет временем.
        if (time == null) take("$L(?:в|к)\\s+(\\d{1,2})(?=\\s*$|\\s*[,.!])") { m -> hour(m.groupValues[1], "") }
        if (time == null) take("$L(утром)$R") { time = 9 * 60; true }
        if (time == null) take("$L(днём|днем)$R") { time = 14 * 60; true }
        if (time == null) take("$L(вечером)$R") { time = 19 * 60; true }

        // ── Дата ──
        if (date == null) take("$L(послезавтра)$R") { date = today.plusDays(2); true }
        if (date == null) take("$L(завтра)$R") { date = today.plusDays(1); true }
        if (date == null) take("$L(сегодня)$R") { date = today; true }
        if (date == null) take("$L(?:через)\\s+(?:(\\d{1,3}|${numberWords.keys.joinToString("|")})\\s+)?(день|дня|дней|недел[юиь]|недель|месяц|месяца|месяцев)$R") { m ->
            val nRaw = m.groupValues[1].lowercase()
            val n = nRaw.toIntOrNull() ?: numberWords[nRaw] ?: 1
            val unit = m.groupValues[2].lowercase()
            date = when {
                unit.startsWith("д") -> today.plusDays(n.toLong())
                unit.startsWith("н") -> today.plusWeeks(n.toLong())
                else -> today.plusMonths(n.toLong())
            }
            true
        }
        if (date == null) take("$L(\\d{1,2})\\.(\\d{1,2})(?:\\.(\\d{2,4}))?$R") { m ->
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val y = m.groupValues[3].toIntOrNull()?.let { if (it < 100) 2000 + it else it }
            runCatching { LocalDate.of(y ?: today.year, mo, d) }.getOrNull()?.let {
                date = if (y == null && it.isBefore(today)) it.plusYears(1) else it
                true
            } ?: false
        }
        if (date == null) {
            for ((i, mon) in months.withIndex()) {
                take("$L(\\d{1,2})\\s+$mon\\p{L}*\\.?$R") { m ->
                    runCatching { LocalDate.of(today.year, i + 1, m.groupValues[1].toInt()) }.getOrNull()?.let {
                        date = if (it.isBefore(today)) it.plusYears(1) else it
                        true
                    } ?: false
                }
                if (date != null) break
            }
        }
        if (date == null) {
            for ((dow, names) in weekdays) {
                // Сокращения — только с предлогом, иначе «вс» и «ср» ловятся где попало.
                val full = names.split("|").filter { it.length > 2 }.joinToString("|")
                take("$L(?:(?:в|во)\\s+(?:$names)|(?:$full))$R") {
                    date = today.with(TemporalAdjusters.next(dow))
                    true
                }
                if (date != null) break
            }
        }

        date?.let { labels += Copy.dayLabel(it, today) }
        time?.let { labels += "⏰ " + Copy.fmtTime(it) }
        repeat?.let { labels += "🔁 " + it.short }
        if (hot) labels += "🔥 горит"
        labels += selectedTags.map { "#" + it.name }

        var clean = text
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\s+([,.!?])"), "$1")
            .replace(Regex("(?:\\s+(?:в|во|к|на))+\\s*$", OPTS), "")
            .trim()
            .trim(',', ' ')
        literals.forEach { (marker, original) -> clean = clean.replace(marker, original) }
        return Result(
            title = if (labels.isEmpty()) input.trim() else clean,
            date = date, time = time, repeat = repeat, hot = hot, labels = labels,
            tagIds = selectedTags.mapTo(linkedSetOf()) { it.id },
        )
    }
}
