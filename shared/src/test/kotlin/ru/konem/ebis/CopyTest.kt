package ru.konem.ebis

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.konem.ebis.ui.Copy

class CopyTest {
    @Test
    fun countsSoundRussian() {
        assertEquals("Осталась 1 херня", Copy.left(1))
        assertEquals("Осталось 3 херни", Copy.left(3))
        assertEquals("Осталось 5 штук херни", Copy.left(5))
        assertEquals("Осталось 12 штук херни", Copy.left(12))
        assertEquals("Осталась 21 херня", Copy.left(21))
        assertEquals("Осталось 22 херни", Copy.left(22))
    }
}
