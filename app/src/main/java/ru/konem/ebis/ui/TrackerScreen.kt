@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package ru.konem.ebis.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.konem.ebis.R
import ru.konem.ebis.data.Repeat
import ru.konem.ebis.data.TagOps
import ru.konem.ebis.data.Task
import ru.konem.ebis.data.OverdueAction
import androidx.compose.runtime.saveable.rememberSaveable
import ru.konem.ebis.data.TaskRepo
import ru.konem.ebis.data.toLocalDate
import ru.konem.ebis.ui.theme.Body
import ru.konem.ebis.ui.theme.Display
import ru.konem.ebis.ui.theme.Mono
import ru.konem.ebis.ui.theme.Palette
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.min

/** Горящие наверху, дальше — ручной порядок (новые задачи получают больший order). */
private fun byHotThenOrder(newFirst: Boolean) = compareByDescending<Task> { it.hot }
    .then(if (newFirst) compareByDescending { it.order } else compareBy { it.order })

fun filterTasks(tasks: List<Task>, tab: Tab, today: LocalDate, newFirst: Boolean = false): List<Task> = when (tab) {
    Tab.Done -> tasks.filter { it.done }.sortedByDescending { it.doneAt }
    Tab.All -> tasks.filter { !it.done }.sortedWith(compareByDescending<Task> { it.hot }.thenBy { it.due }.thenBy { it.order })
    Tab.Today -> tasks.filter { !it.done && !it.due.isAfter(today) }.sortedWith(byHotThenOrder(newFirst))
}

/** Строка списка: заголовок группы или задача. */
private sealed interface ListRow {
    val key: Any

    data class Header(val label: String, val count: Int) : ListRow {
        override val key get() = "h_$label"
    }

    data class Item(val task: Task) : ListRow {
        override val key get() = task.id
    }
}

private fun buildRows(tab: Tab, tasks: List<Task>, today: LocalDate, newFirst: Boolean): List<ListRow> {
    val list = filterTasks(tasks, tab, today, newFirst)

    return when (tab) {
        Tab.Today -> list.map { ListRow.Item(it) }
        Tab.All -> {
            val week = today.plusDays(7)
            grouped(
                listOf(
                    "🪦 Просрочено" to list.filter { it.due.isBefore(today) },
                    "🔥 Сегодня" to list.filter { it.due == today },
                    "⏭ Завтра" to list.filter { it.due == today.plusDays(1) },
                    "📆 На неделе" to list.filter { it.due.isAfter(today.plusDays(1)) && !it.due.isAfter(week) },
                    "🏔 Потом" to list.filter { it.due.isAfter(week) },
                )
            )
        }
        Tab.Done -> {
            val byDay = list.groupBy { it.doneAt.toLocalDate() }
            grouped(byDay.entries.sortedByDescending { it.key }.map { (day, ts) ->
                val label = when (day) {
                    today -> "Сегодня"
                    today.minusDays(1) -> "Вчера"
                    else -> Copy.dayLabel(day, today)
                }
                label to ts
            })
        }
    }
}

private fun grouped(groups: List<Pair<String, List<Task>>>) = groups.filter { it.second.isNotEmpty() }
    .flatMap { (label, ts) -> listOf(ListRow.Header(label, ts.size)) + ts.map { ListRow.Item(it) } }

class TrackerActions(
    val onToggle: (Long) -> Unit,
    val onPostpone: (Long) -> Unit,
    val onDelete: (Long) -> Unit,
    val onEdit: (Task) -> Unit,
    val onReorder: (List<Long>) -> Unit,
)

