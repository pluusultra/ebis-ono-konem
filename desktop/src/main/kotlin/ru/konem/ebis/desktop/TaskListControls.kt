package ru.konem.ebis.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.konem.ebis.data.Task

internal enum class ListOrder(val label: String, val compactLabel: String) {
    Due("По сроку", "Срок"),
    Newest("Сначала новые", "Новые"),
    Oldest("Сначала старые", "Старые");

    fun orderMatches(matches: List<Task>): List<Task> = when (this) {
        Due -> matches
        Newest -> matches.sortedByDescending { it.id }
        Oldest -> matches.sortedBy { it.id }
    }
}

@Composable
internal fun TaskListControls(
    state: AppState,
    compact: Boolean,
    orders: List<ListOrder>,
    order: ListOrder,
    onOrder: (ListOrder) -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val focused = active && state.searchFocused
    val shape = RoundedCornerShape(12.dp)
    LaunchedEffect(active, state.searchFocusRequest) {
        if (!active) return@LaunchedEffect
        val request = state.searchFocusRequest ?: return@LaunchedEffect
        state.searchFocusRequest = null
        when (request) {
            SearchFocusRequest.Focus -> focus.requestFocus()
            SearchFocusRequest.Clear -> if (state.searchFocused) focusManager.clearFocus()
        }
    }
    val borderColor by animateColorAsState(when {
        focused -> Palette.Hay
        state.searchQuery.isNotBlank() -> Palette.Ink
        else -> Palette.Ink.copy(alpha = 0.32f)
    }, tween(160), label = "searchBorder")
    val iconColor by animateColorAsState(if (focused) Palette.Hay else Palette.Mute, tween(160), label = "searchIcon")

    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicTextField(
            value = state.searchQuery,
            onValueChange = { state.searchQuery = it },
            modifier = Modifier.weight(1f).focusRequester(focus).onFocusChanged { state.searchFocused = it.isFocused }
                .semantics { contentDescription = "Поиск по задачам" },
            enabled = active,
            singleLine = true,
            textStyle = TextStyle(color = Palette.Ink, fontSize = 15.sp),
            cursorBrush = SolidColor(Palette.Ink),
            decorationBox = { input ->
                Row(
                    Modifier.fillMaxWidth().height(42.dp).background(Palette.Card, shape)
                        .border(1.5.dp, borderColor, shape)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Canvas(Modifier.size(17.dp)) {
                        val center = Offset(size.width * 0.4f, size.height * 0.4f)
                        drawCircle(iconColor, size.width * 0.32f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.7.dp.toPx()))
                        drawLine(iconColor, center * 1.55f, Offset(size.width * 0.9f, size.height * 0.9f), 1.7.dp.toPx(), StrokeCap.Round)
                    }
                    Box(Modifier.weight(1f)) {
                        if (state.searchQuery.isEmpty()) {
                            Text(
                                if (compact) "Поиск задач" else "Поиск по названию, описанию или #тегу",
                                color = Palette.Mute, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        input()
                    }
                    if (state.searchQuery.isNotEmpty()) {
                        Box(
                            Modifier.size(30.dp).clickable(enabled = active, role = Role.Button, onClickLabel = "Очистить поиск") {
                                state.searchQuery = ""
                                focus.requestFocus()
                            }.hand().semantics { contentDescription = "Очистить поиск" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Canvas(Modifier.size(14.dp)) { cross(iconColor, 1.8.dp.toPx()) }
                        }
                    } else if (!compact && !focused) {
                        Keycaps(state.keys[Action.Search], small = true)
                    }
                }
            },
        )
        if (orders.isNotEmpty()) SortMenu(order, orders, compact, active, onOrder)
    }
}

@Composable
private fun SortMenu(order: ListOrder, orders: List<ListOrder>, compact: Boolean, active: Boolean, onOrder: (ListOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var anchorWidth by remember { mutableIntStateOf(0) }
    val open = active && expanded
    val menuWidth = 174.dp
    val menuOffset = with(LocalDensity.current) { anchorWidth.toDp() } - menuWidth
    val triggerShape = RoundedCornerShape(12.dp)
    val triggerSource = remember { MutableInteractionSource() }
    val hovered by triggerSource.collectIsHoveredAsState()
    val bg by animateColorAsState(
        if (open) lerp(Palette.Card, Palette.Hay, 0.14f) else if (hovered) Palette.Hover else Palette.Card,
        tween(150), label = "sortTriggerFill",
    )
    val outline by animateColorAsState(
        if (open) Palette.Hay else Palette.Ink.copy(alpha = if (hovered) 0.58f else 0.32f),
        tween(150), label = "sortTriggerBorder",
    )
    val rotation by animateFloatAsState(if (open) 180f else 0f, Motion.snappy(), label = "sortChevron")
    Box(Modifier.onSizeChanged { anchorWidth = it.width }) {
        Row(
            Modifier.height(42.dp).background(bg, triggerShape).border(1.5.dp, outline, triggerShape)
                .hoverable(triggerSource)
                .clickable(triggerSource, null, enabled = active, role = Role.Button, onClickLabel = "Порядок сортировки") {
                    expanded = !expanded
                }.hand()
                .padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            val color = Palette.Ink
            Canvas(Modifier.size(14.dp)) {
                listOf(1f, 0.72f, 0.43f).forEachIndexed { index, length ->
                    val y = size.height * (0.2f + index * 0.3f)
                    drawLine(color, Offset(0f, y), Offset(size.width * length, y), 1.7.dp.toPx(), StrokeCap.Round)
                }
            }
            Text(if (compact) order.compactLabel else order.label, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            Canvas(Modifier.size(11.dp).graphicsLayer { rotationZ = rotation }) {
                val start = Offset(size.width * 0.12f, size.height * 0.35f)
                val middle = Offset(size.width * 0.5f, size.height * 0.68f)
                val end = Offset(size.width * 0.88f, size.height * 0.35f)
                drawLine(color, start, middle, 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(color, middle, end, 1.5.dp.toPx(), StrokeCap.Round)
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(menuWidth),
            offset = DpOffset(menuOffset, 6.dp),
            shape = RoundedCornerShape(13.dp),
            containerColor = Palette.Card,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.5.dp, Palette.Ink.copy(alpha = 0.63f)),
        ) {
            orders.forEach { option ->
                SortOption(option, current = order == option) {
                    expanded = false
                    onOrder(option)
                }
            }
        }
    }
}

@Composable
private fun SortOption(option: ListOrder, current: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    val background by animateColorAsState(
        when {
            current -> lerp(Palette.Card, Palette.Hay, if (hovered || focused) 0.29f else 0.20f)
            hovered || focused -> Palette.Hover
            else -> Palette.Card
        },
        tween(130), label = "sortOption",
    )
    DropdownMenuItem(
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp).height(33.dp).clip(shape)
            .background(background, shape)
            .hoverable(source).hand()
            .semantics { selected = current },
        text = { Text(option.label, color = Palette.Ink, fontWeight = if (current) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp, maxLines = 1) },
        trailingIcon = if (current) {
            {
                val ink = Palette.OnHay
                Box(Modifier.size(18.dp).background(Palette.Hay, CircleShape), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(11.dp)) { check(ink, 1.8.dp.toPx()) }
                }
            }
        } else null,
        contentPadding = PaddingValues(horizontal = 9.dp),
        interactionSource = source,
        onClick = onClick,
    )
}
