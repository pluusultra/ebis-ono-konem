package ru.konem.ebis.desktop

import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.drawText
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ru.konem.ebis.data.Repeat
import ru.konem.ebis.data.TagOps
import ru.konem.ebis.data.Task
import ru.konem.ebis.data.toLocalDate
import ru.konem.ebis.ui.Copy
import ru.konem.ebis.ui.QuickParse
import java.io.File
import java.time.LocalDate
import kotlin.math.min

/** Строка списка: заголовок группы или задача. */
private sealed interface Row {
    val key: Any

    data class Header(val label: String, val count: Int) : Row {
        override val key get() = "h_$label"
    }

    data class Item(val task: Task) : Row {
        override val key get() = task.id
    }
}

/** Те же правила, что во вкладках телефона. */
fun filterTasks(tasks: List<Task>, tab: Tab, today: LocalDate, newFirst: Boolean = false): List<Task> = when (tab) {
    Tab.Done -> tasks.filter { it.done }.sortedByDescending { it.doneAt }
    Tab.All -> tasks.filter { !it.done }.sortedWith(compareByDescending<Task> { it.hot }.thenBy { it.due }.thenBy { it.order })
    Tab.Today -> tasks.filter { !it.done && !it.due.isAfter(today) }.sortedWith(
        compareByDescending<Task> { it.hot }.then(if (newFirst) compareByDescending { it.order } else compareBy { it.order })
    )
}

private fun buildRows(tab: Tab, tasks: List<Task>, today: LocalDate, newFirst: Boolean): List<Row> {
    val list = filterTasks(tasks, tab, today, newFirst)
    fun grouped(groups: List<Pair<String, List<Task>>>) = groups.filter { it.second.isNotEmpty() }
        .flatMap { (label, ts) -> listOf(Row.Header(label, ts.size)) + ts.map { Row.Item(it) } }
    return when (tab) {
        Tab.Today -> list.map { Row.Item(it) }
        Tab.All -> {
            val week = today.plusDays(7)
            grouped(
                listOf(
                    "🪦 Просрано" to list.filter { it.due.isBefore(today) },
                    "🔥 Сегодня" to list.filter { it.due == today },
                    "⏭ Завтра" to list.filter { it.due == today.plusDays(1) },
                    "📆 На неделе" to list.filter { it.due.isAfter(today.plusDays(1)) && !it.due.isAfter(week) },
                    "🏔 Потом" to list.filter { it.due.isAfter(week) },
                )
            )
        }
        Tab.Done -> grouped(list.groupBy { it.doneAt.toLocalDate() }.entries.sortedByDescending { it.key }.map { (day, ts) ->
            when (day) {
                today -> "Сегодня"
                today.minusDays(1) -> "Вчера"
                else -> Copy.dayLabel(day, today)
            } to ts
        })
    }
}

/**
 * Всё окно. [frame] — своё окно без системной рамки (null в тестах), [onHide] — крестик в шапке,
 * [pickFolder] и [pickColor] — системные диалоги Swing.
 */
