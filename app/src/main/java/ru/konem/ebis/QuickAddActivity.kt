package ru.konem.ebis

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.konem.ebis.data.Repeat
import ru.konem.ebis.data.TaskRepo
import ru.konem.ebis.notify.Prefs
import ru.konem.ebis.ui.BrutalButton
import ru.konem.ebis.ui.Copy
import ru.konem.ebis.ui.HeaderButton
import ru.konem.ebis.ui.QuickParse
import ru.konem.ebis.ui.brutal
import ru.konem.ebis.ui.theme.Body
import ru.konem.ebis.ui.theme.Display
import ru.konem.ebis.ui.theme.EbisTheme
import ru.konem.ebis.ui.theme.Mono
import ru.konem.ebis.ui.theme.Palette
import ru.konem.ebis.ui.theme.ThemeMode
import ru.konem.ebis.widget.Widgets
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * «+» на виджетах: прозрачное окно поверх рабочего стола — снизу карточка с полем, клавиатура сразу открыта.
 * Приложение целиком не открывается: своя taskAffinity и excludeFromRecents (см. манифест).
 * Enter — задача повешена, окно закрылось, виджеты перерисованы.
 */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        ThemeMode.mode = Prefs(this).themeMode
        TaskRepo.init(this)
        setContent {
            EbisTheme(paintWindow = false) {
                QuickAdd(onClose = ::close, onSave = ::save, onExpand = ::expand)
            }
        }
    }

    private fun save(text: String) {
        val today = LocalDate.now()
        val p = QuickParse.parse(text, today)
        val title = (if (p.found) p.title else text).trim()
        TaskRepo.add(title, "", p.date ?: today, p.time, p.repeat ?: Repeat.None, p.hot)
        // Человек смотрит на виджет — перерисовываем сразу, без обычной паузы.
        Widgets.refresh(this, delayMs = 0)
        Toast.makeText(applicationContext, Copy.saved, Toast.LENGTH_SHORT).show()
        close()
    }

    /** «⤢» — нужна дата, суть или повтор: открываем полную форму с уже набранным текстом. */
    private fun expand(text: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(Incoming.ACTION_ADD)
                .putExtra(Intent.EXTRA_TEXT, text)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        close()
    }

    private fun close() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
    }
}

/** «Во весь экран»: две стрелки в углы. Рисуем сами — символ «⤢» из запасного шрифта мелкий и съезжает с центра. */
@Composable
private fun ExpandIcon() {
    val color = LocalTextStyle.current.color
    Canvas(Modifier.size(14.dp)) {
        val w = 2.dp.toPx()
        val s = size.width
        val head = s * 0.38f
        fun arrow(tip: Offset, dx: Float, dy: Float) {
            drawLine(color, tip, Offset(tip.x - dx * head, tip.y), w, StrokeCap.Round)
            drawLine(color, tip, Offset(tip.x, tip.y - dy * head), w, StrokeCap.Round)
        }
        val a = w / 2
        drawLine(color, Offset(a, s - a), Offset(s - a, a), w, StrokeCap.Round)
        arrow(Offset(s - a, a), 1f, -1f)
        arrow(Offset(a, s - a), -1f, 1f)
    }
}

@Composable
private fun QuickAdd(onClose: () -> Unit, onSave: (String) -> Unit, onExpand: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val parsed = remember(text) { QuickParse.parse(text, today) }

    LaunchedEffect(Unit) {
        shown = true
        // Окно только появилось — фокус и клавиатура со второй попытки надёжнее.
        repeat(3) {
            runCatching { focus.requestFocus() }
            keyboard?.show()
            delay(120)
        }
    }

    fun submit() {
        if (text.isBlank()) {
            error = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch { for (x in listOf(14f, -12f, 9f, -6f, 3f, 0f)) shake.animateTo(x, tween(55)) }
        } else {
            keyboard?.hide()
            onSave(text.trim())
        }
    }

    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(shown, enter = fadeIn(tween(200))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Palette.Scrim)
                    .clickable(remember { MutableInteractionSource() }, null, onClick = onClose),
            )
        }
        AnimatedVisibility(
            shown,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(160)),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .offset { IntOffset(shake.value.roundToInt(), 0) }
                    .brutal(Palette.Paper, radius = 20.dp, shadow = 4.dp, border = 3.dp)
                    .clickable(remember { MutableInteractionSource() }, null) {} // тап по карточке не закрывает
                    .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Накинуть херню",
                        fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    HeaderButton(onClick = { onExpand(text.trim()) }, size = 32.dp) { ExpandIcon() }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it; error = false },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = Body, fontSize = 16.sp, color = Palette.Ink),
                        cursorBrush = SolidColor(Palette.Ink),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        decorationBox = { inner ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .brutal(
                                        if (error) Palette.ErrorBg else Palette.Card,
                                        radius = 12.dp, shadow = 0.dp,
                                        borderColor = if (error) Palette.Red else Palette.Ink,
                                    )
                                    .padding(horizontal = 12.dp, vertical = 11.dp),
                            ) {
                                if (text.isEmpty()) Text("Позвонить маме завтра в 19", fontSize = 16.sp, color = Palette.Faint)
                                inner()
                            }
                        },
                    )
                    BrutalButton(
                        onClick = ::submit,
                        bg = Palette.AccentBg,
                        radius = 12.dp,
                        shadow = 3.dp,
                        shadowColor = Palette.Chestnut,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 9.dp),
                    ) {
                        Text("+", color = Palette.AccentFg, fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                }
                AnimatedVisibility(
                    error || parsed.found,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    if (error) {
                        Text(Copy.emptyTitle, color = Palette.Red, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    } else {
                        Text("🧠 Понял: " + parsed.labels.joinToString(" · "), fontFamily = Mono, fontSize = 11.5.sp, color = Palette.Mute)
                    }
                }
            }
        }
    }
}
