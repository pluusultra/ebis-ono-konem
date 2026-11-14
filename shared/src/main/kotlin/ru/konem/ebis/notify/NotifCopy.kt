package ru.konem.ebis.notify

import ru.konem.ebis.ui.Copy
import ru.konem.ebis.ui.Copy.plural

/** Тексты уведомлений. Каждый раз берётся случайный вариант, чтобы не приедалось. */
object NotifCopy {
    data class Msg(val title: String, val text: String)

    private fun herni(n: Int) = Copy.herni(n)
    private fun zadach(n: Int) = "$n ${plural(n, "задача", "задачи", "задач")}"
    private fun dney(n: Int) = "$n ${plural(n, "день", "дня", "дней")}"

    // ── Утренний пинок ──────────────────────────────────────────
    fun morning(total: Int, overdue: Int): Msg {
        val rot = when {
            overdue == 0 -> ""
            overdue == 1 -> " Одна из них уже протухла."
            else -> " Из них $overdue уже ${plural(overdue, "протухла", "протухли", "протухли")}."
        }
        return when {
            total == 0 -> listOf(
                Msg("Подозрительно пусто", "На сегодня ни хера. Либо гений, либо забыл записать."),
                Msg("Свобода, сука", "Задач на сегодня ноль. Не обосрись от счастья."),
                Msg("Конь в замешательстве", "Сегодня делать нечего. Это ловушка?"),
            ).random()
            total >= 7 -> listOf(
                Msg("Ну ты попал", "${herni(total)} на сегодня. Можешь сразу поплакать.$rot"),
                Msg("Пиздец подкрался незаметно", "${zadach(total)} на сегодня. Конь в ахуе, ты тоже?$rot"),
                Msg("Доброе утро, мученик", "${herni(total)}. Кто это всё навешал? А, ты.$rot"),
            ).random()
            else -> {
                val title = listOf(
                    "Подъём, бедолага 🐴", "Доброе утро (нет)", "Конь уже запряжён",
                    "Вставай, хули лежишь", "Новый день — новая херня", "Утро. Опять. Блять.",
                ).random()
                val text = listOf(
                    "Сегодня ${herni(total)}. Сами себя не сделают.",
                    "${zadach(total)} на сегодня. Ебись оно конём, но надо.",
                    "На сегодня ${herni(total)}. Кофе, и погнали нахер.",
                ).random()
                Msg(title, text + rot)
            }
        }
    }

    // ── Дневное нытьё ───────────────────────────────────────────
    fun nag(task: String, done: Int, remaining: Int): Msg {
        val total = done + remaining
        val t = "«$task»"
        return when {
            remaining == 1 -> listOf(
                Msg("Осталась одна херня", "Добей $t и свободен, жеребец."),
                Msg("Последний рывок", "Только $t осталась. Давай, ёпта."),
                Msg("Финишная прямая", "$t — и можно забить на всё до завтра."),
            ).random()
            done == 0 -> listOf(
                Msg("Ну чё, как успехи?", "$t всё ещё висит. Сделано 0 из $total. Стабильность — признак мастерства."),
                Msg("Конь смотрит с осуждением", "Полдня в пизду, а $t так и не сделана."),
                Msg("Эй, бедолага", "$t сама себя не сделает, хоть ты усрись."),
                Msg("Тук-тук, блять", "Это $t. Я всё ещё тут. 0 из $total, кстати."),
            ).random()
            else -> listOf(
                Msg("$done из $total готово", "Конь почти не стыдится тебя. Следующая — $t."),
                Msg("Неплохо, сука", "Уже $done из $total. Дальше $t, не съёбывай."),
                Msg("Идёт ебашит", "Сделано $done, осталось $remaining. Дальше $t."),
            ).random()
        }
    }

    // ── Позорный столб ──────────────────────────────────────────
    private val jubilees = setOf(100, 200, 300, 365, 500, 666, 730, 1000)

    fun shame(task: String, days: Int, moreOverdue: Int): Msg {
        val t = "«$task»"
        val more = if (moreOverdue > 0) " И ещё $moreOverdue ${plural(moreOverdue, "просрочка", "просрочки", "просрочек")} в очереди." else ""
        val msg = when {
            days in jubilees -> Msg("🎂 Юбилей, ёпта!", "$t висит ровно ${dney(days)}. Торт не дарим.")
            days == 1 -> listOf(
                Msg("Вчерашняя херня", "$t перешла в разряд «ну завтра точно»."),
                Msg("Проёбано", "$t должна была быть вчера. Но кого это ебёт, да?"),
            ).random()
            days < 7 -> listOf(
                Msg("Чувствуешь запах?", "$t тухнет уже ${dney(days)}."),
                Msg("Протухло", "$t висит ${dney(days)}. Воняет на весь телефон."),
            ).random()
            days < 30 -> listOf(
                Msg(if (days < 14) "Неделя позора" else "Позорный столб", "$t просрана на ${dney(days)}. Это уже традиция, блять."),
                Msg("Ну ты и мудак, конечно", "$t ждёт уже ${dney(days)}. Конь бы давно сделал."),
            ).random()
            days < 100 -> Msg("🎖 Задача-ветеран", "$t — ${dney(days)} в строю. Заслужила медаль, а ты — пиздюлей.")
            else -> Msg("🪦 Здесь покоится $t", "Просрано ${dney(days)}. Может, уже нахер её?")
        }
        return msg.copy(text = msg.text + more)
    }