@Composable
fun TrackerScreen(
    tasks: List<Task>,
    tab: Tab,
    onTab: (Tab) -> Unit,
    banner: String?,
    onDismissBanner: () -> Unit,
    actions: TrackerActions,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    streak: Int,
    bestStreak: Int,
    newFirst: Boolean,
    onToggleSort: () -> Unit,
    tagFilter: Set<Long> = emptySet(),
    onTagFilter: (Set<Long>) -> Unit = {},
    onBatch: (Set<Long>, OverdueAction, Set<Long>) -> Unit = { _, _, _ -> },
    onMoveOverdue: (Set<Long>, LocalDate) -> Unit = { _, _ -> },
) {
    val today = LocalDate.now()
    val counts = Tab.entries.associateWith { filterTasks(tasks, it, today).size }
    val tags = TaskRepo.tags
    var query by rememberSaveable(tab) { mutableStateOf("") }
    var searchOrder by rememberSaveable { mutableStateOf(TaskListOrder.Due) }
    var searchFocused by remember { mutableStateOf(false) }
    var selectionIds by rememberSaveable(tab) { mutableStateOf<LongArray?>(null) }
    val selection = selectionIds?.toSet()
    val searching = query.isNotBlank()
    val selecting = selection != null
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun dismissInput() {
        focus.clearFocus()
        keyboard?.hide()
    }
    fun closeSearch() {
        query = ""
        dismissInput()
    }
    fun closeSelection() {
        selectionIds = null
        dismissInput()
    }
    BackHandler(enabled = selecting || query.isNotEmpty() || searchFocused) {
        if (selecting) closeSelection() else closeSearch()
    }
    val matches = remember(tasks, tags, query, searchOrder) {
        searchOrder.sortMatches(TaskSearch.find(tasks, tags, query))
    }
    val overdue = remember(tasks, tab, today, tagFilter, searching) {
        if (tab == Tab.Done || searching) emptySet()
        else tasks.filter { !it.done && it.due.isBefore(today) && TagOps.matches(it, tagFilter) }.map { it.id }.toSet()
    }
    val selected = selection.orEmpty().intersect(overdue)
    val tabOrder = if (newFirst) TaskListOrder.Newest else TaskListOrder.Oldest
    val orders = when {
        searching -> TaskListOrder.entries
        tab == Tab.Today -> listOf(TaskListOrder.Newest, TaskListOrder.Oldest)
        else -> emptyList()
    }

    // Шапка падает сверху с пружинкой после появления экрана.
    val drop = remember { Animatable(-1f) }
    LaunchedEffect(Unit) {
        delay(250)
        drop.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }
    // Первые секунды карточки въезжают лесенкой, дальше — обычные анимации списка.
    var intro by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1100)
        intro = false
    }

    Box(Modifier.fillMaxSize().background(Palette.Paper).then(if (searchFocused || selecting) Modifier.imePadding() else Modifier)) {
        Column(Modifier.fillMaxSize()) {
            Header(
                subtitle = Copy.subtitle(counts.getValue(Tab.Today)),
                streak = streak,
                onSettings = onSettings,
                modifier = Modifier
                    .zIndex(1f)
                    .graphicsLayer { translationY = drop.value * size.height },
            )
            Tabs(if (searching) null else tab, { target ->
                closeSearch()
                selectionIds = null
                onTab(target)
            }, counts)
            AnimatedContent(
                selecting,
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp),
                transitionSpec = {
                    (fadeIn(tween(200)) + slideInVertically(tween(200)) { -it / 4 }) togetherWith fadeOut(tween(110))
                },
                label = "taskControls",
            ) { showSelection ->
                if (showSelection) {
                    OverdueSelectionBar(
                        selected, overdue, tags, today,
                        onCancel = ::closeSelection,
                        onToggleAll = {
                            if (selectionIds != null) selectionIds = if (selected == overdue) LongArray(0) else overdue.toLongArray()
                        },
                        onMove = { due ->
                            if (selected.isNotEmpty()) {
                                onMoveOverdue(selected, due)
                                closeSelection()
                            }
                        },
                        onApply = { action, tagIds ->
                            if (selected.isNotEmpty()) {
                                onBatch(selected, action, tagIds)
                                closeSelection()
                            }
                        },
                    )
                } else {
                    TaskListControls(
                        query, { query = it }, { searchFocused = it }, ::closeSearch, ::dismissInput,
                        orders, if (searching) searchOrder else tabOrder,
                        onOrder = { if (searching) searchOrder = it else if (it != tabOrder) onToggleSort() },
                        enabled = !selecting,
                    )
                }
            }
            AnimatedVisibility(
                banner != null,
                enter = fadeIn() + expandVertically(Motion.smooth()),
                exit = fadeOut(tween(150)) + shrinkVertically(Motion.smooth()),
            ) {
                Banner(banner.orEmpty(), onDismissBanner)
            }
            // Смена вкладки: старый список тает, новый проявляется с лёгким сдвигом в сторону вкладки.
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (fadeIn(tween(220, delayMillis = 70)) + slideInHorizontally(Motion.smooth(IntOffset(1, 1))) { dir * it / 10 }) togetherWith
                        (fadeOut(tween(110)) + slideOutHorizontally(tween(160)) { -dir * it / 14 })
                },
                label = "tabPage",
            ) { t ->
                TaskPage(
                    t, tasks, today, intro, actions, streak, bestStreak, newFirst, tagFilter, onTagFilter,
                    searchMatches = if (searching) matches else null,
                    query = query.trim(),
                    searchOrder = searchOrder,
                    overdue = overdue,
                    selection = if (selecting) selected else null,
                    onStartSelection = { id ->
                        dismissInput()
                        selectionIds = if (id == null) LongArray(0) else longArrayOf(id)
                    },
                    onSelect = { id ->
                        selectionIds?.let { current ->
                            val ids = current.toSet().intersect(overdue)
                            selectionIds = (if (id in ids) ids - id else ids + id).toLongArray()
                        }
                    },
                )
            }
        }

        // «Накинуть» выпрыгивает чуть позже шапки.
        val fab = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            delay(450)
            fab.animateTo(1f, Motion.bouncy())
        }
        if (!selecting && !searchFocused) BrutalButton(
            onClick = onAdd,
            bg = Palette.Red,
            shadow = 4.dp,
            contentPadding = PaddingValues(start = 14.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 18.dp, bottom = 20.dp)
                .graphicsLayer {
                    scaleX = fab.value; scaleY = fab.value
                    rotationZ = (1f - fab.value) * -12f
                    transformOrigin = TransformOrigin(1f, 1f)
                },
        ) {
            Text("+", color = Color.White, fontSize = 24.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
            Text("Накинуть", color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun TaskPage(
    tab: Tab,
    tasks: List<Task>,
    today: LocalDate,
    intro: Boolean,
    actions: TrackerActions,
    streak: Int,
    bestStreak: Int,
    newFirst: Boolean,
    tagFilter: Set<Long>,
    onTagFilter: (Set<Long>) -> Unit,
    searchMatches: List<Task>?,
    query: String,
    searchOrder: TaskListOrder,
    overdue: Set<Long>,
    selection: Set<Long>?,
    onStartSelection: (Long?) -> Unit,
    onSelect: (Long) -> Unit,
) {
    val searching = searchMatches != null
    val selecting = selection != null
    val rows = remember(tasks, tab, today, newFirst, tagFilter, searchMatches) {
        if (searchMatches != null) {
            val (active, done) = searchMatches.partition { !it.done }
            grouped(listOf("Активные" to active, "Сделано" to done))
        } else buildRows(tab, tasks.filter { TagOps.matches(it, tagFilter) }, today, newFirst)
    }
    val tabTotal = remember(tasks, tab, today) { filterTasks(tasks, tab, today).size }
    val tags = TaskRepo.tags
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listState = rememberLazyListState()
    LaunchedEffect(query, searchOrder) { listState.scrollToItem(0) }
    val haptic = LocalHapticFeedback.current

    // Перетаскивание (только «Сегодня»): пока тащим, показываем локальный порядок.
    var dragOrder by remember { mutableStateOf<List<ListRow>?>(null) }
    var dragKey by remember { mutableStateOf<Long?>(null) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    val shown = dragOrder ?: rows

    fun onDrag(dy: Float) {
        dragDy += dy
        val order = dragOrder ?: return
        val info = listState.layoutInfo.visibleItemsInfo
        val me = info.firstOrNull { it.key == dragKey } ?: return
        val center = me.offset + dragDy + me.size / 2f
        val target = info.firstOrNull {
            it.key != dragKey && it.key is Long && center > it.offset && center < it.offset + it.size
        } ?: return
        val from = order.indexOfFirst { it.key == dragKey }
        val to = order.indexOfFirst { it.key == target.key }
        if (from < 0 || to < 0) return
        dragOrder = order.toMutableList().apply { add(to, removeAt(from)) }
        // Карточка встанет на место соседа — компенсируем, чтобы под пальцем она не прыгнула.
        dragDy -= (target.offset - me.offset)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 110.dp + nav),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (tab == Tab.Done && !searching) {
            item(key = "stats") {
                val doneToday = rows.filterIsInstance<ListRow.Item>().count { it.task.doneAt.toLocalDate() == today }
                Stats(doneToday, streak, bestStreak, Modifier.animateItem(placementSpec = Motion.placement))
            }
        }
        if (searchMatches != null) {
            item(key = "search-summary") {
                Text("Поиск во всех задачах · " + searchMatches.size, color = Palette.Mute, fontSize = 12.sp)
            }
        } else if (!selecting && (tags.isNotEmpty() || overdue.isNotEmpty())) {
            item(key = "filter") {
                TagFilterBar(tagFilter, onTagFilter, Modifier.animateItem(placementSpec = Motion.placement)) {
                    if (overdue.isNotEmpty()) {
                        GhostChip("Разгрести просрочку", small = true) { onStartSelection(null) }
                    }
                }
            }
        }
        if (rows.isEmpty()) {
            if (searching) {
                item(key = "empty_search") {
                    Text("Ничего не нашлось", Modifier.padding(vertical = 24.dp), color = Palette.Mute, fontSize = 15.sp)
                }
            } else if (tagFilter.isNotEmpty() && tabTotal > 0) {
                item(key = "empty_filter") {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 36.dp).animateItem(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("С такими тегами тут пусто", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        GhostChip("Показать всё") { onTagFilter(emptySet()) }
                    }
                }
            } else {
                item(key = "empty_$tab") { Empty(tab, Modifier.animateItem()) }
            }
        }
        var index = 0
        shown.forEach { row ->
            val i = index++
            when (row) {
                is ListRow.Header -> stickyHeader(key = row.key, contentType = "header") {
                    SectionHeader(row.label, row.count, Modifier.animateItem(placementSpec = Motion.placement))
                }
                is ListRow.Item -> item(key = row.key, contentType = "task") {
                    val t = row.task
                    val eligible = t.id in overdue
                    val canReorder = tab == Tab.Today && !selecting && !searching
                    val isDragged = dragKey == t.id
                    val lift by animateFloatAsState(if (isDragged) 1f else 0f, Motion.snappy(), label = "lift")
                    TaskCard(
                        task = t,
                        today = today,
                        actions = actions,
                        lift = lift,
                        selecting = selecting,
                        selected = selection?.contains(t.id) == true,
                        onSelect = if (selecting && eligible) ({ onSelect(t.id) }) else null,
                        onLongPress = if (!selecting && eligible && !canReorder) ({ onStartSelection(t.id) }) else null,
                        keepInList = searching,
                        dragModifier = if (!canReorder) Modifier else Modifier.pointerInput(t.id, rows, newFirst, eligible) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragDy = 0f
                                },
                                onDrag = { change, amount ->
                                    if (dragOrder == null) {
                                        dragOrder = rows
                                        dragKey = t.id
                                    }
                                    change.consume()
                                    onDrag(amount.y)
                                },
                                onDragEnd = {
                                    dragOrder?.let { o ->
                                        // reorder ждёт порядок «сверху вниз = по возрастанию order».
                                        val ids = o.filterIsInstance<ListRow.Item>().map { it.task.id }
                                        actions.onReorder(if (newFirst) ids.reversed() else ids)
                                    } ?: if (eligible) onStartSelection(t.id) else Unit
                                    dragOrder = null
                                    dragKey = null
                                    dragDy = 0f
                                },
                                onDragCancel = {
                                    dragOrder = null
                                    dragKey = null
                                    dragDy = 0f
                                },
                            )
                        },
                        modifier = Modifier
                            .zIndex(if (isDragged) 1f else 0f)
                            .graphicsLayer { if (isDragged) translationY = dragDy }
                            .animateItem(
                                fadeInSpec = tween(220),
                                placementSpec = if (isDragged) null else Motion.placement,
                                fadeOutSpec = tween(180),
                            )
                            .stagger(i, intro && !searching),
                    )
                }
            }
        }
    }
}

