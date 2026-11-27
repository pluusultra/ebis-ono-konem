package ru.konem.ebis.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.konem.ebis.data.Repeat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.prefs.Preferences

@OptIn(ExperimentalTestApi::class)
class TaskToolsTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun searchAndOverdueSelectionBothWorkInTheTaskList() {
        val today = LocalDate.now()
        val node = "ru/konem/ebis-task-tools-test/" + UUID.randomUUID()
        val state = AppState(DesktopStore(node).apply { this.folder = this@TaskToolsTest.folder.root })
        state.add("Оплатить счёт", today, null, Repeat.None, false, desc = "Электричество")
        val finished = state.tasks.single().id
        state.add("Ответить на письмо", today.minusDays(1), null, Repeat.None, false)
        state.add("Купить корм", today.minusDays(2), null, Repeat.None, false)

        try {
            runComposeUiTest {
                setContent {
                    EbisTheme(ThemeMode.Light) {
                        Box(Modifier.fillMaxSize().onPreviewKeyEvent { onKey(it, state) {} }) {
                            Main(state, today, FocusRequester(), compact = false)
                        }
                    }
                }
                onNodeWithText("Сначала старые").performClick()
                onNodeWithText("Сначала новые").performClick()
                runOnIdle {
                    assertEquals(true, state.store.todayNewFirst)
                    state.selectTab(Tab.Done)
                }
                val search = onNodeWithContentDescription("Поиск по задачам")
                runOnIdle { state.focusSearch() }
                search.assertIsFocused()
                search.performKeyInput { pressKey(Key.Escape) }
                search.assertIsNotFocused()

                runOnIdle { state.focusSearch() }
                search.performTextInput("электричество")
                search.performKeyInput { pressKey(Key.Escape) }
                search.assertIsNotFocused()
                runOnIdle { assertEquals("", state.searchQuery) }

                search.performTextInput("электричество")
                onNodeWithContentDescription("Отметить выполненной").performClick()
                runOnIdle {
                    assertEquals(true, state.tasks.single { it.id == finished }.done)
                    assertEquals(null, state.editing)
                    assertEquals("электричество", state.searchQuery)
                }
                onNodeWithText("Оплатить счёт").performClick()
                runOnIdle {
                    assertEquals(finished, state.editing?.id)
                    state.editing = null
                }
                onNodeWithContentDescription("Вернуть в активные").performClick()
                runOnIdle { assertEquals(false, state.tasks.single { it.id == finished }.done) }
                onNodeWithContentDescription("Очистить поиск").performClick()
                runOnIdle {
                    assertEquals("", state.searchQuery)
                    state.selectTab(Tab.Today)
                }
                onNodeWithText("Разгрести просроченное").performClick()
                onNodeWithText("Выбрано 0 из 2").assertExists()
                onNodeWithText("Выбрать все").performClick()
                onNodeWithText("Выбрано 2 из 2").assertExists()
                onNodeWithText("Снять все").performClick()
                onNodeWithText("Ответить на письмо").performClick()
                onNodeWithText("Выбрано 1 из 2").assertExists()
                runOnIdle {
                    val selected = state.tasks.single { it.title == "Ответить на письмо" }
                    assertEquals(setOf(selected.id), state.overdueSelection)
                    assertEquals(false, selected.done)
                    assertEquals(today.minusDays(1), selected.due)
                    assertEquals(null, state.editing)
                }
                onNodeWithText("Перенести").performClick()
                val later = today.plusDays(5)
                onNodeWithContentDescription("Дата переноса").performTextInput(later.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
                onNodeWithText("Применить").performClick()
                runOnIdle {
                    assertEquals(later, state.tasks.single { it.title == "Ответить на письмо" }.due)
                    assertEquals(today.minusDays(2), state.tasks.single { it.title == "Купить корм" }.due)
                    assertEquals(null, state.overdueSelection)
                    state.toast?.undo?.invoke()
                    assertEquals(today.minusDays(1), state.tasks.single { it.title == "Ответить на письмо" }.due)
                }
            }
        } finally {
            Preferences.userRoot().node(node).removeNode()
        }
    }
}
