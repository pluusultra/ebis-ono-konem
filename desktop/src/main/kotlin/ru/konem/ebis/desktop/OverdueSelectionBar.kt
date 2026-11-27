package ru.konem.ebis.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.konem.ebis.data.OverdueAction
import java.time.LocalDate

private enum class BatchPanel { Date, Tags }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OverdueSelectionBar(state: AppState, available: Set<Long>, today: LocalDate) {
    val selected = state.overdueSelection.orEmpty().intersect(available)
    val hasSelection = selected.isNotEmpty()
    var panel by remember(hasSelection) { mutableStateOf<BatchPanel?>(null) }
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().background(Palette.Card, shape)
            .border(1.5.dp, Palette.Hay, shape).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                AnimatedContent(
                    selected.size,
                    Modifier.align(Alignment.CenterVertically).padding(horizontal = 4.dp),
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        (fadeIn(tween(170)) + slideInVertically(tween(170)) { it * direction / 2 }) togetherWith
                            (fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it * direction / 2 })
                    },
                    label = "selectedCount",
                ) { count ->
                    Text("Выбрано $count из ${available.size}", color = Palette.Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                val allSelected = hasSelection && selected.size == available.size
                BatchButton(if (allSelected) "Снять все" else "Выбрать все", enabled = available.isNotEmpty()) {
                    state.toggleAllOverdue(available)
                }
                BatchButton("Перенести", enabled = hasSelection, accent = true) {
                    panel = if (panel == BatchPanel.Date) null else BatchPanel.Date
                }
                BatchButton("Выполнить", enabled = hasSelection) { state.batchOverdue(selected, OverdueAction.Complete, today) }
                if (state.tags.isNotEmpty()) {
                    BatchButton("Теги", enabled = hasSelection) { panel = if (panel == BatchPanel.Tags) null else BatchPanel.Tags }
                }
            }
            BatchButton("Отмена", onClick = state::endOverdueSelection)
        }
        AnimatedContent(
            panel,
            transitionSpec = {
                (fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 3 }) togetherWith fadeOut(tween(100))
            },
            label = "batchActions",
        ) { shown ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (shown) {
                    BatchPanel.Date -> BatchDatePicker(today) { if (hasSelection) state.moveOverdue(selected, it, today) }
                    BatchPanel.Tags -> BatchTagPicker(state) { action, tags ->
                        if (hasSelection) state.batchOverdue(selected, action, today, tags)
                    }
                    null -> Unit
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BatchDatePicker(today: LocalDate, onDate: (LocalDate) -> Unit) {
    var text by remember { mutableStateOf("") }
    val date = parseDate(text, today)?.takeUnless { it.isBefore(today) }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BatchButton("На сегодня", Modifier.align(Alignment.CenterVertically)) { onDate(today) }
        BatchButton("На завтра", Modifier.align(Alignment.CenterVertically)) { onDate(today.plusDays(1)) }
        BatchButton("Через неделю", Modifier.align(Alignment.CenterVertically)) { onDate(today.plusWeeks(1)) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(134.dp)) {
                Field(
                    text, { text = it }, "дд.мм.гггг", singleLine = true,
                    modifier = Modifier.semantics { contentDescription = "Дата переноса" },
                )
            }
            BatchButton("Применить", enabled = date != null, accent = true) { date?.let(onDate) }
        }
    }
    if (text.isNotBlank() && date == null) {
        Text("Укажи сегодняшнюю дату или позже", color = Palette.Mute, fontSize = 13.sp)
    }
}

@Composable
private fun BatchTagPicker(state: AppState, apply: (OverdueAction, Set<Long>) -> Unit) {
    var picked by remember { mutableStateOf(emptySet<Long>()) }
    val tags = state.tags
    val valid = picked.intersect(tags.map { it.id }.toSet())
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tags.forEach { tag ->
            TagChip(tag, tag.id in valid, small = true) {
                picked = if (tag.id in picked) picked - tag.id else picked + tag.id
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BatchButton("Добавить теги", enabled = valid.isNotEmpty(), accent = true) { apply(OverdueAction.AddTags, valid) }
        BatchButton("Снять теги", enabled = valid.isNotEmpty()) { apply(OverdueAction.RemoveTags, valid) }
    }
}

@Composable
private fun BatchButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val activeAccent = enabled && accent
    val background by animateColorAsState(if (activeAccent) Palette.Hay else Palette.Paper, tween(160), label = "batchButton")
    Text(
        label,
        modifier.background(background, shape)
            .border(1.dp, Palette.Ink.copy(alpha = if (enabled) 0.45f else 0.15f), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (enabled) Modifier.hand() else Modifier)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        color = when {
            activeAccent -> Palette.OnHay
            enabled -> Palette.Ink
            else -> Palette.Mute2
        },
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}
