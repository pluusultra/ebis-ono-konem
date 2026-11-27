@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ru.konem.ebis.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.konem.ebis.data.OverdueAction
import ru.konem.ebis.data.Tag
import ru.konem.ebis.data.Task
import ru.konem.ebis.ui.theme.Body
import ru.konem.ebis.ui.theme.Palette
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

internal enum class TaskListOrder(val label: String, val shortLabel: String) {
    Due("По сроку", "Срок"),
    Newest("Сначала новые", "Новые"),
    Oldest("Сначала старые", "Старые");

    fun sortMatches(matches: List<Task>) = when (this) {
        Due -> matches
        Newest -> matches.sortedByDescending { it.id }
        Oldest -> matches.sortedBy { it.id }
    }
}

@Composable
internal fun TaskListControls(
    query: String,
    onQuery: (String) -> Unit,
    onFocus: (Boolean) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    orders: List<TaskListOrder>,
    order: TaskListOrder,
    onOrder: (TaskListOrder) -> Unit,
    enabled: Boolean,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val outline by animateColorAsState(
        if (focused) Palette.Hay else Palette.Ink.copy(alpha = if (query.isNotEmpty()) 0.7f else 0.32f),
        tween(160), label = "searchOutline",
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(color = Palette.Ink, fontFamily = Body, fontSize = 15.sp),
            cursorBrush = SolidColor(Palette.Ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.weight(1f).onFocusChanged {
                focused = it.isFocused
                onFocus(focused)
            }.onPreviewKeyEvent {
                if (it.key == Key.Escape && it.type == KeyEventType.KeyDown) { onClear(); true } else false
            }.semantics { contentDescription = "Поиск по задачам" },
            decorationBox = { field ->
                Row(
                    Modifier.fillMaxWidth().height(46.dp).background(Palette.Card, shape).border(1.5.dp, outline, shape)
                        .padding(start = 10.dp, end = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val color = if (focused) Palette.Hay else Palette.Mute
                    Canvas(Modifier.size(16.dp)) {
                        val center = Offset(size.width * 0.40f, size.height * 0.40f)
                        drawCircle(color, size.width * 0.31f, center, style = Stroke(1.6.dp.toPx()))
                        drawLine(color, center * 1.5f, Offset(size.width * 0.9f, size.height * 0.9f), 1.6.dp.toPx(), StrokeCap.Round)
                    }
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Найти задачу или #тег", color = Palette.Mute, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        field()
                    }
                    if (query.isNotEmpty() || focused) {
                        Box(
                            Modifier.size(38.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClear)
                                .semantics { contentDescription = "Закрыть поиск" },
                            contentAlignment = Alignment.Center,
                        ) { Text("×", fontSize = 23.sp, color = Palette.Ink) }
                    }
                }
            },
        )
        if (orders.isNotEmpty()) SortMenu(order, orders, enabled, onOrder)
    }
}

