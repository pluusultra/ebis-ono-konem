@file:OptIn(ExperimentalLayoutApi::class)

package ru.konem.ebis.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.konem.ebis.data.Tag
import ru.konem.ebis.data.TagColors
import ru.konem.ebis.data.TagOps
import ru.konem.ebis.data.TaskRepo
import ru.konem.ebis.ui.theme.Body
import ru.konem.ebis.ui.theme.Mono
import ru.konem.ebis.ui.theme.Palette

private fun tagBg(color: Int) = Color(color)
private fun tagFg(color: Int) = if (TagColors.isLight(color)) Color(0xFF111111) else Color.White

/** Плашка тега на карточке задачи. */
@Composable
fun TagBadge(tag: Tag) {
    Text(
        "# " + tag.name, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = tagFg(tag.color),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(tagBg(tag.color), RoundedCornerShape(6.dp))
            .border(2.dp, Palette.Ink, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** Таблетка тега для выбора: своим цветом, если выбрана; иначе карточка с цветной точкой. */
@Composable
fun TagChip(tag: Tag, selected: Boolean, small: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val src = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) tagBg(tag.color) else Palette.Card, Motion.fade(), label = "tagBg")
    Row(
        Modifier
            .pressScale(src, 0.93f)
            .background(bg, shape)
            .border(2.5.dp, Palette.Ink, shape)
            .clickable(src, null, onClick = onClick)
            .padding(horizontal = if (small) 11.dp else 13.dp, vertical = if (small) 5.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!selected) Box(Modifier.size(9.dp).background(tagBg(tag.color), CircleShape).border(1.5.dp, Palette.Ink, CircleShape))
        Text(
            tag.name, fontWeight = FontWeight.Bold, fontSize = if (small) 12.5.sp else 13.5.sp,
            color = if (selected) tagFg(tag.color) else Palette.Ink, maxLines = 1,
        )
    }
}

/** Пунктирная таблетка-действие: «+ тег», «Все», «⚙». */
@Composable
fun GhostChip(text: String, selected: Boolean = false, small: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val src = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) Palette.AccentBg else Color.Transparent, Motion.fade(), label = "ghostBg")
    Text(
        text, fontWeight = FontWeight.Bold, fontSize = if (small) 12.5.sp else 13.5.sp,
        color = if (selected) Palette.AccentFg else Palette.Ink, maxLines = 1,
        modifier = Modifier
            .pressScale(src, 0.93f)
            .background(bg, shape)
            .then(if (selected) Modifier.border(2.5.dp, Palette.Ink, shape) else Modifier.dashedBorder(2.dp, Palette.Ink, 50.dp))
            .clickable(src, null, onClick = onClick)
            .padding(horizontal = if (small) 11.dp else 13.dp, vertical = if (small) 5.dp else 7.dp),
    )
}

@Composable
private fun TagColorPicker(color: Int, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TagColors.presets.forEach { c ->
            val selected = c == color
            val s by animateFloatAsState(if (selected) 1.12f else 1f, Motion.snappy(), label = "tagSw")
            Box(
                Modifier
                    .size(38.dp)
                    .graphicsLayer { scaleX = s; scaleY = s }
                    .border(2.5.dp, if (selected) Palette.Ink else Color.Transparent, CircleShape)
                    .padding(3.dp)
                    .background(tagBg(c), CircleShape)
                    .border(2.dp, Palette.Ink, CircleShape)
                    .clickable(remember { MutableInteractionSource() }, null) { onPick(c) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Text("✓", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = tagFg(c))
            }
        }
    }
}

/** Новому тегу — первый цвет, который ещё никем не занят. */
private fun nextFreeColor(tags: List<Tag>): Int =
    TagColors.presets.firstOrNull { c -> tags.none { it.color == c } } ?: TagColors.presets[tags.size % TagColors.presets.size]