    // ── Вечерний итог ───────────────────────────────────────────
    fun evening(done: Int, remaining: Int, tomorrow: Int): Msg {
        val tm = if (tomorrow > 0) " Завтра ждёт ещё ${herni(tomorrow)}." else ""
        return when {
            done > 0 && remaining == 0 -> listOf(
                Msg("Ебать, всё закрыл", "Сегодня $done из $done. Конь в шоке, иди отдыхай.$tm"),
                Msg("Легенда, блять", "Ни одной висящей херни. Запишу этот день в историю.$tm"),
            ).random()
            done > 0 -> listOf(
                Msg("Итоги дня", "Закрыл $done, а $remaining ${plural(remaining, "переползает", "переползают", "переползают")} на завтра. Ебись оно конём.$tm"),
                Msg("Ну, хоть что-то", "$done сделано, $remaining висит. Сойдёт для сельской местности.$tm"),
            ).random()
            remaining > 0 -> listOf(
                Msg("Итоги дня: хуй", "Ноль из $remaining. Завтра будет то же самое, но больше.$tm"),
                Msg("Ну и денёк", "Сделано ни хера, висит $remaining. Иди поплачь, разрешаю.$tm"),
            ).random()
            else -> Msg("Итоги дня", "Задач не было, делать ни хера не пришлось. Красавчик.$tm")
        }
    }

    val celebrate get() = listOf(
        Msg("Всё закрыл?! 🏆", "Иди отдыхай, жеребец. На сегодня ни одной херни."),
        Msg("Ахуеть, всё сделано 🏆", "Конь снимает шляпу. До завтра можешь не возвращаться."),
    ).random()

    // ── Кнопки и тосты ──────────────────────────────────────────
    const val actionDone = "Сделал"
    const val actionTomorrow = "Завтра, честно"
    const val actionDelete = "Нахер"
    const val actionMute = "Отъебись на сегодня"

    const val toastDone = "Конь бы гордился 🐴"
    const val toastTomorrow = "Завтра так завтра. Ага, конечно."
    const val toastDelete = "Ушло нахер. Не жалко."
    const val toastMute = "Ладно, отъебался до завтра 🤐"

    // ── Напоминалка про конкретную задачу ──────────────────────
    fun remind(title: String): Msg = Msg(
        "⏰ $title",
        listOf(
            "Время пришло. Конь стоит и смотрит.",
            "Ты сам просил напомнить. Вот, напоминаю.",
            "Сейчас или никогда. Хотя «никогда» — не дата.",
            "Хватит листать ленту, иди делай.",
        ).random(),
    )

    const val actionSnooze = "Через час"
    const val toastSnooze = "Ладно, через час снова заору"

    // ── Будильник «Горит» ──────────────────────────────────────
    /** Чем чаще откладываешь, тем злее конь. [at] — на сколько ставил, «15:00». */
    fun alarm(at: String, snoozes: Int): Msg = when {
        snoozes <= 0 -> Msg("ГОРИТ, БЛЯТЬ", "Ты сам поставил это на $at. Конь пришёл спросить, где результат.")
        snoozes == 1 -> Msg("ОПЯТЬ ГОРИТ", "Отложил разок. Задача сама себя не сделала, я проверял.")
        snoozes == 2 -> Msg("ПОЖАР В КОНЮШНЕ", "Второе «потом». Конь уже ржёт над тобой при соседях.")
        else -> Msg("ВСЁ СГОРЕЛО НАХЕР", "Откладываешь это уже ${snoozes + 1}-й раз. Конь записал тебя в позорный столб.")
    }

    const val alarmDone = "Сделал, отъебись"

    fun alarmSnooze(min: Int, snoozes: Int): String = when {
        snoozes <= 0 -> "Через ${minutes(min)}"
        snoozes == 1 -> "Ещё ${minutes(min)}"
        snoozes == 2 -> "Потом (ну-ну)"
        else -> "Потом (позорище)"
    }

    fun toastAlarmSnooze(min: Int) = "Ладно. Через ${minutes(min)} снова заору"

    /** «10 мин», «30 мин», «час», «2 часа». */
    fun minutes(min: Int): String = when {
        min == 60 -> "час"
        min % 60 == 0 -> "${min / 60} ${plural(min / 60, "час", "часа", "часов")}"
        else -> "$min мин"
    }
}