@Composable
private fun SortMenu(order: TaskListOrder, orders: List<TaskListOrder>, enabled: Boolean, onOrder: (TaskListOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val expanded = enabled && open
    val shape = RoundedCornerShape(12.dp)
    val bg by animateColorAsState(if (expanded) lerp(Palette.Card, Palette.Hay, 0.14f) else Palette.Card, label = "sortFill")
    Box {
        Row(
            Modifier.height(46.dp).background(bg, shape)
                .border(1.5.dp, if (expanded) Palette.Hay else Palette.Ink.copy(alpha = 0.32f), shape)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Порядок сортировки") { open = true }
                .padding(horizontal = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val ink = Palette.Ink
            Canvas(Modifier.size(13.dp)) {
                drawLine(ink, Offset(0f, size.height * 0.2f), Offset(size.width, size.height * 0.2f), 1.7.dp.toPx(), StrokeCap.Round)
                drawLine(ink, Offset(0f, size.height * 0.5f), Offset(size.width * 0.72f, size.height * 0.5f), 1.7.dp.toPx(), StrokeCap.Round)
                drawLine(ink, Offset(0f, size.height * 0.8f), Offset(size.width * 0.43f, size.height * 0.8f), 1.7.dp.toPx(), StrokeCap.Round)
            }
            Text(order.shortLabel, color = ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(if (expanded) "⌃" else "⌄", color = ink, fontSize = 13.sp)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { open = false },
            modifier = Modifier.width(172.dp),
            offset = DpOffset(0.dp, 5.dp),
            shape = RoundedCornerShape(12.dp),
            containerColor = Palette.Card,
            tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Palette.Ink.copy(alpha = 0.64f)),
        ) {
            orders.forEach { option ->
                val current = order == option
                val rowShape = RoundedCornerShape(8.dp)
                DropdownMenuItem(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp).height(40.dp).clip(rowShape)
                        .background(if (current) lerp(Palette.Card, Palette.Hay, 0.20f) else Palette.Card)
                        .semantics { selected = current },
                    text = { Text(option.label, color = Palette.Ink, fontSize = 13.sp, fontWeight = if (current) FontWeight.Bold else FontWeight.Medium) },
                    trailingIcon = if (current) ({ Text("✓", color = Palette.Ink, fontWeight = FontWeight.Bold) }) else null,
                    contentPadding = PaddingValues(horizontal = 9.dp),
                    onClick = { open = false; onOrder(option) },
                )
            }
        }
    }
}

private enum class SelectionPanel { Date, Tags }
private val batchDateFormat = DateTimeFormatter.ofPattern("d.M.uuuu").withResolverStyle(ResolverStyle.STRICT)

@Composable
internal fun OverdueSelectionBar(
    selected: Set<Long>,
    available: Set<Long>,
    tags: List<Tag>,
    today: LocalDate,
    onCancel: () -> Unit,
    onToggleAll: () -> Unit,
    onMove: (LocalDate) -> Unit,
    onApply: (OverdueAction, Set<Long>) -> Unit,
) {
    val hasSelection = selected.isNotEmpty()
    var panel by remember(hasSelection) { mutableStateOf<SelectionPanel?>(null) }
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().background(Palette.Card, shape).border(1.5.dp, Palette.Hay, shape).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AnimatedContent(
                selected.size, Modifier.weight(1f),
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(170)) + slideInVertically(tween(170)) { it * direction / 2 }) togetherWith
                        (fadeOut(tween(110)) + slideOutVertically(tween(110)) { -it * direction / 2 })
                },
                label = "selectedCount",
            ) { count ->
                Text("Выбрано $count из ${available.size}", color = Palette.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            BatchButton(if (hasSelection && selected == available) "Снять все" else "Выбрать все", available.isNotEmpty(), onClick = onToggleAll)
            Box(
                Modifier.size(40.dp).clickable(role = Role.Button, onClick = onCancel)
                    .semantics { contentDescription = "Выйти из выделения" },
                contentAlignment = Alignment.Center,
            ) { Text("×", color = Palette.Ink, fontSize = 23.sp) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            BatchButton("Перенести", hasSelection, accent = true) { panel = if (panel == SelectionPanel.Date) null else SelectionPanel.Date }
            BatchButton("Выполнить", hasSelection) { onApply(OverdueAction.Complete, emptySet()) }
            if (tags.isNotEmpty()) BatchButton("Теги", hasSelection) { panel = if (panel == SelectionPanel.Tags) null else SelectionPanel.Tags }
        }
        AnimatedContent(
            panel,
            transitionSpec = { (fadeIn(tween(180)) + slideInVertically { -it / 3 }) togetherWith fadeOut(tween(100)) },
            label = "batchPanel",
        ) { active ->
            when (active) {
                SelectionPanel.Date -> BatchDate(today, onMove)
                SelectionPanel.Tags -> BatchTags(tags, onApply)
                null -> Unit
            }
        }
    }
}

@Composable
private fun BatchDate(today: LocalDate, onMove: (LocalDate) -> Unit) {
    var input by remember { mutableStateOf("") }
    val date = remember(input, today) {
        runCatching { LocalDate.parse(input.trim(), batchDateFormat) }.getOrNull()?.takeUnless { it.isBefore(today) }
    }
    val invalid = input.trim().substringAfterLast('.', "").length >= 4 && date == null
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BatchButton("На сегодня") { onMove(today) }
            BatchButton("На завтра") { onMove(today.plusDays(1)) }
            BatchButton("Через неделю") { onMove(today.plusWeeks(1)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Field(input, { input = it }, "дд.мм.гггг", singleLine = true, modifier = Modifier.weight(1f), error = invalid)
            BatchButton("Применить", date != null, accent = true) { date?.let(onMove) }
        }
        if (invalid) Text("Укажи сегодняшнюю дату или позже", color = Palette.Mute, fontSize = 12.sp)
    }
}

@Composable
private fun BatchTags(tags: List<Tag>, onApply: (OverdueAction, Set<Long>) -> Unit) {
    var chosen by remember { mutableStateOf(emptySet<Long>()) }
    val valid = chosen.intersect(tags.map { it.id }.toSet())
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tags.forEach { tag ->
                TagChip(tag, tag.id in valid, small = true, alwaysColored = true) {
                    chosen = if (tag.id in chosen) chosen - tag.id else chosen + tag.id
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BatchButton("Добавить теги", valid.isNotEmpty(), accent = true) { onApply(OverdueAction.AddTags, valid) }
            BatchButton("Снять теги", valid.isNotEmpty()) { onApply(OverdueAction.RemoveTags, valid) }
        }
    }
}

@Composable
private fun BatchButton(label: String, enabled: Boolean = true, accent: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val highlighted = enabled && accent
    val bg by animateColorAsState(if (highlighted) Palette.Hay else Palette.Paper, tween(160), label = "batchButton")
    Box(
        Modifier.height(42.dp).background(bg, shape)
            .border(1.dp, Palette.Ink.copy(alpha = if (enabled) 0.48f else 0.18f), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontSize = 12.sp, maxLines = 1, fontWeight = FontWeight.Bold,
            color = if (highlighted) Palette.OnHay else if (enabled) Palette.Ink else Palette.Mute,
        )
    }
}
