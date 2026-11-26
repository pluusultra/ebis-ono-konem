package ru.konem.ebis.desktop

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.konem.ebis.data.Repeat
import ru.konem.ebis.data.Task
import ru.konem.ebis.ui.Copy
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Затемнение и карточка по центру окна: появляется пружиной и так же уезжает при закрытии.
 * Клик мимо закрывает ([onDismiss] = null — нельзя закрыть).
 */
@Composable
fun BoxScope.Overlay(visible: Boolean, onDismiss: (() -> Unit)?, keepAlive: Boolean = false, content: @Composable () -> Unit) {
    val p = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible) {
        if (visible) p.animateTo(1f, spring(dampingRatio = 0.68f, stiffness = 520f))
        else p.animateTo(0f, tween(170, easing = FastOutLinearInEasing))
    }
    val moving by remember { derivedStateOf { p.value > 0f } }
    val shown = visible || moving
    // keepAlive: содержимое собрано заранее и просто не выставляется — первый кадр открытия не тратится на сборку.
    if (!shown && !keepAlive) return
    Box(Modifier.matchParentSize().placeIf(shown)) {
        // Затемнение рисуется цветом с прозрачностью, без отдельного слоя на всё окно.
        Box(
            Modifier.matchParentSize()
                .drawBehind { drawRect(Palette.Scrim, alpha = p.value.coerceIn(0f, 1f)) }
                .then(if (visible) Modifier.clickable(remember { MutableInteractionSource() }, null) { onDismiss?.invoke() } else Modifier),
        )
        Box(
            Modifier.align(Alignment.Center)
                .graphicsLayer {
                    val v = p.value
                    val sc = 0.92f + 0.08f * v
                    scaleX = sc; scaleY = sc
                    translationY = (1f - v) * 16.dp.toPx()
                    // Прозрачность только в начале и в конце: полупрозрачный слой дороже непрозрачного.
                    alpha = (v * 3f).coerceIn(0f, 1f)
                }
                .padding(20.dp)
                .clickable(remember { MutableInteractionSource() }, null) {},
        ) { content() }
    }
}

/** Выставить на место или спрятать, не выкидывая из композиции. */
private fun Modifier.placeIf(show: Boolean) = layout { m, c ->
    val pl = m.measure(c)
    layout(pl.width, pl.height) { if (show) pl.place(0, 0) }
}

private val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** «17.10», «17.10.2026», «17.10.26» → дата; ерунда → null. */
private fun parseDate(s: String, today: LocalDate): LocalDate? {
    val parts = s.trim().split('.', '/', '-').filter { it.isNotBlank() }
    val d = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
    val y = parts.getOrNull(2)?.toIntOrNull()?.let { if (it < 100) 2000 + it else it } ?: today.year
    return runCatching { LocalDate.of(y, m, d) }.getOrNull()
}

/** «15:00», «9.30», «930» → минуты от полуночи; пусто → null без ошибки. */
fun parseTime(s: String): Result<Int?> {
    val t = s.trim()
    if (t.isEmpty()) return Result.success(null)
    val digits = t.split(':', '.', ' ').filter { it.isNotBlank() }
    val (h, m) = when {
        digits.size >= 2 -> digits[0].toIntOrNull() to digits[1].toIntOrNull()
        t.length >= 3 -> t.dropLast(2).toIntOrNull() to t.takeLast(2).toIntOrNull()
        else -> t.toIntOrNull() to 0
    }
    if (h == null || m == null || h !in 0..23 || m !in 0..59) return Result.failure(IllegalArgumentException())
    return Result.success(h * 60 + m)
}

