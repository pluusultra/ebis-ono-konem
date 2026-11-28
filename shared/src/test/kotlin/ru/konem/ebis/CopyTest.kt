package ru.konem.ebis

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.konem.ebis.ui.Copy

class CopyTest {
    @Test
    fun countsSoundRussian() {
        assertEquals("Осталась 1 задача", Copy.left(1))
        assertEquals("Осталось 3 задачи", Copy.left(3))
        assertEquals("Осталось 5 задач", Copy.left(5))
        assertEquals("Осталось 12 задач", Copy.left(12))
        assertEquals("Осталась 21 задача", Copy.left(21))
        assertEquals("Осталось 22 задачи", Copy.left(22))
    }
}