/** Форма тега: название, цвет, живое превью. [tag] = null — новый; [onDone] получает id. */
@Composable
fun TagEditor(tag: Tag?, onDone: (Long) -> Unit, onCancel: () -> Unit) {
    val tags = TaskRepo.tags
    var name by rememberSaveable(tag?.id) { mutableStateOf(tag?.name ?: "") }
    var color by rememberSaveable(tag?.id) { mutableStateOf(tag?.color ?: nextFreeColor(tags)) }
    var problem by remember(tag?.id) { mutableStateOf<String?>(null) }

    fun save() {
        problem = TagOps.problem(tags, name, tag?.id)
        if (problem != null) return
        if (tag == null) onDone(TaskRepo.addTag(name, color))
        else {
            TaskRepo.editTag(tag.id, name, color)
            onDone(tag.id)
        }
    }

    Column(
        Modifier.fillMaxWidth().brutal(Palette.Paper, radius = 14.dp, shadow = 0.dp, border = 2.dp).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicTextField(
                value = name,
                onValueChange = { if (it.length <= TagOps.MAX_NAME) name = it; problem = null },
                singleLine = true,
                textStyle = TextStyle(fontFamily = Body, fontSize = 15.sp, color = Palette.Ink),
                cursorBrush = SolidColor(Palette.Ink),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxWidth()
                            .brutal(if (problem != null) Palette.ErrorBg else Palette.Card, radius = 12.dp, shadow = 0.dp, borderColor = if (problem != null) Palette.Red else Palette.Ink)
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        if (name.isEmpty()) Text("Название тега", fontSize = 15.sp, color = Palette.Faint)
                        inner()
                    }
                },
            )
            TagBadge(Tag(0, name.trim().ifEmpty { "тег" }, color))
        }
        TagColorPicker(color) { color = it }
        AnimatedVisibility(problem != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Text(problem.orEmpty(), color = Palette.Red, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            SelectChip("Отмена", false, onClick = onCancel)
            SelectChip(if (tag == null) "Завести" else "Сохранить", true, onClick = ::save)
        }
    }
}

/** Содержимое раздела настроек «Теги»: список, правка, удаление. */
@Composable
fun TagsManager(onToast: (String) -> Unit) {
    val tags = TaskRepo.tags
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Long?>(null) }

    if (adding) TagEditor(null, onDone = { adding = false }, onCancel = { adding = false })
    else Box { GhostChip("+ Новый тег") { adding = true; editing = null } }

    tags.forEach { tag ->
        if (editing == tag.id) {
            TagEditor(tag, onDone = { editing = null }, onCancel = { editing = null })
        } else {
            val used = TaskRepo.tasks.count { tag.id in it.tagIds && !it.done }
            Row(
                Modifier.fillMaxWidth().clickable { editing = tag.id; adding = false; confirmDelete = null }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TagBadge(tag)
                Text(
                    if (used == 0) "без задач" else "задач: $used", fontSize = 12.5.sp, color = Palette.Mute,
                    modifier = Modifier.weight(1f),
                )
                if (confirmDelete == tag.id) {
                    Text(
                        "точно нахер", fontFamily = Mono, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Palette.Red, RoundedCornerShape(8.dp))
                            .clickable {
                                confirmDelete = null
                                TaskRepo.deleteTag(tag.id)
                                onToast("Тег «${tag.name}» — нахер")
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                    Text("не", fontFamily = Mono, fontSize = 11.sp, modifier = Modifier.clickable { confirmDelete = null }.padding(6.dp))
                } else {
                    Text("✎", fontSize = 15.sp, modifier = Modifier.clickable { editing = tag.id; adding = false }.padding(6.dp))
                    Text("нахер", fontFamily = Mono, fontSize = 10.5.sp, color = Palette.Faint, modifier = Modifier.clickable { confirmDelete = tag.id }.padding(6.dp))
                }
            }
        }
    }
}

/** Выбор тегов в форме задачи, с заведением нового тега прямо здесь. */
@Composable
fun TagPicker(selected: Set<Long>, onChange: (Set<Long>) -> Unit) {
    var adding by rememberSaveable { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TaskRepo.tags.forEach { t ->
            TagChip(t, t.id in selected) { onChange(if (t.id in selected) selected - t.id else selected + t.id) }
        }
        if (!adding) GhostChip("+ тег") { adding = true }
    }
    AnimatedVisibility(adding, enter = fadeIn() + expandVertically(Motion.smooth()), exit = fadeOut() + shrinkVertically(Motion.smooth())) {
        TagEditor(null, onDone = { id -> adding = false; onChange(selected + id) }, onCancel = { adding = false })
    }
}

/** Полоска фильтра по тегам. Справа к ней пристраивается [trailing] — сортировка. */
@Composable
fun TagFilterBar(
    filter: Set<Long>,
    onFilter: (Set<Long>) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    val tags = TaskRepo.tags
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (tags.isNotEmpty()) {
                GhostChip("Все", selected = filter.isEmpty(), small = true) { onFilter(emptySet()) }
                tags.forEach { t ->
                    TagChip(t, t.id in filter, small = true) { onFilter(if (t.id in filter) filter - t.id else filter + t.id) }
                }
            }
            GhostChip(if (tags.isEmpty()) "🏷 Теги" else "⚙", small = true, onClick = onManage)
        }
        trailing()
    }
}
