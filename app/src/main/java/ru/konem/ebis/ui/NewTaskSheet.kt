@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ru.konem.ebis.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.konem.ebis.data.Repeat
import ru.konem.ebis.data.Task
import ru.konem.ebis.ui.theme.Body
import ru.konem.ebis.ui.theme.Display
import ru.konem.ebis.ui.theme.Mono
import ru.konem.ebis.ui.theme.Palette
import java.time.LocalDate
import kotlin.math.roundToInt

private enum class When { Today, Tomorrow, Pick }

private const val DAY_MS = 86_400_000L

/** Что вернула форма. id == null — новая задача. */
data class Draft(
    val id: Long?,
    val title: String,
    val desc: String,
    val due: LocalDate,
    val time: Int?,
    val repeat: Repeat,
    val hot: Boolean,
)

/**
 * Шторка «Новая херня» / «Правим херню». Выезжает на пружине, смахивается вниз за шапку,
 * поддерживает системный жест «назад» с предпросмотром.
 */
@Composable
fun NewTaskSheet(
    visible: Boolean,
    editing: Task?,
    prefillTitle: String,
    prefillDesc: String,
    backEnabled: Boolean,
    onClose: () -> Unit,
    onNever: () -> Unit,
    onSave: (Draft) -> Unit,
) {
    AnimatedVisibility(visible, enter = fadeIn(tween(250)), exit = fadeOut(tween(250))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Palette.Scrim)
                .clickable(remember { MutableInteractionSource() }, null, onClick = onClose),
        )
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(Motion.screenOffset) { it },
        exit = slideOutVertically(tween(260)) { it },
    ) {
        // Содержимое пересоздаётся при каждом открытии, так что форма всегда свежая.
        SheetContent(editing, prefillTitle, prefillDesc, backEnabled && visible, onClose, onNever, onSave)
    }
}