/** Лесенка при первом показе: каждая следующая карточка чуть позже. */
@Composable
private fun Modifier.stagger(index: Int, enabled: Boolean): Modifier {
    if (!enabled) return this
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(200L + index * 45L)
        a.animateTo(1f, Motion.smooth())
    }
    return graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 36.dp.toPx()
        val s = 0.96f + 0.04f * a.value
        scaleX = s; scaleY = s
    }
}

@Composable
private fun Header(subtitle: String, streak: Int, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    HayBar(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeaderMascot()
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.app_name), fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 20.sp,
                    lineHeight = 23.sp, letterSpacing = (-0.5).sp, maxLines = 2,
                )
                Row(
                    Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AnimatedContent(
                        subtitle,
                        transitionSpec = {
                            (fadeIn(tween(200)) + slideInVertically(Motion.snappy(IntOffset(1, 1))) { it / 2 }) togetherWith
                                (fadeOut(tween(120)) + slideOutVertically(tween(160)) { -it / 2 })
                        },
                        label = "subtitle",
                        modifier = Modifier.weight(1f, fill = false),
                    ) { s ->
                        Text(s, fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 16.sp, color = Palette.HeaderSub)
                    }
                    AnimatedVisibility(streak >= 2, enter = fadeIn() + scaleIn(Motion.bouncy()), exit = fadeOut()) {
                        Text(
                            "🔥 $streak",
                            fontFamily = Mono, fontSize = 11.sp, color = Palette.OnHay,
                            modifier = Modifier
                                .background(Palette.HeaderBtn, RoundedCornerShape(6.dp))
                                .border(1.5.dp, Palette.OnHay, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            SettingsButton(onSettings)
        }
    }
}

/** Шестерёнка: вдавливается в тень и проворачивается при нажатии. */
@Composable
private fun SettingsButton(onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val p by animateFloatAsState(if (pressed) 1f else 0f, Motion.snappy(), label = "gearPress")
    var turns by remember { mutableIntStateOf(0) }
    val rot by animateFloatAsState(turns * 90f, Motion.bouncy(), label = "gearRot")
    Box(
        Modifier
            .offset(2.dp * p, 2.dp * p)
            .size(44.dp)
            .brutal(Palette.HeaderBtn, radius = 13.dp, shadow = 3.dp - 3.dp * p, borderColor = Palette.OnHay, shadowColor = Palette.OnHay)
            .clickable(src, null) {
                turns++
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(R.drawable.ic_gear), "Настройки",
            colorFilter = ColorFilter.tint(Palette.OnHay),
            modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = rot },
        )
    }
}

