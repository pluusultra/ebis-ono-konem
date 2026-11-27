package ru.konem.ebis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.konem.ebis.data.ChecklistItem
import ru.konem.ebis.data.Tag
import ru.konem.ebis.data.TaskRepo
import ru.konem.ebis.ui.theme.Display
import ru.konem.ebis.ui.theme.Palette

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickTagSuggestions(input: String, tags: List<Tag>, onChange: (String) -> Unit) {
    val suggestions = remember(input, tags) { QuickParse.tagSuggestions(input, tags) }
    if (suggestions.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        suggestions.forEach { tag ->
            SelectChip("#" + tag.name, selected = false) { onChange(QuickParse.completeTag(input, tag)) }
        }
    }
}

@Composable
internal fun ChecklistEditor(items: List<ChecklistItem>, onChange: (List<ChecklistItem>) -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val label = if (items.isEmpty()) "+ Чек-лист" else "Чек-лист (" + items.count { it.done } + "/" + items.size + ")"
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SelectChip(label, expanded) { expanded = !expanded }
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
                TextButton(onClick = { onChange(items.filterIndexed { i, _ -> i != index }) }) { Text("Убрать") }
            }
        }
        Field(draft, { draft = it }, "Новый пункт", singleLine = true)
        if (draft.isNotBlank()) {
            SelectChip("+ Добавить пункт", selected = false) {
                onChange(items + ChecklistItem(draft.trim()))
                draft = ""
            }
        }
    }
}

@Composable
internal fun TrashDialog(onClose: () -> Unit, onMessage: (String) -> Unit) {
    val tasks = TaskRepo.data.recoverableTasks
    val tags = TaskRepo.data.recoverableTags
    ToolsDialog("Корзина", onClose) {
        Text(if (tasks.isEmpty() && tags.isEmpty()) "Корзина пуста" else "Задачи и теги можно вернуть в течение 60 дней.", color = Palette.Mute)
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            items(tasks, key = { "task" + it.id }) { task ->
                TrashRow(task.title) {
                    if (TaskRepo.restoreTask(task.id)) onMessage("Задача восстановлена")
                }
            }
            items(tags, key = { "tag" + it.id }) { tag ->
                TrashRow("#" + tag.name) {
                    onMessage(if (TaskRepo.restoreTag(tag.id)) "Тег восстановлен" else "Не удалось вернуть тег. Проверь название")
                }
            }
        }
    }
}

@Composable
private fun TrashRow(title: String, restore: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = Palette.Ink)
        TextButton(onClick = restore) { Text("Вернуть") }
    }
}

@Composable
private fun ToolsDialog(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title, fontFamily = Display, color = Palette.Ink) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() } },
        confirmButton = { TextButton(onClick = onClose) { Text("Закрыть") } },
        containerColor = Palette.Paper,
    )
}
