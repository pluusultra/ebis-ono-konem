package ru.konem.ebis.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.konem.ebis.data.Repeat
import ru.konem.ebis.data.Task
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import java.util.prefs.Preferences

@OptIn(ExperimentalTestApi::class)
class SelectedDaysDialogTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun savesBothSelectedDays() {
        val today = LocalDate.of(2026, 11, 23)
        val node = "ru/konem/ebis-desktop-repeat-test/${UUID.randomUUID()}"
        val state = AppState(DesktopStore(node).apply { this.folder = this@SelectedDaysDialogTest.folder.root })
        state.editing = Task(id = DRAFT_ID, title = "Задача", desc = "", due = today)

        try {
            runComposeUiTest {
                setContent {
                    EbisTheme(ThemeMode.Light) {
                        Box(Modifier.fillMaxSize()) { EditOverlay(state, today) }
                    }
                }
                onNodeWithText("По дням недели").performClick()
                onNodeWithText("Накинуть").performClick()
                runOnIdle { assertTrue(state.tasks.isEmpty()) }

                onNodeWithText("вт").performClick()
                onNodeWithText("пт").performClick()
                onNodeWithText("Накинуть").performClick()
                runOnIdle {
                    val task = state.tasks.single()
                    assertEquals(Repeat.SelectedDays, task.repeat)
                    assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY), task.repeatDays)
                    assertEquals(today.plusDays(1), task.due)
                }
            }
        } finally {
            Preferences.userRoot().node(node).removeNode()
        }
    }
}