@Composable
private fun Tabs(tab: Tab?, onTab: (Tab) -> Unit, counts: Map<Tab, Int>) {
    val labels = mapOf(Tab.Today to "🔥 Сегодня", Tab.All to "📦 Все задачи", Tab.Done to "✅ Сделано")
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Tab.entries.forEach { t ->
            TabPill(labels.getValue(t), counts[t] ?: 0, t == tab) { onTab(t) }
        }
    }
}

/**
 * Вкладка-кнопка: выбранная «вдавлена» в свою тень и залита акцентом,
 * при выборе коротко пружинит.
 */
@Composable
private fun TabPill(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val p by animateFloatAsState(if (selected || pressed) 1f else 0f, Motion.snappy(), label = "tabPress")
    val bg by animateColorAsState(if (selected) Palette.AccentBg else Palette.Card, tween(180), label = "tabBg")
    val fg by animateColorAsState(if (selected) Palette.AccentFg else Palette.Ink, tween(180), label = "tabFg")
    val pop = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(selected) {
        if (first) { first = false; return@LaunchedEffect }
        if (selected) {
            pop.snapTo(0.92f)
            pop.animateTo(1f, Motion.bouncy())
        }
    }
    Row(
        Modifier
            .offset(2.dp * p, 2.dp * p)
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .brutal(bg, radius = 50.dp, shadow = 2.dp - 2.dp * p, border = 2.5.dp)
            .clickable(src, null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = fg, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        AnimatedCount(count, fg.copy(alpha = 0.7f))
    }
}

/** Число, которое прокручивается как табло: вверх при росте, вниз при уменьшении. */
@Composable
private fun AnimatedCount(n: Int, color: Color) {
    AnimatedContent(
        n,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(Motion.snappy(IntOffset(1, 1))) { if (up) it else -it } + fadeIn(tween(150))) togetherWith
                (slideOutVertically(tween(150)) { if (up) -it else it } + fadeOut(tween(120)))
        },
        label = "count",
    ) { v ->
        Text("$v", fontFamily = Mono, fontSize = 11.sp, color = color)
    }
}

