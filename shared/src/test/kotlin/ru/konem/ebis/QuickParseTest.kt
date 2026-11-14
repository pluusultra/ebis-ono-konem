package ru.konem.ebis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.konem.ebis.data.Repeat
import ru.konem.ebis.ui.QuickParse
import java.time.LocalDate

class QuickParseTest {
    // Пятница, 17 октября 2026.
    private val today = LocalDate.of(2026, 10, 17)
    private fun p(s: String) = QuickParse.parse(s, today)

    @Test fun tomorrowWithTime() {
        val r = p("Скинуть Стасу завтра в 15:00")
        assertEquals("Скинуть Стасу", r.title)
        assertEquals(today.plusDays(1), r.date)
        assertEquals(15 * 60, r.time)
    }

    @Test fun plainTitleUntouched() {
        val r = p("Купить завтрак и молоко")
        assertFalse(r.found)
        assertEquals("Купить завтрак и молоко", r.title)
    }

    @Test fun dayAfterTomorrowNotTomorrow() = assertEquals(today.plusDays(2), p("отчёт послезавтра").date)

    @Test fun inDays() {
        assertEquals(today.plusDays(3), p("позвонить через 3 дня").date)
        assertEquals(today.plusWeeks(1), p("позвонить через неделю").date)
        assertEquals(today.plusWeeks(2), p("через две недели отпуск").date)
    }

    @Test fun weekday() {
        val r = p("созвон в пн")
        assertEquals(LocalDate.of(2026, 10, 19), r.date)
        assertEquals("созвон", r.title)
        assertEquals(LocalDate.of(2026, 10, 23), p("отчёт в пятницу").date)
    }

    @Test fun dateNotTime() {
        val r = p("сдать отчёт 10.11")
        assertEquals(LocalDate.of(2026, 11, 10), r.date)
        assertNull(r.time)
    }

    @Test fun monthName() {
        assertEquals(LocalDate.of(2026, 10, 25), p("день рождения 25 окт").date)
        assertEquals(LocalDate.of(2027, 3, 1), p("налоги 1 марта").date)
    }

    @Test fun hourWords() {
        assertEquals(19 * 60, p("кино в 7 вечера").time)
        assertEquals(9 * 60, p("пробежка в 9").time)
        assertNull(p("купить в 3 магазинах").time)
    }

    @Test fun hoursMidPhrase() {
        val r = p("завтра в 15 часов проверить")
        assertEquals(today.plusDays(1), r.date)
        assertEquals(15 * 60, r.time)
        assertEquals("проверить", r.title)
        assertEquals(15 * 60, p("завтра в 15 часов").time)
        assertEquals(19 * 60, p("в 7 вечера сходить в кино").time)
        assertEquals(9 * 60, p("в 9 ч. утра пробежка").time)
    }

    @Test fun repeatAndHot() {
        val r = p("таблетки каждый день в 9:30 срочно")
        assertEquals(Repeat.Daily, r.repeat)
        assertEquals(9 * 60 + 30, r.time)
        assertTrue(r.hot)
        assertEquals("таблетки", r.title)
        val w = p("планёрка каждый понедельник")
        assertEquals(Repeat.Weekly, w.repeat)
        assertEquals(LocalDate.of(2026, 10, 19), w.date)
    }

    @Test fun nextRepeatDate() {
        assertEquals(LocalDate.of(2026, 10, 19), Repeat.Weekdays.next(today, today))
        assertEquals(today.plusDays(1), Repeat.Daily.next(today.minusDays(5), today))
    }
}