/** Окно правки. Пока уезжает — показывает последнюю задачу, а не пустоту. */
@Composable
fun BoxScope.EditOverlay(state: AppState, today: LocalDate) {
    var last by remember { mutableStateOf<Task?>(null) }
    state.editing?.let { last = it }
    Overlay(state.editing != null, { state.editing = null }) {
        last?.let { EditDialog(state, it, today) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditDialog(state: AppState, task: Task, today: LocalDate) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    var desc by remember(task.id) { mutableStateOf(task.desc) }
    var due by remember(task.id) { mutableStateOf(task.due) }
    var dateText by remember(task.id) { mutableStateOf(task.due.format(dateFmt)) }
    var timeText by remember(task.id) { mutableStateOf(task.time?.let { Copy.fmtTime(it) } ?: "") }
    var repeat by remember(task.id) { mutableStateOf(task.repeat) }
    var repeatDays by remember(task.id) { mutableStateOf(task.repeatDays) }
    var hot by remember(task.id) { mutableStateOf(task.hot) }
    // Новая задача при включённом фильтре сразу получает выбранные теги — иначе она пропала бы из ленты.
    var tagIds by remember(task.id) { mutableStateOf(if (task.id == DRAFT_ID) state.tagFilter else task.tagIds) }
    var checklist by remember(task.id) { mutableStateOf(task.checklist) }
    var problem by remember(task.id) { mutableStateOf<String?>(null) }
    val isNew = task.id == DRAFT_ID
    val close = { state.editing = null }
    val focus = remember { FocusRequester() }
    LaunchedEffect(task.id) { runCatching { focus.requestFocus() } }

    fun save() {
        if (state.editing == null) return
        val time = parseTime(timeText).getOrElse { problem = "Время не понял. Пиши как 15:00"; return }
        val d = parseDate(dateText, today) ?: run { problem = "Дату не понял. Пиши как 17.10.2026"; return }
        val days = if (repeat == Repeat.SelectedDays) repeatDays else emptySet()
        if (repeat == Repeat.SelectedDays && days.isEmpty()) return
        if (isNew) {
            if (title.isBlank()) { problem = "Без названия не накину"; return }
            val tags = tagIds.filterTo(mutableSetOf()) { id -> state.tags.any { it.id == id } }
            state.add(title, d, time, repeat, hot, desc, days, tags, checklist)
        } else {
            // Незнакомые id (тег удалили на другом устройстве) не трогаем — пусть живут, как жили.
            state.edit(task.id, title, desc, d, time, repeat, hot, days, tagIds, checklist, task)
        }
        close()
    }

    // Ctrl+Enter ловит окно (сочетание переназначается), а сюда приходит сигнал.
    var seen by remember(task.id) { mutableStateOf(state.saveRequests) }
    LaunchedEffect(state.saveRequests) {
        if (state.saveRequests != seen) { seen = state.saveRequests; save() }
    }

    fun setDue(d: LocalDate) {
        due = d
        dateText = d.format(dateFmt)
        problem = null
    }

    Column(
        Modifier
            .widthIn(max = 620.dp)
            .fillMaxWidth()
            .brutal(Palette.Paper, radius = 24.dp, shadow = 7.dp, border = 3.dp)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (isNew) "Накинуть херню" else "Переписать херню", fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.weight(1f))
            CloseButton(close)
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Field(title, { title = it; problem = null }, "Что надо сделать", singleLine = true, big = true, modifier = Modifier.focusRequester(focus))
            Field(desc, { desc = it }, "Суть, детали, оправдания", singleLine = false)

            Label("Когда")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Сегодня" to today, "Завтра" to today.plusDays(1), "Послезавтра" to today.plusDays(2), "Через неделю" to today.plusWeeks(1))
                    .forEach { (label, d) -> SelectChip(label, due == d) { setDue(d) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(170.dp)) {
                    Field(dateText, {
                        dateText = it
                        parseDate(it, today)?.let { d -> due = d }
                        problem = null
                    }, "дд.мм.гггг", singleLine = true)
                }
                Box(Modifier.width(120.dp)) { Field(timeText, { timeText = it; problem = null }, "время", singleLine = true) }
                Text(Copy.dayLabel(due, today), fontSize = 14.sp, color = Palette.Mute)
            }

            Label("Повтор")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Repeat.entries.forEach { r -> SelectChip(r.label, repeat == r) { repeat = r } }
            }
            if (repeat == Repeat.SelectedDays) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayOfWeek.values().forEach { day ->
                        SelectChip(Copy.shortWeekday(day), day in repeatDays) {
                            repeatDays = if (day in repeatDays) repeatDays - day else repeatDays + day
                        }
                    }
                }
                if (repeatDays.isEmpty()) Label("Выбери хотя бы один день")
            }

            Label("Теги")
            TagPicker(state, tagIds) { tagIds = it }
            ChecklistEditor(checklist) { checklist = it }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BrutalSwitch(hot) { hot = it }
                Text("🔥 Горит — всегда наверху", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            // Горит + время = будильник: показываем сразу, чтобы не было сюрпризом.
            val alarmAt = parseTime(timeText).getOrNull()
            androidx.compose.animation.AnimatedVisibility(hot && alarmAt != null && state.notif.alarmOn) {
                Column(Modifier.fillMaxWidth().brutal(Palette.Red, radius = 13.dp, shadow = 0.dp).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("🔊 В ${alarmAt?.let { Copy.fmtTime(it) }} заорёт будильник", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Пока не ответишь. Мелодия — в настройках", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                }
            }

            problem?.let { Text(it, color = Palette.Red, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }

        FlowRow(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!isNew) CardAction("нахер", { trash(it, 1.8.dp.toPx()) }, danger = true) { state.delete(task.id); close() }
            Spacer(Modifier.weight(1f))
            BrutalButton(close, bg = Palette.Card) { Text("Отмена", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            BrutalButton(::save, bg = Palette.Red, shadowColor = Palette.Ink) {
                Text(if (isNew) "Накинуть" else "Сохранить", color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Keycaps(state.keys[Action.Save], small = true, onDark = true)
            }
        }
    }
}

@Composable
fun Label(text: String) {
    Text(text.uppercase(), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.4.sp, color = Palette.Mute2)
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, placeholder: String, singleLine: Boolean, big: Boolean = false, modifier: Modifier = Modifier, mono: Boolean = false) {
    val size = if (big) 18.sp else 16.sp
    Box(
        Modifier.fillMaxWidth().brutal(Palette.Card, radius = 13.dp, shadow = 0.dp, border = 2.5.dp)
            .padding(horizontal = 14.dp, vertical = if (singleLine) 11.dp else 12.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, color = Palette.Faint, fontSize = size, fontFamily = if (mono) Mono else Body)
        BasicTextField(
            value, onChange,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            maxLines = if (singleLine) 1 else 6,
            textStyle = TextStyle(fontFamily = if (mono) Mono else Body, fontSize = size, color = Palette.Ink, fontWeight = if (big || mono) FontWeight.Bold else FontWeight.Normal),
            cursorBrush = SolidColor(Palette.Ink),
            modifier = modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun BoxScope.Onboarding(state: AppState, pickFolder: () -> File?) {
    Overlay(state.folder == null, null) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().brutal(Palette.Hay, radius = 24.dp, shadow = 7.dp, border = 3.dp, borderColor = Palette.OnHay, shadowColor = Palette.OnHay).padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Ну здарова, бедолага 🐴", fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 24.sp, color = Palette.OnHay)
            Text(
                "Где лежит ${DesktopStore.FILE_NAME}? Выбери папку, которую синхронизируешь с телефоном — " +
                    "в телефоне её же выбирают в «Настройки → Синхронизация с ПК». Если файл там уже есть, задачи подтянутся.",
                fontSize = 15.5.sp, color = Palette.HeaderSub,
            )
            BrutalButton(
                { pickFolder()?.let { state.connect(it) } },
                bg = Palette.OnHay, shadowColor = Palette.Red, borderColor = Palette.OnHay,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Text("📁 Выбрать папку синхронизации", color = Palette.Hay, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Text(
                "Пока хранить на этом компьютере →",
                fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = Palette.OnHay,
                modifier = Modifier.clickable { state.connect(DesktopStore.defaultFolder()) }.hand().padding(vertical = 4.dp),
            )
        }
    }
}