@Composable
private fun SheetContent(
    editing: Task?,
    prefillTitle: String,
    prefillDesc: String,
    backEnabled: Boolean,
    onClose: () -> Unit,
    onNever: () -> Unit,
    onSave: (Draft) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val initialWhen = when {
        editing == null -> When.Today
        editing.due == today -> When.Today
        editing.due == today.plusDays(1) -> When.Tomorrow
        else -> When.Pick
    }
    var title by rememberSaveable { mutableStateOf(editing?.title ?: prefillTitle) }
    var desc by rememberSaveable { mutableStateOf(editing?.desc ?: prefillDesc) }
    var whenSel by rememberSaveable { mutableStateOf(initialWhen) }
    var picked by rememberSaveable { mutableStateOf(editing?.due?.takeIf { initialWhen == When.Pick }?.toEpochDay()) }
    var time by rememberSaveable { mutableStateOf(editing?.time) }
    var repeat by rememberSaveable { mutableStateOf(editing?.repeat ?: Repeat.None) }
    var hot by rememberSaveable { mutableStateOf(editing?.hot ?: false) }
    var error by rememberSaveable { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current

    // Шторку можно тянуть вниз за шапку и закрывать жестом «назад» — оба двигают одно смещение.
    val drag = remember { Animatable(0f) }
    val backScale = remember { Animatable(1f) }
    val dragState = rememberDraggableState { d -> scope.launch { drag.snapTo((drag.value + d).coerceAtLeast(0f)) } }

    PredictiveBackHandler(enabled = backEnabled) { progress ->
        try {
            progress.collect { e -> backScale.snapTo(1f - 0.08f * e.progress) }
            onClose()
        } catch (e: CancellationException) {
            backScale.animateTo(1f, Motion.smooth())
        }
    }

    // Разбор «завтра в 15:00 каждый день» из названия. Подставляем, как только нашли.
    val parsed = remember(title) { QuickParse.parse(title, today) }
    LaunchedEffect(parsed.date) {
        parsed.date?.let { d ->
            when (d) {
                today -> whenSel = When.Today
                today.plusDays(1) -> whenSel = When.Tomorrow
                else -> { picked = d.toEpochDay(); whenSel = When.Pick }
            }
        }
    }
    LaunchedEffect(parsed.time) { parsed.time?.let { time = it } }
    LaunchedEffect(parsed.repeat) { parsed.repeat?.let { repeat = it } }
    LaunchedEffect(parsed.hot) { if (parsed.hot) hot = true }

    LaunchedEffect(Unit) {
        if (editing == null) {
            delay(380)
            runCatching { focus.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 18.dp)) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = drag.value
                    scaleX = backScale.value
                    scaleY = backScale.value
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                }
                .brutal(Palette.Paper, radius = 22.dp, shadow = 0.dp, border = 3.dp)
                .clickable(remember { MutableInteractionSource() }, null) {} // не пропускаем тапы под форму
                .imePadding(),
        ) {
            HayBar(
                modifier = Modifier.draggable(
                    dragState,
                    Orientation.Vertical,
                    onDragStopped = { v ->
                        if (drag.value > 160f || v > 1800f) onClose()
                        else drag.animateTo(0f, Motion.snappy())
                    },
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            ) {
                // Ручка: намекает, что шторку можно смахнуть.
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(42.dp)
                        .height(5.dp)
                        .background(Palette.OnHay.copy(alpha = 0.35f), RoundedCornerShape(50)),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (editing == null) "Новая херня" else "Правим херню",
                        fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 19.sp, modifier = Modifier.weight(1f),
                    )
                    HeaderButton(onClick = onClose, size = 34.dp) { Text("✕", fontSize = 16.sp) }
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    Modifier.offset { IntOffset(shake.value.roundToInt(), 0) },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Label("Чё надо сделать")
                    Field(
                        value = title,
                        onChange = { title = it; error = false },
                        placeholder = "Позвонить маме завтра в 19:00",
                        error = error,
                        singleLine = true,
                        modifier = Modifier.focusRequester(focus),
                    )
                    AnimatedVisibility(error, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Text(Copy.emptyTitle, color = Palette.Red, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                    AnimatedVisibility(
                        parsed.found,
                        enter = fadeIn() + expandVertically(Motion.smooth()),
                        exit = fadeOut() + shrinkVertically(Motion.smooth()),
                    ) {
                        Text(
                            "🧠 Понял: " + parsed.labels.joinToString(" · "),
                            fontFamily = Mono, fontSize = 11.5.sp, color = Palette.Mute,
                            modifier = Modifier.animateContentSize(Motion.smooth()),
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 13.5.sp)) { append("Суть") }
                        withStyle(SpanStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Palette.Mute2)) {
                            append(" · если она вообще есть")
                        }
                    })
                    Field(
                        value = desc,
                        onChange = { desc = it },
                        placeholder = "Зачем, почему и кто виноват",
                        singleLine = false,
                        minHeight = 80.dp,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label("Когда")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectChip("Сегодня", whenSel == When.Today) { whenSel = When.Today }
                        SelectChip("Завтра", whenSel == When.Tomorrow) { whenSel = When.Tomorrow }
                        val pickedDate = picked?.let { LocalDate.ofEpochDay(it) }
                        SelectChip(
                            if (whenSel == When.Pick && pickedDate != null) "📅 ${Copy.shortDate(pickedDate)}" else "📅 Своя дата",
                            whenSel == When.Pick,
                        ) { showPicker = true }
                        SelectChip("Никогда", selected = false, dashed = true, onClick = onNever)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label("Напомнить")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectChip(time?.let { "⏰ " + Copy.fmtTime(it) } ?: "⏰ Во сколько?", time != null) { showTime = true }
                        if (time != null) SelectChip("Без времени", false) { time = null }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label("Повтор")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Repeat.entries.forEach { r ->
                            SelectChip(if (r == Repeat.None) "Разово" else r.label, repeat == r) { repeat = r }
                        }
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .brutal(if (hot) Palette.ErrorBg else Palette.Card, radius = 14.dp, shadow = 0.dp, borderColor = if (hot) Palette.Red else Palette.Ink)
                        .clickable(remember { MutableInteractionSource() }, null) {
                            hot = !hot
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("🔥", fontSize = 20.sp)
                    Column(Modifier.weight(1f)) {
                        Text("Горит", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Всегда наверху списка", fontSize = 12.sp, color = Palette.Mute)
                    }
                    BrutalSwitch(hot) { hot = it }
                }

                // Горит + время = будильник: показываем сразу, чтобы не было сюрпризом.
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val alarmPrefs = remember { ru.konem.ebis.notify.Prefs(ctx) }
                val alarmTime = time
                androidx.compose.animation.AnimatedVisibility(hot && alarmTime != null && alarmPrefs.alarmOn) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .brutal(Palette.Red, radius = 14.dp, shadow = 0.dp)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text("🔊 В ${alarmTime?.let { Copy.fmtTime(it) }} заорёт будильник", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Пока не ответишь. Мелодия — в настройках", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                    }
                }

                BrutalButton(
                    onClick = {
                        val t = (if (parsed.found) parsed.title else title).trim()
                        if (t.isEmpty()) {
                            error = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                for (x in listOf(14f, -12f, 9f, -6f, 3f, 0f)) shake.animateTo(x, tween(55))
                            }
                        } else {
                            val due = when (whenSel) {
                                When.Today -> today
                                When.Tomorrow -> today.plusDays(1)
                                When.Pick -> picked?.let { LocalDate.ofEpochDay(it) } ?: today
                            }
                            onSave(Draft(editing?.id, t, desc.trim(), due, time, repeat, hot))
                        }
                    },
                    bg = Palette.AccentBg,
                    radius = 16.dp,
                    shadow = 4.dp,
                    shadowColor = Palette.Chestnut,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (editing == null) "Повесить на себя" else "Сохранить",
                        color = Palette.AccentFg, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    )
                }
            }
        }
    }

    if (showTime) {
        TimeDialog(
            title = "Во сколько орать?",
            initial = time ?: (java.time.LocalTime.now().hour + 1).coerceAtMost(23) * 60,
            onDismiss = { showTime = false },
            onPick = { time = it; showTime = false },
        )
    }

    if (showPicker) {
        val todayMs = today.toEpochDay() * DAY_MS
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (picked ?: today.plusDays(2).toEpochDay()) * DAY_MS,
            selectableDates = remember {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayMs
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        picked = Math.floorDiv(it, DAY_MS)
                        whenSel = When.Pick
                    }
                    showPicker = false
                }) { Text("Вот эта", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Отмена") }
            },
        ) {
            DatePicker(state = state, showModeToggle = false)
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
    error: Boolean = false,
    minHeight: Dp = 0.dp,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        textStyle = TextStyle(fontFamily = Body, fontSize = 15.sp, color = Palette.Ink),
        cursorBrush = SolidColor(Palette.Ink),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .brutal(
                        if (error) Palette.ErrorBg else Palette.Card,
                        radius = 12.dp, shadow = 0.dp,
                        borderColor = if (error) Palette.Red else Palette.Ink,
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = Palette.Faint)
                inner()
            }
        },
    )
}
