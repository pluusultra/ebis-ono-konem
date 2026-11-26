package ru.konem.ebis.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.konem.ebis.data.Repeat
import java.time.LocalDate
import java.util.UUID
import java.util.prefs.Preferences

@OptIn(ExperimentalTestApi::class)
class TaskToolsTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun searchOpensFinishedTaskAndBatchMovesSelectedOverdueTask() {
        val today = LocalDate.now()
        val node = "ru/konem/ebis-task-tools-test/" + UUID.randomUUID()
        val state = AppState(DesktopStore(node).apply { this.folder = this@TaskToolsTest.folder.root })
        state.add("Оплатить счёт", today, null, Repeat.None, false, desc = "Электричество")
        val finished = state.tasks.single().id
        state.toggle(finished)
        state.add("Ответить на письмо", today.minusDays(1), null, Repeat.None, false)
        state.add("Купить корм", today.minusDays(2), null, Repeat.None, false)
        state.tools = TaskTool.Search

        try {
            runComposeUiTest {
                setContent {
                    EbisTheme(ThemeMode.Light) {
                        Box(Modifier.fillMaxSize()) { TaskTools(state, today) }
                    }
                }
                onNode(hasSetTextAction()).performTextInput("электричество")
                onNodeWithText("Оплатить счёт").performClick()
                runOnIdle {
                    assertEquals(finished, state.editing?.id)
                    state.editing = null
                    state.tools = TaskTool.Overdue
                }
                onNodeWithText("Ответить на письмо").performClick()
                onNodeWithText("На завтра").performClick()
                runOnIdle {
                    assertEquals(today.plusDays(1), state.tasks.single { it.title == "Ответить на письмо" }.due)
                    assertEquals(today.minusDays(2), state.tasks.single { it.title == "Купить корм" }.due)
                    assertEquals(null, state.tools)
                }
            }
        } finally {
            Preferences.userRoot().node(node).removeNode()
        }
    }
}
