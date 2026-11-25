package ru.konem.ebis.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.konem.ebis.data.Tag
import ru.konem.ebis.data.TagColors
import ru.konem.ebis.data.TagOps

fun tagBg(tag: Tag) = Color(tag.color)
fun tagFg(tag: Tag) = if (TagColors.isLight(tag.color)) Color(0xFF111111) else Color.White

/** Плашка тега на карточке задачи. */
@Composable
fun TagBadge(tag: Tag) {
    Text(
        "# " + tag.name, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = tagFg(tag),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(tagBg(tag), RoundedCornerShape(7.dp))
            .border(2.dp, Palette.Ink, RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * Таблетка тега для выбора: своим цветом, если выбрана; иначе карточка с цветной точкой.
 */
@Composable
fun TagChip(tag: Tag, selected: Boolean, small: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val bg by animateColorAsState(
        when {
            selected -> tagBg(tag)
            hovered -> Palette.Hover
            else -> Palette.Card
        },
        tween(160), label = "tagBg",
    )
    val fg = if (selected) tagFg(tag) else Palette.Ink
    val lift by animateFloatAsState(if (selected) 1f else 0f, Motion.snappy(), label = "tagLift")
    Row(
        Modifier
            .graphicsLayer { translationY = -1.dp.toPx() * lift }
            .background(bg, shape)
            .border(2.dp, Palette.Ink, shape)
            .clickable(src, null, onClick = onClick)
            .hand()
            .padding(horizontal = if (small) 11.dp else 14.dp, vertical = if (small) 5.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (!selected) Box(Modifier.size(10.dp).background(tagBg(tag), CircleShape).border(1.5.dp, Palette.Ink, CircleShape))
        Text(tag.name, fontWeight = FontWeight.Bold, fontSize = if (small) 13.sp else 14.sp, color = fg, maxLines = 1)
    }
}

/** Пунктирная таблетка-действие: «+ тег», «Все» и т.п. */
@Composable
fun GhostChip(text: String, selected: Boolean = false, small: Boolean = false, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val bg by animateColorAsState(if (selected) Palette.AccentBg else if (hovered) Palette.Hover else Color.Transparent, tween(160), label = "ghostBg")
    Text(
        text, fontWeight = FontWeight.Bold, fontSize = if (small) 13.sp else 14.sp,
        color = if (selected) Palette.AccentFg else Palette.Ink, maxLines = 1,
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .then(if (selected) Modifier.border(2.dp, Palette.Ink, RoundedCornerShape(50)) else Modifier.dashedBorder(2.dp, Palette.Ink, 50.dp))
            .clickable(src, null, onClick = onClick)
            .hand()
            .padding(horizontal = if (small) 11.dp else 14.dp, vertical = if (small) 5.dp else 8.dp),
    )
}

/** Кружки готовых цветов. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagColorPicker(color: Int, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TagColors.presets.forEach { c ->
            val selected = c == color
            val src = remember { MutableInteractionSource() }
            val hovered by src.collectIsHoveredAsState()
            val s by animateFloatAsState(if (hovered || selected) 1.1f else 1f, Motion.snappy(), label = "tagSw")
            Box(
                Modifier
                    .size(36.dp)
                    .graphicsLayer { scaleX = s; scaleY = s }
                    .border(2.5.dp, if (selected) Palette.Ink else Color.Transparent, CircleShape)
                    .padding(3.dp)
                    .background(Color(c), CircleShape)
                    .border(2.dp, Palette.Ink, CircleShape)
                    .clickable(src, null) { onPick(c) }
                    .hand(),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Text("✓", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (TagColors.isLight(c)) Color(0xFF111111) else Color.White)
            }
        }
    }
}

/**
 * Форма тега: название, цвет, живое превью. [tag] = null — новый тег.
 * [onDone] получает id сохранённого тега.
 */
@Composable
fun TagEditor(state: AppState, tag: Tag?, onDone: (Long) -> Unit, onCancel: () -> Unit) {
    var name by remember(tag?.id) { mutableStateOf(tag?.name ?: "") }
    var color by remember(tag?.id) { mutableStateOf(tag?.color ?: nextFreeColor(state.tags)) }
    var problem by remember(tag?.id) { mutableStateOf<String?>(null) }

    fun save() {
        problem = TagOps.problem(state.tags, name, tag?.id)
        if (problem != null) return
        if (tag == null) state.addTag(name, color)?.let(onDone)
        else if (state.editTag(tag.id, name, color)) onDone(tag.id)
    }

    Column(
        Modifier.fillMaxWidth().brutal(Palette.Card, radius = 16.dp, shadow = 0.dp, border = 2.dp).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                Field(name, { if (it.length <= TagOps.MAX_NAME) name = it; problem = null }, "Название тега", singleLine = true)
            }
            TagBadge(Tag(0, name.trim().ifEmpty { "тег" }, color))
        }
        TagColorPicker(color) { color = it }
        problem?.let { Text(it, color = Palette.Red, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
            BrutalButton(onCancel, bg = Palette.Paper, shadow = 2.dp, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                Text("Отмена", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            BrutalButton(::save, bg = Palette.AccentBg, shadow = 2.dp, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                Text(if (tag == null) "Завести" else "Сохранить", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Palette.AccentFg)
            }
        }
    }
}

/** Новому тегу — первый цвет, который ещё никем не занят. */
fun nextFreeColor(tags: List<Tag>): Int =
    TagColors.presets.firstOrNull { c -> tags.none { it.color == c } } ?: TagColors.presets[tags.size % TagColors.presets.size]

/** Раздел настроек «Теги»: список, правка, удаление. */
@Composable
fun TagsPane(state: AppState) {
    var editing by remember { mutableStateOf<Long?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Long?>(null) }

    Text(
        "Заведи теги заранее — потом в форме задачи их можно просто натыкать, а над лентой фильтровать.",
        fontSize = 14.sp, color = Palette.Mute,
    )
    if (adding) {
        TagEditor(state, null, onDone = { adding = false }, onCancel = { adding = false })
    } else {
        Box { GhostChip("+ Новый тег") { adding = true; editing = null } }
    }
    if (state.tags.isEmpty() && !adding) {
        Text("Пока ни одного. Конь без тегов — просто конь.", fontSize = 14.sp, color = Palette.Faint)
    }
    state.tags.forEach { tag ->
        if (editing == tag.id) {
            TagEditor(state, tag, onDone = { editing = null }, onCancel = { editing = null })
        } else {
            val used = state.tasks.count { tag.id in it.tagIds && !it.done }
            Row(
                Modifier.fillMaxWidth().brutal(Palette.Card, radius = 16.dp, shadow = 0.dp, border = 2.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TagBadge(tag)
                Text(if (used == 0) "без задач" else "задач: $used", fontSize = 13.5.sp, color = Palette.Mute, modifier = Modifier.weight(1f))
                if (confirmDelete == tag.id) {
                    Text("точно?", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    CardAction("нахер", { trash(it, 1.8.dp.toPx()) }, danger = true) { confirmDelete = null; state.deleteTag(tag.id) }
                    CardAction("не", { cross(it, 1.8.dp.toPx()) }) { confirmDelete = null }
                } else {
                    GhostChip("править", small = true) { editing = tag.id; adding = false }
                    CardAction("нахер", { trash(it, 1.8.dp.toPx()) }, danger = true) { confirmDelete = tag.id }
                }
            }
        }
    }
}

/**
 * Полоска фильтра над лентой: «Все» и теги, листается вбок. Справа от неё — сортировка (её передаёт [trailing]).
 */
@Composable
fun TagFilterBar(state: AppState, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.tags.isNotEmpty()) {
                GhostChip("Все", selected = state.tagFilter.isEmpty(), small = true) { state.clearTagFilter() }
                state.tags.forEach { t -> TagChip(t, t.id in state.tagFilter, small = true) { state.toggleTagFilter(t.id) } }
            }
            GhostChip(if (state.tags.isEmpty()) "🏷 Завести теги" else "⚙", small = true) { state.openSettings(SettingsPane.Tags) }
        }
        trailing()
    }
}

/** Выбор тегов в форме задачи, с заведением нового тега на месте. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPicker(state: AppState, selected: Set<Long>, onChange: (Set<Long>) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.tags.forEach { t ->
            TagChip(t, t.id in selected) { onChange(if (t.id in selected) selected - t.id else selected + t.id) }
        }
        if (!adding) GhostChip("+ тег") { adding = true }
    }
    if (adding) {
        TagEditor(state, null, onDone = { id -> adding = false; onChange(selected + id) }, onCancel = { adding = false })
    }
}
