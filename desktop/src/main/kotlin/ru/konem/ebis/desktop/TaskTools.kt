package ru.konem.ebis.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.konem.ebis.data.ChecklistItem
import ru.konem.ebis.data.Tag
import ru.konem.ebis.ui.QuickParse

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickTagSuggestions(input: String, tags: List<Tag>, onChange: (String) -> Unit) {
    val suggestions = remember(input, tags) { QuickParse.tagSuggestions(input, tags) }
    if (suggestions.isEmpty()) return
    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        suggestions.forEach { tag ->
            SelectChip("#" + tag.name, false) { onChange(QuickParse.completeTag(input, tag)) }
        }
    }
}

@Composable
internal fun ChecklistEditor(items: List<ChecklistItem>, onChange: (List<ChecklistItem>) -> Unit) {
    var draft by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val label = if (items.isEmpty()) "+ Чек-лист" else "Чек-лист (" + items.count { it.done } + "/" + items.size + ")"
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GhostChip(label, selected = expanded) { expanded = !expanded }
        if (!expanded) return@Column
        items.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = item.done,
                    onCheckedChange = { checked ->
                        onChange(items.toMutableList().apply { this[index] = item.copy(done = checked) })
                    },
                    modifier = Modifier.semantics { contentDescription = item.text },
                )
                Text(item.text, Modifier.weight(1f), color = Palette.Ink)
                GhostChip("Убрать", small = true) { onChange(items.filterIndexed { i, _ -> i != index }) }
            }
        }
        Field(draft, { draft = it }, "Новый пункт", singleLine = true)
        if (draft.isNotBlank()) {
            GhostChip("+ Добавить пункт", small = true) {
                onChange(items + ChecklistItem(draft.trim()))
                draft = ""
            }
        }
    }
}

@Composable
internal fun BoxScope.TrashOverlay(state: AppState) {
    if (!state.trashOpen) return
    val close = { state.trashOpen = false }
    Overlay(visible = true, onDismiss = close) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth()
                .brutal(Palette.Paper, radius = 24.dp, shadow = 7.dp, border = 3.dp).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Корзина", Modifier.weight(1f), fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                CloseButton(close)
            }
            TrashPanel(state)
        }
    }
}

@Composable
private fun TrashPanel(state: AppState) {
    val tasks = state.data.recoverableTasks
    val tags = state.data.recoverableTags
    Text(if (tasks.isEmpty() && tags.isEmpty()) "Корзина пуста" else "Задачи и теги можно вернуть в течение 60 дней.", color = Palette.Mute)
    LazyColumn(Modifier.heightIn(max = 360.dp)) {
        items(tasks, key = { "task" + it.id }) { task -> TrashRow(task.title) { state.restoreTask(task.id) } }
        items(tags, key = { "tag" + it.id }) { tag -> TrashRow("#" + tag.name) { state.restoreTag(tag.id) } }
    }
}

@Composable
private fun TrashRow(title: String, restore: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = Palette.Ink)
        GhostChip("Вернуть", small = true, onClick = restore)
    }
}
