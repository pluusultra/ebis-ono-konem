package ru.konem.ebis.ui

import ru.konem.ebis.data.Repeat
import ru.konem.ebis.data.Task
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Все тексты интерфейса в одном месте. */
object Copy {
    fun plural(n: Int, one: String, few: String, many: String): String {
        val m10 = n % 10
        val m100 = n % 100
        return when {
            m10 == 1 && m100 != 11 -> one
            m10 in 2..4 && (m100 < 12 || m100 > 14) -> few
            else -> many
        }
    }

    /** «1 херня», «3 херни», «5 штук херни»: «5 херней» по-русски не говорят. */
    fun herni(n: Int): String = plural(n, "$n херня", "$n херни", "$n ${plural(n, "штука", "штуки", "штук")} херни")

    /** «Осталась 1 херня», «Осталось 3 херни», «Осталось 5 штук херни». */
    fun left(n: Int): String = "${plural(n, "Осталась", "Осталось", "Осталось")} ${herni(n)}"

    fun subtitle(n: Int): String = when {
        n == 0 -> "Ноль херни на сегодня. Подозрительно."
        n >= 7 -> "${left(n)}. Ну ты попал."
        else -> "${left(n)} на сегодня"
    }

    val praise = listOf("Ни хера себе, сделал 😳", "Конь бы гордился 🐴", "Минус одна херня", "Глянь-ка, может же", "Записал в легенды")
    val undo = listOf("Передумал? Ну конечно.", "Вернул обратно. Классика.")
    const val deleted = "Ушло нахер. Не жалко."
    val postponed = listOf("Отложил. Завтра точно (нет)", "Перенёс. Конь всё видит 👀", "Завтра так завтра")
    const val edited = "Переписал. Суть та же, херня та же."
    const val undoLabel = "Верни"
    const val restored = "Вернул как было"
    const val saved = "Повешено. Удачи, бедолага."
    const val emptyTitle = "Без названия даже конь не поймёт."

    const val moodWork = "⛓️ Режим раба активирован. Начни с горящего."
    const val moodCry = "💧 Поплачь, потом закрой хоть одну. Конь верит в тебя (нет)."

    fun allDone(streak: Int, record: Boolean): String = when {
        record && streak >= 2 -> "Рекорд: $streak ${plural(streak, "день", "дня", "дней")} подряд всё закрыто! 🏆🐴"
        streak >= 2 -> "Всё закрыл, $streak-й день подряд. Жеребец 🏆"
        else -> "Всё закрыл?! Иди отдыхай, жеребец 🏆"
    }

    fun fmtTime(min: Int) = "%d:%02d".format(min / 60, min % 60)

    private val weekdays = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
    fun shortWeekday(day: DayOfWeek) = weekdays[day.value - 1]

    fun repeatLabel(task: Task): String = when (task.repeat) {
        Repeat.SelectedDays -> task.repeatDays.sorted().joinToString(", ", transform = ::shortWeekday)
        else -> task.repeat.short
    }

    /** «сегодня», «завтра», «пт, 24 окт». */
    fun dayLabel(d: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(today, d).toInt()) {
        0 -> "сегодня"
        1 -> "завтра"
        2 -> "послезавтра"
        else -> "${shortWeekday(d.dayOfWeek)}, ${shortDate(d)}"
    }

    private val months = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
    fun shortDate(d: LocalDate) = "${d.dayOfMonth} ${months[d.monthValue - 1]}"

    enum class BadgeKind { Normal, Hot, Late }

    fun dueBadge(due: LocalDate, done: Boolean, today: LocalDate): Pair<String, BadgeKind> {
        if (done) return "закрыто" to BadgeKind.Normal
        val d = ChronoUnit.DAYS.between(today, due).toInt()
        return when {
            d < 0 -> "Просрано на ${-d} ${plural(-d, "день", "дня", "дней")}" to BadgeKind.Late
            d == 0 -> "Сегодня" to BadgeKind.Hot
            d == 1 -> "Завтра" to BadgeKind.Normal
            else -> shortDate(due) to BadgeKind.Normal
        }
    }
}