@Composable
private fun Banner(text: String, onDismiss: () -> Unit) {
    Row(
        Modifier
            .padding(start = 14.dp, end = 14.dp, top = 6.dp)
            .fillMaxWidth()
            .background(Palette.Banner, RoundedCornerShape(12.dp))
            .dashedBorder(2.dp, Palette.Ink, 12.dp)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            "✕", fontSize = 16.sp,
            modifier = Modifier
                .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Переключатель порядка во вкладке «Сегодня»: новые сверху / старые сверху. */
@Composable
private fun SectionHeader(label: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Palette.Paper)
            .padding(top = 6.dp, bottom = 2.dp, start = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("$count", fontFamily = Mono, fontSize = 11.sp, color = Palette.Mute2)
        Box(Modifier.weight(1f).height(2.dp).background(Palette.Ink.copy(alpha = 0.15f)))
    }
}

@Composable
private fun Stats(doneToday: Int, streak: Int, best: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Сегодня закрыл", doneToday, "", Modifier.weight(1f))
        StatTile("Серия", streak, "🔥", Modifier.weight(1f))
        StatTile("Рекорд", best, "🏆", Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: Int, emoji: String, modifier: Modifier = Modifier) {
    Column(
        modifier.brutal(Palette.Card, radius = 12.dp, shadow = 2.dp).padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 11.sp, color = Palette.Mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (emoji.isNotEmpty()) Text(emoji, fontSize = 15.sp, modifier = Modifier.padding(end = 4.dp))
            AnimatedContent(
                value,
                transitionSpec = {
                    (slideInVertically(Motion.bouncy(IntOffset(1, 1))) { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                },
                label = "stat",
            ) { v ->
                Text("$v", fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: Task,
    today: LocalDate,
    actions: TrackerActions,
    lift: Float,
    selecting: Boolean,
    selected: Boolean,
    onSelect: (() -> Unit)?,
    onLongPress: (() -> Unit)?,
    keepInList: Boolean,
    dragModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // Сначала галочка рисуется и зачёркивает текст, потом карточка уезжает вбок.
    var checking by remember { mutableStateOf(false) }
    val leave = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(1) }
    var swipe by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(task, selecting) {
        checking = false
        leave.snapTo(0f)
        swipe = 0f
    }
    val done = task.done xor checking

    fun commit(direction: Float, action: () -> Unit) {
        scope.launch {
            leave.animateTo(direction, tween(230, easing = FastOutSlowInEasing))
            action()
        }
    }

    // ── Свайпы: вправо — сделал (в «Сделано» — вернуть), влево — на завтра ──
    val threshold = width * 0.3f
    val allowLeft = !task.done
    var armed by remember { mutableStateOf(false) }
    val nowArmed = abs(swipe) > threshold && (swipe > 0 || allowLeft)
    LaunchedEffect(nowArmed) {
        if (nowArmed != armed) {
            armed = nowArmed
            if (nowArmed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    val dragState = rememberDraggableState { d ->
        val resist = if (swipe + d < 0 && !allowLeft) 0.15f else 1f
        swipe += d * resist
    }
    var dragged by remember { mutableStateOf(false) }

    val strike = remember { Animatable(if (task.done) 1f else 0f) }
    LaunchedEffect(done) { strike.animateTo(if (done) 1f else 0f, tween(if (done) 300 else 180, delayMillis = if (done) 140 else 0)) }

    Box(
        modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width }
            .graphicsLayer {
                translationX = leave.value * width * 1.1f
                alpha = 1f - abs(leave.value) * 0.6f
            },
    ) {
        // Подложка, которая открывается при свайпе.
        if (!selecting && swipe != 0f) SwipeUnderlay(swipe, threshold, task.done, Modifier.matchParentSize())

        val src = remember { MutableInteractionSource() }
        val cardBg by animateColorAsState(
            when {
                selected -> lerp(Palette.Card, Palette.Hay, if (Palette.IsDark) 0.15f else 0.25f)
                done -> Palette.CardDone
                else -> Palette.Card
            }, Motion.fade(), label = "cardBg",
        )
        val outline by animateColorAsState(
            if (selected) Palette.Hay else if (task.hot && !done) Palette.Red else Palette.Ink,
            Motion.fade(), label = "cardOutline",
        )
        val shadow by animateDpAsState(if (done) 0.dp else 3.dp + 3.dp * lift, Motion.snappy(), label = "cardShadow")
        val tap = when {
            !selecting -> Modifier.combinedClickable(
                interactionSource = src, indication = null,
                onLongClick = onLongPress,
                onLongClickLabel = if (onLongPress == null) null else "Выделить задачу",
                onClick = { if (!dragged && lift == 0f) actions.onEdit(task) },
            )
            onSelect != null -> Modifier.toggleable(selected, src, indication = null, role = Role.Checkbox) { onSelect() }
            else -> Modifier
        }
        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = swipe
                    rotationZ = swipe / width * 4f
                    val s = 1f + 0.03f * lift
                    scaleX = s; scaleY = s
                }
                .then(dragModifier)
                .draggable(
                    dragState,
                    Orientation.Horizontal,
                    enabled = !selecting,
                    onDragStarted = { dragged = true },
                    onDragStopped = { v ->
                        val right = swipe > threshold || (v > 2200f && swipe > 30f)
                        val left = allowLeft && (swipe < -threshold || (v < -2200f && swipe < -30f))
                        when {
                            right -> {
                                animate(swipe, if (keepInList) 0f else width * 1.15f, animationSpec = tween(180)) { x, _ -> swipe = x }
                                actions.onToggle(task.id)
                            }
                            left -> {
                                animate(swipe, if (keepInList) 0f else -width * 1.15f, animationSpec = tween(180)) { x, _ -> swipe = x }
                                actions.onPostpone(task.id)
                            }
                            else -> animate(swipe, 0f, animationSpec = Motion.snappy()) { x, _ -> swipe = x }
                        }
                        dragged = false
                    },
                )
                .pressScale(src, 0.98f)
                .brutal(
                    cardBg, radius = 14.dp, shadow = shadow,
                    borderColor = outline,
                )
                .then(tap)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                onSelect != null -> CheckBox(selected, forSelection = true)
                selecting -> Spacer(Modifier.width(28.dp))
                else -> CheckBox(done, label = if (task.done) "Вернуть в активные" else "Отметить выполненной") {
                    if (checking) return@CheckBox
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (keepInList) {
                        actions.onToggle(task.id)
                    } else {
                        checking = true
                        scope.launch {
                            delay(if (task.done) 260 else 480)
                            leave.animateTo(if (task.done) -1f else 1f, tween(230, easing = FastOutSlowInEasing))
                            actions.onToggle(task.id)
                        }
                    }
                }
            }

            Column(Modifier.weight(1f)) {
                StrikeText(task.title, strike.value, done)
                if (task.desc.isNotBlank()) {
                    Text(
                        task.desc, fontSize = 13.sp, lineHeight = 17.sp, color = Palette.Mute,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val (label, kind) = Copy.dueBadge(task.due, task.done, today)
                    Badge(label, kind)
                    if (task.hot && !task.done) Badge("🔥 горит", Copy.BadgeKind.Late)
                    task.time?.let { Badge("⏰ " + Copy.fmtTime(it), Copy.BadgeKind.Normal) }
                    if (task.repeat != Repeat.None) Badge("🔁 " + Copy.repeatLabel(task), Copy.BadgeKind.Normal)
                    if (task.checklist.isNotEmpty()) Badge("✓ " + task.checklist.count { it.done } + "/" + task.checklist.size, Copy.BadgeKind.Normal)
                    TagOps.of(task, TaskRepo.tags).forEach { TagBadge(it) }
                }
            }

            if (!selecting) Text(
                "в корзину", fontFamily = Mono, fontSize = 10.5.sp, color = Palette.Faint,
                modifier = Modifier
                    .clickable { commit(-1f) { actions.onDelete(task.id) } }
                    .padding(4.dp),
            )
        }
    }
}

@Composable
private fun SwipeUnderlay(swipe: Float, threshold: Float, done: Boolean, modifier: Modifier) {
    val right = swipe > 0
    val p = (abs(swipe) / threshold).coerceIn(0f, 1f)
    val armed = abs(swipe) > threshold
    val iconScale by animateFloatAsState(if (armed) 1.25f else 0.9f, Motion.bouncy(), label = "uScale")
    val (bg, fg, text) = when {
        right && done -> Triple(Palette.Hay, Palette.OnHay, "↩ Вернуть")
        right -> Triple(Palette.Green, Color.White, "✓ Сделал")
        else -> Triple(Palette.Hay, Palette.OnHay, "Завтра ⏭")
    }
    Box(
        modifier
            .graphicsLayer { alpha = 0.35f + 0.65f * p }
            .background(bg, RoundedCornerShape(14.dp))
            .border(2.5.dp, Palette.Ink, RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp),
        contentAlignment = if (right) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Text(
            text, color = fg, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale },
        )
    }
}

/** Квадратик: галочка прорисовывается штрихом, сам квадрат пружинит. */
@Composable
private fun CheckBox(done: Boolean, forSelection: Boolean = false, label: String = "", onClick: (() -> Unit)? = null) {
    val bg by animateColorAsState(
        if (!done) Palette.Paper else if (forSelection) Palette.Hay else Palette.Green,
        Motion.fade(160), label = "checkBg",
    )
    val draw by animateFloatAsState(if (done) 1f else 0f, tween(if (done) 280 else 150, easing = FastOutSlowInEasing), label = "checkDraw")
    val bounce = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(done) {
        if (first) { first = false; return@LaunchedEffect }
        bounce.animateTo(0.78f, tween(70))
        bounce.animateTo(1f, Motion.bouncy())
    }
    val path = remember { Path() }
    val seg = remember { Path() }
    val measure = remember { PathMeasure() }
    val ink = if (forSelection) Palette.OnHay else Color.White
    val interaction = if (onClick == null) Modifier else Modifier
        .toggleable(done, role = Role.Checkbox) { onClick() }
        .semantics { contentDescription = label }
    Box(
        Modifier
            .size(28.dp)
            .graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }
            .brutal(bg, radius = 8.dp, shadow = 0.dp)
            .then(interaction),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            if (draw <= 0f) return@Canvas
            val s = size.width / 24f
            path.reset()
            path.moveTo(4 * s, 12.5f * s); path.lineTo(9 * s, 17.5f * s); path.lineTo(20 * s, 6 * s)
            measure.setPath(path, false)
            seg.reset()
            measure.getSegment(0f, measure.length * draw, seg, true)
            drawPath(seg, ink, style = Stroke(3.5f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Название, которое зачёркивается линией слева направо, строка за строкой. */
@Composable
private fun StrikeText(text: String, progress: Float, done: Boolean) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val color by animateColorAsState(if (done) Palette.Mute2 else Palette.Ink, Motion.fade(), label = "titleInk")
    val lineColor = Palette.Mute2
    Text(
        text,
        fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp, color = color,
        onTextLayout = { layout = it },
        modifier = Modifier.drawWithContent {
            drawContent()
            val l = layout ?: return@drawWithContent
            if (progress <= 0f) return@drawWithContent
            val widths = (0 until l.lineCount).map { l.getLineRight(it) - l.getLineLeft(it) }
            var left = widths.sum() * progress
            for (i in 0 until l.lineCount) {
                if (left <= 0f) break
                val seg = min(widths[i], left)
                val x = l.getLineLeft(i)
                val y = (l.getLineTop(i) + l.getLineBottom(i)) / 2f
                drawLine(lineColor, Offset(x, y), Offset(x + seg, y), 2.dp.toPx(), cap = StrokeCap.Round)
                left -= seg
            }
        },
    )
}

@Composable
private fun Badge(text: String, kind: Copy.BadgeKind) {
    val (bg, fg) = when (kind) {
        Copy.BadgeKind.Hot -> Palette.Hay to Palette.OnHay
        Copy.BadgeKind.Late -> Palette.Red to Color.White
        Copy.BadgeKind.Normal -> Palette.Paper to Palette.Ink
    }
    Text(
        text, fontFamily = Mono, fontSize = 10.5.sp, color = fg,
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .border(1.5.dp, Palette.Ink, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun Empty(tab: Tab, modifier: Modifier = Modifier) {
    val (emoji, title, sub) = when (tab) {
        Tab.Today -> Triple("😶", "На сегодня ничего. Подозрительно.", "Либо всё сделал, либо всё забыл.")
        Tab.All -> Triple("🏝️", "Задач нет вообще.", "Либо ты гений, либо врёшь.")
        Tab.Done -> Triple("🪦", "Ни одной сделанной. Классика.", "Отметь хоть что-нибудь, конь ждёт.")
    }
    // Эмодзи выпрыгивает и покачивается, текст подтягивается следом.
    val pop = remember { Animatable(0f) }
    val textIn = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { pop.animateTo(1f, Motion.bouncy()) }
        delay(120)
        textIn.animateTo(1f, Motion.smooth())
    }
    Column(
        modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            emoji, fontSize = 44.sp,
            modifier = Modifier.graphicsLayer {
                val s = 0.3f + 0.7f * pop.value
                scaleX = s; scaleY = s
                rotationZ = (1f - pop.value) * -25f
            },
        )
        Column(
            Modifier.graphicsLayer { alpha = textIn.value; translationY = (1f - textIn.value) * 12.dp.toPx() },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            Text(sub, color = Palette.Mute, textAlign = TextAlign.Center)
        }
    }
}