@Composable
fun App(
    state: AppState,
    quickAdd: FocusRequester,
    frame: WindowFrame? = null,
    onHide: () -> Unit = {},
    pickColor: (Color?) -> Color? = { null },
    pickSound: () -> File? = { null },
    reminders: Reminders? = null,
    pickFolder: () -> File?,
) {
    var today by remember { mutableStateOf(LocalDate.now()) }

    // Раз в полторы секунды смотрим, не поменял ли файл телефон; заодно замечаем смену дня.
    // Окно в трее или в фоне — реже: напоминалкам хватит, а диск и процессор отдыхают.
    val focused by androidx.compose.runtime.rememberUpdatedState(androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused)
    LaunchedEffect(state.folder) {
        while (true) {
            delay(if (focused) 1500 else 5000)
            today = LocalDate.now()
            if (state.folder == null) continue
            val ext = withContext(Dispatchers.IO) { state.store.readIfChanged() }
            if (ext != null && state.pull(ext)) state.show("Подтянул свежие задачи с телефона 📲")
        }
    }

    val cover = remember { Animatable(0f) }
    LaunchedEffect(state.pendingLook) {
        val next = state.pendingLook ?: return@LaunchedEffect
        cover.animateTo(1f, tween(110))
        state.changeLook(next)
        // Пара кадров на перекладку под новый масштаб под заливкой, потом она уходит.
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.withFrameNanos { }
        cover.animateTo(0f, tween(220))
        // Сбрасываем только в самом конце: смена ключа отменяет этот эффект, и заливка навсегда закрыла бы окно.
        state.pendingLook = null
    }
    // Спрятаться или выйти — сразу, без затухания. Раньше содержимое гасло в прозрачность, но окно непрозрачное,
    // и под ним Skia рисует белым: окно выцветало в белое. Последний такой кадр DirectX ещё и показывал при
    // следующем появлении из трея — белая вспышка, а потом текст «прыгал» из уменьшенного кадра в нормальный.
    LaunchedEffect(frame?.exit) {
        val then = frame?.exit ?: return@LaunchedEffect
        then()
        frame.exit = null
    }

    val lively = LocalLively.current
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // Любое движение мыши будит украшения; ничего не съедаем — событие идёт дальше как обычно.
            .pointerInput(lively) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        lively.poke()
                    }
                }
            }
            .background(Palette.Paper)
            .then(if (frame != null) Modifier.border(1.5.dp, Palette.OnHay.copy(alpha = 0.85f)) else Modifier),
    ) {
        val w = maxWidth
        val rail = w < 900.dp
        val compact = w < 640.dp
        Column(Modifier.fillMaxSize().entrance(0, dy = 14.dp, scaleFrom = 0.97f, duration = 550)) {
            Header(state, frame, today, w, onHide)
            Row(Modifier.weight(1f)) {
                Sidebar(state, today, rail, compact)
                Box(Modifier.width(2.5.dp).fillMaxHeight().background(Palette.Ink))
                Main(state, today, quickAdd, compact)
            }
        }

        ToastHost(state, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))

        EditOverlay(state, today)
        SettingsOverlay(state, pickFolder, pickColor, reminders, compact = w < 760.dp, pickSound = pickSound)
        Onboarding(state, pickFolder)

        if (cover.value > 0f) Box(Modifier.fillMaxSize().drawBehind { drawRect(Palette.Paper, alpha = cover.value) })
        // Рамки системной нет — тянуть окно за края и углы даём сами, поверх всего.
        if (frame != null) ResizeEdges(frame)
    }
}

// ── Боковая панель ─────────────────────────────────────────────

