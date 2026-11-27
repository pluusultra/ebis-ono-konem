package ru.konem.ebis.desktop

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.prefs.Preferences

/** Свой фон не должен перебивать тему: светлый живёт в дне, тёмный — в ночи. */
class LookTest {
    private val mint = BgPresets.first { it.first == "Мята" }.second
    private val graphite = BgPresets.first { it.first == "Графит" }.second

    @Test
    fun lightBackgroundDoesNotBlockNight() {
        val look = Look().withBg(mint, dark = false)
        assertEquals(mint, LightScheme.with(look).paper)
        val night = DarkScheme.with(look)
        assertTrue(night.isDark)
        assertEquals(DarkScheme.paper, night.paper)
    }

    @Test
    fun darkBackgroundGoesToNightSlot() {
        val look = Look().withBg(graphite, dark = false)
        assertNull(look.bgLight)
        assertEquals(graphite, look.bgDark)
        assertEquals(graphite, DarkScheme.with(look).paper)
        assertFalse(LightScheme.with(look).isDark)
    }

    @Test
    fun resetClearsOnlyCurrentTheme() {
        val look = Look(bgLight = mint, bgDark = graphite).withBg(null, dark = true)
        assertEquals(mint, look.bgLight)
        assertNull(look.bgDark)
    }

    @Test
    fun oldSingleBackgroundIsMigrated() {
        val node = Preferences.userRoot().node("ru/konem/ebis-desktop-look-test")
        try {
            node.put("look.bg", Color(0xFFE6F4EC).toArgbLong())
            val l = LookStore(node).load()
            assertEquals(Color(0xFFE6F4EC), l.bgLight)
            assertNull(l.bgDark)
        } finally {
            node.removeNode()
        }
    }

    @Test
    fun mascotPreferenceDefaultsAndRoundTrips() {
        val node = Preferences.userRoot().node("ru/konem/ebis-desktop-mascot-test")
        try {
            val store = LookStore(node)
            HorseMascot.entries.forEach { assertNotNull(javaClass.getResource("/${it.image}")) }
            assertEquals(HorseMascot.Sleepy, store.load().mascot)
            store.save(Look(mascot = HorseMascot.Heckler))
            assertEquals(HorseMascot.Heckler, LookStore(node).load().mascot)
            node.put("look.mascot", "unknown")
            assertEquals(HorseMascot.Sleepy, store.load().mascot)
        } finally {
            node.removeNode()
        }
    }

    private fun Color.toArgbLong(): String {
        val a = (alpha * 255).toInt(); val r = (red * 255).toInt(); val g = (green * 255).toInt(); val b = (blue * 255).toInt()
        return ((a shl 24) or (r shl 16) or (g shl 8) or b).toLong().toString()
    }
}
