package ru.konem.ebis.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Test
import java.nio.file.Files
import java.util.prefs.Preferences

/** После смены масштаба окно не должно остаться закрытым заливкой (выглядело как пустое, «упавшее» окно). */
@OptIn(ExperimentalTestApi::class)
class ScaleChangeTest {
    @Test
    fun windowVisibleAfterScaleChange() {
        val dir = Files.createTempDirectory("ebis-scale").toFile()
        val node = "ru/konem/ebis-desktop-scale-test"
        val store = DesktopStore(node).apply { folder = dir }
        val state = AppState(store)
        state.openSettings(SettingsPane.General)
        try {
            // Часы виртуальные и всё в одном потоке: бесконечные анимации и опрос файла не мешают.
            runComposeUiTest {
                mainClock.autoAdvance = false
                setContent {
                    EbisTheme(ThemeMode.Light, state.look) { App(state, remember { FocusRequester() }) { null } }
                }
                mainClock.advanceTimeBy(1500)
                for ((s, label) in listOf(1.3f to "130%", 1f to "100%", 1.15f to "115%")) {
                    onAllNodesWithText(label)[0].performClick()
                    // Тестовые часы идут кадрами: даём с запасом доиграть заливку туда и обратно.
                    repeat(10) { mainClock.advanceTimeBy(300) }
                    check(state.look.scale == s) { "масштаб не применился: ${state.look.scale} != $s" }
                    check(state.pendingLook == null) { "pendingLook не сброшен" }
                    val px = onRoot().captureToImage().toPixelMap()
                    val colors = HashSet<Int>()
                    for (x in 0 until px.width step 23) for (y in 0 until px.height step 17) colors += px[x, y].hashCode()
                    check(colors.size > 5) { "после масштаба $s окно залито одним цветом" }
                }
            }
        } finally {
            Preferences.userRoot().node(node).removeNode()
            dir.deleteRecursively()
        }
    }
}
