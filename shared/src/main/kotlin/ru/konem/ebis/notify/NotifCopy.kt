package ru.konem.ebis.notify

import ru.konem.ebis.ui.Copy.plural
import ru.konem.ebis.ui.Copy.taskCount

/** Тексты уведомлений. Каждый раз берётся случайный вариант, чтобы не приедалось. */
object NotifCopy {
    data class Msg(val title: String, val text: String)

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
                Msg("Подозрительно пусто", "На сегодня ни одной задачи. Либо гений, либо забыл записать."),
                Msg("Свобода, жеребец", "Задач на сегодня ноль. Конь тоже взял выходной."),
                Msg("Конь в замешательстве", "Сегодня делать нечего. Это ловушка?"),
            ).random()
            total >= 7 -> listOf(
                Msg("Ну ты попал", "${taskCount(total)} на сегодня. Можешь сразу поплакать.$rot"),
                Msg("Аврал подкрался незаметно", "${taskCount(total)} на сегодня. Конь в шоке, ты тоже?$rot"),
                Msg("Доброе утро, мученик", "${taskCount(total)}. Кто это всё навешал? А, ты.$rot"),
            ).random()
            else -> {
                val title = listOf(
                    "Подъём, бедолага 🐴", "Доброе утро (нет)", "Конь уже запряжён",
                    "Подъём, сено само себя не съест", "Новый день — новый завал", "Утро. Опять. Вот же ж.",
                ).random()
                val text = listOf(
                    "Сегодня ${taskCount(total)}. Сами себя не сделают.",
                    "${taskCount(total)} на сегодня. Хочешь не хочешь, а надо.",
                    "На сегодня ${taskCount(total)}. Кофе, и рысью.",
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
                Msg("Осталась одна задача", "Добей $t и свободен, жеребец."),
                Msg("Последний рывок", "Только $t осталась. Давай, добей."),
                Msg("Финишная прямая", "$t — и можно забить на всё до завтра."),
            ).random()
            done == 0 -> listOf(
                Msg("Ну чё, как успехи?", "$t всё ещё висит. Сделано 0 из $total. Стабильность — признак мастерства."),
                Msg("Конь смотрит с осуждением", "Полдня как не бывало, а $t так и не сделана."),
                Msg("Эй, бедолага", "$t сама себя не сделает, сколько на неё ни смотри."),
                Msg("Тук-тук. Опять я", "Это $t. Я всё ещё тут. 0 из $total, кстати."),
            ).random()
            else -> listOf(
                Msg("$done из $total готово", "Конь почти не стыдится тебя. Следующая — $t."),
                Msg("Неплохо, жеребец", "Уже $done из $total. Дальше $t, не сбавляй ход."),
                Msg("Идёт как танк", "Сделано $done, осталось $remaining. Дальше $t."),
            ).random()
        }
    }

    // ── Позорный столб ──────────────────────────────────────────
    private val jubilees = setOf(100, 200, 300, 365, 500, 666, 730, 1000)

    fun shame(task: String, days: Int, moreOverdue: Int): Msg {
        val t = "«$task»"
        val more = if (moreOverdue > 0) " И ещё $moreOverdue ${plural(moreOverdue, "просрочка", "просрочки", "просрочек")} в очереди." else ""
        val msg = when {
            days in jubilees -> Msg("🎂 С юбилеем, что ли!", "$t висит ровно ${dney(days)}. Торт не дарим.")
            days == 1 -> listOf(
                Msg("Привет из вчера", "$t перешла в разряд «ну завтра точно»."),
                Msg("Проспали", "$t должна была быть вчера. Но у нас же тут машина времени, да?"),
            ).random()
            days < 7 -> listOf(
                Msg("Чувствуешь запах?", "$t тухнет уже ${dney(days)}."),
                Msg("Протухло", "$t висит ${dney(days)}. Воняет на весь телефон."),
            ).random()
            days < 30 -> listOf(
                Msg(if (days < 14) "Неделя позора" else "Позорный столб", "$t висит уже ${dney(days)}. Это уже традиция."),
                Msg("Чемпион по откладыванию", "$t ждёт уже ${dney(days)}. Конь бы давно сделал."),
            ).random()
            days < 100 -> Msg("🎖 Задача-ветеран", "$t — ${dney(days)} в строю. Заслужила медаль, а тебе конь выписал выговор.")
            else -> Msg("🪦 Здесь покоится $t", "Висит ${dney(days)}. Может, пора в корзину?")
        }
        return msg.copy(text = msg.text + more)
    }

    // ── Вечерний итог ───────────────────────────────────────────
    fun evening(done: Int, remaining: Int, tomorrow: Int): Msg {
        val tm = if (tomorrow > 0) " Завтра ждёт ещё ${taskCount(tomorrow)}." else ""
        return when {
            done > 0 && remaining == 0 -> listOf(
                Msg("Ого, всё закрыл", "Сегодня $done из $done. Конь в шоке, иди отдыхай.$tm"),
                Msg("Ну ты легенда", "Ничего больше не висит. Запишу этот день в историю.$tm"),
            ).random()
            done > 0 -> listOf(
                Msg("Итоги дня", "Закрыл $done, а $remaining ${plural(remaining, "переползает", "переползают", "переползают")} на завтра. Конь делает вид, что так и задумано.$tm"),
                Msg("Ну, хоть что-то", "$done сделано, $remaining висит. Сойдёт для сельской местности.$tm"),
            ).random()
            remaining > 0 -> listOf(
                Msg("Итоги дня: круглый ноль", "Ноль из $remaining. Завтра будет то же самое, но больше.$tm"),
                Msg("Ну и денёк", "Ничего не сделано, висит $remaining. Иди поплачь, разрешаю.$tm"),
            ).random()
            else -> Msg("Итоги дня", "План на день: ничего. Выполнен. Красавчик.$tm")
        }
    }

    val celebrate get() = listOf(
        Msg("Всё закрыл?! 🏆", "Иди отдыхай, жеребец. На сегодня ни одной задачи."),
        Msg("Вот это галоп, всё сделано 🏆", "Конь снимает шляпу. До завтра можешь не возвращаться."),
    ).random()

    // ── Кнопки и тосты ──────────────────────────────────────────
    const val actionDone = "Сделал"
    const val actionTomorrow = "Завтра, честно"
    const val actionDelete = "В корзину"
    const val actionMute = "Конь, помолчи"

    const val toastDone = "Конь бы гордился 🐴"
    const val toastTomorrow = "Завтра так завтра. Ага, конечно."
    const val toastDelete = "В корзину. Не жалко."
    const val toastMute = "Ладно, молчу до завтра 🤐"

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
        snoozes <= 0 -> Msg("ГОРИТ!", "Ты сам поставил это на $at. Конь пришёл спросить, где результат.")
        snoozes == 1 -> Msg("ОПЯТЬ ГОРИТ", "Отложил разок. Задача сама себя не сделала, я проверял.")
        snoozes == 2 -> Msg("ПОЖАР В КОНЮШНЕ", "Второе «потом». Конь уже ржёт над тобой при соседях.")
        else -> Msg("СГОРЕЛО ДОТЛА", "Откладываешь это уже ${snoozes + 1}-й раз. Конь записал тебя в позорный столб.")
    }

    const val alarmDone = "Сделал, конь"

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