@Composable
private fun Sidebar(state: AppState, today: LocalDate, rail: Boolean, compact: Boolean) {
    val w by androidx.compose.animation.core.animateDpAsState(if (compact) 72.dp else if (rail) 88.dp else 268.dp, Motion.smooth(), label = "side")
    Column(
        Modifier.width(w).fillMaxHeight().padding(horizontal = if (rail) 14.dp else 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val iconPad by androidx.compose.animation.core.animateDpAsState(if (compact) 10.dp else if (rail) 18.dp else 16.dp, Motion.smooth(), label = "iconPad")
        Tab.entries.forEachIndexed { i, t ->
            TabPill(t, filterTasks(state.tasks, t, today).size, state.tab == t, rail, iconPad, Modifier.entrance(280 + i * 70, dx = (-24).dp, dy = 0.dp)) { state.tab = t }
        }
        Spacer(Modifier.weight(1f))
        SyncStatus(state, rail, Modifier.entrance(600, dy = 0.dp))
    }
}

/** Вкладка-таблетка. Под мышкой только подсвечивается: не прыгает и не показывает подсказок. */
@Composable
private fun TabPill(tab: Tab, count: Int, selected: Boolean, rail: Boolean, iconPad: Dp, modifier: Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val p by animateFloatAsState(if (selected) 1f else 0f, Motion.snappy(), label = "tab")
    val bg by animateColorAsState(if (selected) Palette.AccentBg else if (hovered) Palette.Hover else Palette.Card, tween(160), label = "tabBg")
    val fg by animateColorAsState(if (selected) Palette.AccentFg else Palette.Ink, tween(160), label = "tabFg")
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .graphicsLayer { translationX = 3.dp.toPx() * p; translationY = 3.dp.toPx() * p }
                .brutal(bg, radius = 50.dp, shadow = 3.dp * (1f - p), border = 2.5.dp)
                .clickable(src, null, onClick = onClick)
                .hand()
                .padding(start = iconPad, end = if (rail) 0.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tab.icon, fontSize = 19.sp, textAlign = TextAlign.Center, modifier = Modifier.width(24.dp))
            AnimatedVisibility(
                !rail, Modifier.weight(1f),
                enter = fadeIn(tween(200, delayMillis = 80)) + expandHorizontally(Motion.smooth(), expandFrom = Alignment.Start),
                exit = fadeOut(tween(90)) + shrinkHorizontally(Motion.smooth(), shrinkTowards = Alignment.Start),
            ) {
                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tab.title, fontWeight = FontWeight.Bold, fontSize = 16.5.sp, color = fg, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, modifier = Modifier.weight(1f))
                    CountChip(count, fg)
                }
            }
        }
        AnimatedVisibility(
            rail && count > 0,
            Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp),
            enter = fadeIn(tween(150)) + scaleIn(Motion.bouncy()),
            exit = fadeOut(tween(120)) + scaleOut(tween(150)),
        ) {
            Box(
                Modifier.size(26.dp).background(Palette.Red, CircleShape).border(2.dp, Palette.Ink, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("$count", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White) }
        }
    }
}

@Composable
private fun CountChip(count: Int, fg: Color) {
    Box(Modifier.background(fg.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
        AnimatedContent(count, transitionSpec = { (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut()) }, label = "count") { n ->
            Text("$n", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = fg)
        }
    }
}

@Composable
private fun SyncStatus(state: AppState, rail: Boolean, modifier: Modifier) {
    val folder = state.folder ?: return
    val ok = state.error == null
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(10_000); now = System.currentTimeMillis() } }
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val frameA by animateFloatAsState(if (rail) 0f else if (hovered) 0.9f else 0.3f, tween(200), label = "syncBorder")
    Column(
        modifier
            .fillMaxWidth()
            .dashedBorder(2.dp, Palette.Ink.copy(alpha = frameA), 14.dp)
            .clickable(src, null) { state.openSettings(SettingsPane.Sync) }
            .hand()
            .padding(if (rail) 6.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = if (rail) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(12.dp).background(if (ok) Palette.Green else Palette.Red, CircleShape).border(2.dp, Palette.Ink, CircleShape))
            AnimatedVisibility(!rail, enter = fadeIn(tween(200, delayMillis = 80)) + expandHorizontally(Motion.smooth()), exit = fadeOut(tween(90)) + shrinkHorizontally(Motion.smooth())) {
                Text(if (ok) "Синхронизировано" else "Ошибка записи", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
        AnimatedVisibility(!rail, enter = fadeIn(tween(200, delayMillis = 80)) + expandVertically(Motion.smooth()), exit = fadeOut(tween(90)) + shrinkVertically(Motion.smooth())) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.lastSync > 0) Text(agoLabel(now - state.lastSync), fontSize = 13.sp, color = Palette.Mute)
                Text(
                    shortPath(File(folder, DesktopStore.FILE_NAME)), fontFamily = Mono, fontSize = 11.5.sp, color = Palette.Faint,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                state.error?.let { Text(it, fontSize = 12.5.sp, color = Palette.Red, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

private fun agoLabel(ms: Long): String {
    val s = ms / 1000
    return when {
        s < 15 -> "только что"
        s < 60 -> "меньше минуты назад"
        s < 3600 -> "${s / 60} мин назад"
        else -> "${s / 3600} ч назад"
    }
}

/** Домашняя папка — как «~», чтобы путь влезал. */
private fun shortPath(f: File): String {
    val home = System.getProperty("user.home")
    val p = f.absolutePath
    return if (p.startsWith(home)) "~" + p.removePrefix(home) else p
}

// ── Лента задач ────────────────────────────────────────────────

@Composable
private fun Main(state: AppState, today: LocalDate, quickAdd: FocusRequester, compact: Boolean) {
    val look = Palette.look
    Box(Modifier.fillMaxSize().patternBackground(look.pattern)) {
        Watermark(Modifier.matchParentSize(), compact)
        Column(Modifier.fillMaxSize()) {
            QuickAdd(state, today, quickAdd, compact)
            AnimatedContent(
                state.tab,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically(Motion.snappy()) { 40 }) togetherWith fadeOut(tween(120))
                },
                label = "tabList",
            ) { tab -> TaskList(state, tab, today, compact) }
        }
    }
}

/**
 * Фон ленты: мягкое пятно света в углу и узор из «Оформления». Узор — одна клетка-плитка, которую
 * видеокарта повторяет сама: при изменении размера окна ничего не перерисовывается заново.
 */
fun Modifier.patternBackground(pattern: Pattern, glowRadius: Dp = 520.dp): Modifier = drawWithCache {
    val paper = Palette.Paper
    val glow = Brush.radialGradient(listOf(Palette.Glow, Color.Transparent), center = Offset.Zero, radius = glowRadius.toPx())
    val tile = patternTile(pattern, Palette.Dots, this)
    val brush = tile?.let { androidx.compose.ui.graphics.ShaderBrush(androidx.compose.ui.graphics.ImageShader(it, androidx.compose.ui.graphics.TileMode.Repeated, androidx.compose.ui.graphics.TileMode.Repeated)) }
    onDrawBehind {
        drawRect(paper)
        drawRect(glow)
        if (brush != null) drawRect(brush)
    }
}

private fun patternTile(pattern: Pattern, c: Color, d: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.ImageBitmap? {
    val stepDp = when (pattern) {
        Pattern.Dots -> 24.dp
        Pattern.Grid -> 28.dp
        Pattern.Plus -> 26.dp
        Pattern.Hatch -> 16.dp
        Pattern.None -> return null
    }
    val step = with(d) { stepDp.roundToPx() }.coerceAtLeast(4)
    val img = androidx.compose.ui.graphics.ImageBitmap(step, step)
    val canvas = androidx.compose.ui.graphics.Canvas(img)
    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(d, LayoutDirection.Ltr, canvas, androidx.compose.ui.geometry.Size(step.toFloat(), step.toFloat())) {
        val s = step.toFloat()
        val m = s / 2
        when (pattern) {
            Pattern.Dots -> drawCircle(c, 1.4.dp.toPx(), Offset(m, m))
            Pattern.Grid -> {
                val w = 1.5.dp.toPx()
                drawRect(c, Offset.Zero, androidx.compose.ui.geometry.Size(s, w))
                drawRect(c, Offset(0f, w), androidx.compose.ui.geometry.Size(w, s - w))
            }
            Pattern.Plus -> {
                val arm = 5.dp.toPx()
                val w = 2.dp.toPx()
                drawLine(c, Offset(m - arm, m), Offset(m + arm, m), w, StrokeCap.Round)
                drawLine(c, Offset(m, m - arm), Offset(m, m + arm), w, StrokeCap.Round)
            }
            Pattern.Hatch -> {
                val w = 2.5.dp.toPx()
                for (k in -1..1) drawLine(c, Offset(k * s, s), Offset(k * s + s, 0f), w)
            }
            Pattern.None -> Unit
        }
    }
    return img
}

/** Контур «КОНЁМ» в углу, чтобы лента не была пустой. */
@Composable
private fun Watermark(modifier: Modifier, compact: Boolean) {
    val a by animateFloatAsState(if (compact) 0f else 1f, tween(300), label = "wm")
    // Обведённый текст такого размера дорог каждый кадр — рисуем его один раз в картинку.
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val color = Palette.Watermark
    val img = remember(color, density) {
        val layout = measurer.measure(
            "КОНЁМ",
            TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 150.sp, letterSpacing = (-6).sp, color = color, drawStyle = Stroke(width = 5f)),
            softWrap = false, maxLines = 1, density = density,
        )
        val pad = 8
        val bmp = androidx.compose.ui.graphics.ImageBitmap(layout.size.width + pad * 2, layout.size.height + pad * 2)
        androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
            density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(bmp),
            androidx.compose.ui.geometry.Size(bmp.width.toFloat(), bmp.height.toFloat()),
        ) { drawText(layout, topLeft = Offset(pad.toFloat(), pad.toFloat())) }
        bmp
    }
    if (a == 0f) return
    Box(
        modifier.entrance(500, duration = 1400, dy = 0.dp).drawBehind {
            val pivot = Offset(size.width, size.height)
            rotate(-7f, pivot) {
                drawImage(img, Offset(size.width - img.width + 40.dp.toPx(), size.height - img.height + 30.dp.toPx()), alpha = a)
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickAdd(state: AppState, today: LocalDate, focus: FocusRequester, compact: Boolean) {
    var text by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val parsed = remember(text, today) { QuickParse.parse(text, today) }
    val lift by animateFloatAsState(if (focused) 1f else 0f, Motion.snappy(), label = "qaLift")

    fun submit() {
        if (text.isBlank()) return
        // При включённом фильтре задача сразу получает его теги — иначе она исчезла бы из ленты.
        state.add(parsed.title, parsed.date ?: today, parsed.time, parsed.repeat ?: Repeat.None, parsed.hot, tagIds = state.tagFilter)
        val d = parsed.date ?: today
        if (state.tab == Tab.Today && d.isAfter(today)) state.show("Накинул на «${Copy.dayLabel(d, today).lowercase()}» — смотри во «Вся херня»")
        text = ""
    }

    Column(Modifier.fillMaxWidth().entrance(380).padding(start = if (compact) 14.dp else 24.dp, end = if (compact) 14.dp else 28.dp, top = 20.dp, bottom = 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(62.dp)
                .graphicsLayer { translationX = -1.dp.toPx() * lift; translationY = -1.dp.toPx() * lift }
                .brutal(Palette.Card, radius = 16.dp, shadow = 4.dp + 2.dp * lift)
                .padding(start = 14.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TooltipArea("Полная форма") {
                Box(
                    Modifier.size(36.dp).brutal(Palette.Red, radius = 10.dp, shadow = 0.dp, border = 2.dp)
                        .clickable { state.newTask() }.hand(),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(15.dp)) { plus(Color.White, 3.dp.toPx()) }
                }
            }
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) {
                    Text("Накинуть херню… «завтра в 15:00», «в пт», «срочно»", color = Palette.Faint, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BasicTextField(
                    text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Body, fontSize = 17.sp, color = Palette.Ink),
                    cursorBrush = SolidColor(Palette.Ink),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .onFocusChanged { focused = it.isFocused }
                        .onPreviewKeyEvent {
                            if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (it.key) {
                                Key.Enter, Key.NumPadEnter -> { submit(); true }
                                Key.Escape -> { text = ""; false }
                                else -> false
                            }
                        },
                )
            }
            AnimatedVisibility(!compact, enter = fadeIn(tween(200)) + expandHorizontally(Motion.smooth()), exit = fadeOut(tween(100)) + shrinkHorizontally(Motion.smooth())) {
                Keycaps(state.keys[Action.Quick])
            }
        }
        AnimatedVisibility(parsed.found && text.isNotBlank()) {
            FlowRow(
                Modifier.padding(top = 10.dp, start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Понял так:", fontSize = 14.sp, color = Palette.Mute, modifier = Modifier.align(Alignment.CenterVertically))
                parsed.labels.forEach { Badge(it, Copy.BadgeKind.Hot) }
                Text("Enter — накинуть", fontSize = 14.sp, color = Palette.Faint, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskList(state: AppState, tab: Tab, today: LocalDate, compact: Boolean) {
    val filter = state.tagFilter
    val rows = remember(state.tasks, tab, today, state.newFirst, filter) {
        buildRows(tab, state.tasks.filter { TagOps.matches(it, filter) }, today, state.newFirst)
    }
    val showSort = tab == Tab.Today && filterTasks(state.tasks, tab, today).size > 1
    val listState = rememberLazyListState()
    val side = if (compact) 14.dp else 24.dp
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = side, end = if (compact) 18.dp else 30.dp, top = 10.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "filter") {
                TagFilterBar(state, Modifier.animateItem().entrance(440)) {
                    if (showSort) SortToggle(state.newFirst, state::toggleSort)
                }
            }
            if (rows.isEmpty()) {
                if (filter.isNotEmpty() && filterTasks(state.tasks, tab, today).isNotEmpty()) {
                    item(key = "empty_filter") { FilteredEmpty(state, Modifier.animateItem()) }
                } else {
                    item(key = "empty_$tab") { Empty(tab, Modifier.animateItem()) }
                }
            }
            rows.forEachIndexed { i, row ->
                when (row) {
                    is Row.Header -> stickyHeader(key = row.key) { SectionHeader(row.label, row.count, Modifier.entrance(460 + i * 55)) }
                    is Row.Item -> item(key = row.key) {
                        TaskCard(row.task, today, tab, state, compact, Modifier.animateItem(fadeInSpec = tween(220), fadeOutSpec = tween(160)).entrance(460 + i * 55))
                    }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 8.dp, horizontal = 5.dp))
    }
}

@Composable
private fun SortToggle(newFirst: Boolean, onClick: () -> Unit) {
    val rot by animateFloatAsState(if (newFirst) 0f else 180f, Motion.bouncy(), label = "sortRot")
    BrutalButton(onClick, radius = 50.dp, shadow = 2.dp, border = 2.dp, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)) {
        Text("↓", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.graphicsLayer { rotationZ = rot })
        Text(if (newFirst) "Сначала новые" else "Сначала старые", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false)
    }
}

/** Задачи во вкладке есть, но фильтр по тегам всё спрятал. */
@Composable
private fun FilteredEmpty(state: AppState, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("С такими тегами тут пусто", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        GhostChip("Показать всё") { state.clearTagFilter() }
    }
}

@Composable
private fun SectionHeader(label: String, count: Int, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0.75f to Palette.Paper, 1f to Palette.Paper.copy(alpha = 0f)))
            .padding(top = 10.dp, bottom = 6.dp, start = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text("$count", fontFamily = Mono, fontSize = 13.sp, color = Palette.Mute2)
        Box(Modifier.weight(1f).height(2.dp).background(Palette.Ink.copy(alpha = 0.16f)))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskCard(task: Task, today: LocalDate, tab: Tab, state: AppState, compact: Boolean, modifier: Modifier) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val done = task.done
    val strike = remember { Animatable(if (done) 1f else 0f) }
    LaunchedEffect(done) { strike.animateTo(if (done) 1f else 0f, tween(if (done) 320 else 180)) }
    val bg by animateColorAsState(if (done) Palette.CardDone else Palette.Card, tween(200), label = "cardBg")
    val lift by animateFloatAsState(if (hovered && !done) 1f else 0f, Motion.snappy(), label = "lift")

    Column(
        modifier
            .fillMaxWidth()
            .hoverable(src)
            .graphicsLayer { translationX = -2.dp.toPx() * lift; translationY = -2.dp.toPx() * lift }
            .brutal(bg, radius = 16.dp, shadow = if (done) 0.dp else 4.dp + 2.dp * lift, borderColor = if (task.hot && !done) Palette.Red else Palette.Ink)
            .clickable(src, null) { state.editing = task }
            .hand()
            .padding(start = 18.dp, end = 14.dp, top = 16.dp, bottom = 18.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CheckBox(done) { state.toggle(task.id) }
            Column(Modifier.weight(1f)) {
                StrikeText(task.title, strike.value, done)
                if (task.desc.isNotBlank()) {
                    Text(
                        task.desc, fontSize = 14.5.sp, lineHeight = 19.sp, color = Palette.Mute,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val (label, kind) = Copy.dueBadge(task.due, task.done, today)
                    Badge(label, kind)
                    if (task.hot && !task.done) Badge("🔥 горит", Copy.BadgeKind.Late)
                    task.time?.let { Badge("⏰ " + Copy.fmtTime(it), Copy.BadgeKind.Normal) }
                    if (task.repeat != Repeat.None) Badge("🔁 " + Copy.repeatLabel(task), Copy.BadgeKind.Normal)
                    TagOps.of(task, state.tags).forEach { TagBadge(it) }
                }
            }
            if (!compact) CardActions(task, tab, state, hovered)
        }
        if (compact) Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.CenterEnd) { CardActions(task, tab, state, true) }
    }
}

/** «Завтра» и «нахер» проявляются под мышкой, чтобы список оставался спокойным. */
@Composable
private fun CardActions(task: Task, tab: Tab, state: AppState, hovered: Boolean) {
    val a by animateFloatAsState(if (hovered) 1f else 0.45f, tween(150), label = "actions")
    Row(Modifier.graphicsLayer { alpha = a }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (tab != Tab.Done) CardAction("завтра", { later(it, 1.8.dp.toPx()) }) { state.postpone(task.id) }
        CardAction("нахер", { trash(it, 1.8.dp.toPx()) }, danger = true) { state.delete(task.id) }
    }
}

@Composable
fun CardAction(text: String, icon: (DrawScope.(Color) -> Unit)? = null, danger: Boolean = false, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered) (if (danger) Palette.Red else Palette.Paper) else Color.Transparent, tween(120), label = "caBg")
    val fg by animateColorAsState(if (hovered) (if (danger) Color.White else Palette.Ink) else Palette.Mute, tween(120), label = "caFg")
    val border by animateColorAsState(if (hovered) Palette.Ink else Color.Transparent, tween(120), label = "caBorder")
    Row(
        Modifier
            .height(36.dp)
            .background(bg, RoundedCornerShape(10.dp))
            .border(2.dp, border, RoundedCornerShape(10.dp))
            .clickable(src, null, onClick = onClick)
            .hand()
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Canvas(Modifier.size(15.dp)) { icon(fg) }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = fg)
    }
}

/** Квадратик: галочка прорисовывается штрихом, сам квадрат пружинит. */
@Composable
private fun CheckBox(done: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val bg by animateColorAsState(
        if (done) Palette.Green else if (hovered) androidx.compose.ui.graphics.lerp(Palette.Paper, Palette.Green, 0.25f) else Palette.Paper,
        tween(160), label = "checkBg",
    )
    val draw by animateFloatAsState(if (done) 1f else 0f, tween(if (done) 280 else 150, easing = FastOutSlowInEasing), label = "checkDraw")
    val bounce = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(done) {
        if (first) { first = false; return@LaunchedEffect }
        bounce.animateTo(0.78f, tween(70))
        bounce.animateTo(1f, Motion.bouncy())
    }
    Box(
        Modifier
            .size(32.dp)
            .graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }
            .brutal(bg, radius = 9.dp, shadow = 0.dp)
            .clickable(src, null, onClick = onClick)
            .hand(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(21.dp)) { if (draw > 0f) check(Color.White, 3.4.dp.toPx(), draw) }
    }
}

/** Название, которое зачёркивается линией слева направо. */
@Composable
private fun StrikeText(text: String, progress: Float, done: Boolean) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val color by animateColorAsState(if (done) Palette.Mute2 else Palette.Ink, tween(200), label = "titleInk")
    val lineColor = Palette.Mute2
    Text(
        text,
        fontWeight = FontWeight.Bold, fontSize = 17.5.sp, lineHeight = 23.sp, color = color,
        onTextLayout = { layout = it },
        modifier = Modifier.drawWithContent {
            drawContent()
            val l = layout ?: return@drawWithContent
            if (progress <= 0f) return@drawWithContent
            val widths = (0 until l.lineCount).map { l.getLineRight(it) - l.getLineLeft(it) }
            var left = widths.sum() * progress
            for (i in 0 until l.lineCount) {
                if (left <= 0f) break
                val segW = min(widths[i], left)
                val x = l.getLineLeft(i)
                val y = (l.getLineTop(i) + l.getLineBottom(i)) / 2f
                drawLine(lineColor, Offset(x, y), Offset(x + segW, y), 2.5.dp.toPx(), cap = StrokeCap.Round)
                left -= segW
            }
        },
    )
}

@Composable
private fun Empty(tab: Tab, modifier: Modifier = Modifier) {
    val (emoji, title, sub) = when (tab) {
        Tab.Today -> Triple("😶", "На сегодня ничего. Подозрительно.", "Либо всё сделал, либо всё забыл.")
        Tab.All -> Triple("🏝️", "Задач нет вообще.", "Либо ты гений, либо врёшь.")
        Tab.Done -> Triple("🪦", "Ни одной сделанной. Классика.", "Отметь хоть что-нибудь, конь ждёт.")
    }
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, Motion.bouncy()) }
    Column(modifier.fillMaxWidth().padding(vertical = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            emoji, fontSize = 58.sp,
            modifier = Modifier.graphicsLayer {
                val s = 0.3f + 0.7f * pop.value
                scaleX = s; scaleY = s
                rotationZ = (1f - pop.value) * -25f
            },
        )
        Text(title, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
        Text(sub, fontSize = 15.5.sp, color = Palette.Mute, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ToastHost(state: AppState, modifier: Modifier) {
    val toast = state.toast
    LaunchedEffect(toast?.id) {
        if (toast == null) return@LaunchedEffect
        delay(if (toast.undo != null) 5000 else 3000)
        if (state.toast?.id == toast.id) state.toast = null
    }
    AnimatedVisibility(
        toast != null,
        modifier = modifier,
        enter = fadeIn(tween(150)) + slideInVertically(Motion.bouncy()) { it } + scaleIn(initialScale = 0.9f),
        exit = fadeOut(tween(150)) + slideOutVertically(tween(180)) { it / 2 },
    ) {
        // Пока тост уезжает, держим последний текст, а не пустоту.
        var last by remember { mutableStateOf(toast) }
        if (toast != null) last = toast
        val t = last ?: return@AnimatedVisibility
        Row(
            Modifier.widthIn(max = 620.dp).brutal(Palette.AccentBg, radius = 16.dp, shadow = 4.dp, shadowColor = if (Palette.IsDark) Palette.Red else Palette.Ink)
                .padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(t.text, color = Palette.AccentFg, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            t.undo?.let { undo ->
                Text(
                    Copy.undoLabel, color = Palette.AccentBg, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier
                        .background(Palette.AccentFg, RoundedCornerShape(10.dp))
                        .clickable { state.toast = null; undo() }
                        .hand()
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                )
            } ?: Spacer(Modifier.width(8.dp))
        }
    }
}
