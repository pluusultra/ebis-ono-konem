package ru.konem.ebis.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.awt.ComposeWindow
import ru.konem.ebis.data.DaySnapshot
import ru.konem.ebis.ui.Copy
import java.awt.Cursor
import java.awt.Frame
import java.awt.MouseInfo
import java.awt.Rectangle
import java.awt.Dimension
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.time.LocalDate
import java.util.prefs.Preferences
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Размер окна при первом запуске на масштабе 100%; дальше — какой человек сам растянул. */
val DefaultWindowSize = DpSize(1100.dp, 760.dp)

/** Меньше — вёрстка уже не помещается даже в узком режиме. */
val MinWindowSize = DpSize(560.dp, 460.dp)

/** Рабочая область экрана (без панели задач) в тех же единицах, что и размер окна. */
fun workArea(gc: GraphicsConfiguration): Rectangle {
    val b = gc.bounds
    val i = Toolkit.getDefaultToolkit().getScreenInsets(gc)
    return Rectangle(b.x + i.left, b.y + i.top, b.width - i.left - i.right, b.height - i.top - i.bottom)
}

private fun defaultScreen(): GraphicsConfiguration =
    GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration

/**
 * Окно без системной рамки: перетаскивание за шапку, растягивание за края, двойной клик по шапке — во весь экран,
 * свернуть, спрятать в трей. Положение, размер и «во весь экран» запоминаются.
 */
class WindowFrame(val window: ComposeWindow, private val prefs: Preferences) {
    /** Что сделать, когда содержимое доиграет анимацию исчезновения (свернуть, спрятать, выйти). */
    var exit by mutableStateOf<(() -> Unit)?>(null)

    val maximized get() = window.extendedState and Frame.MAXIMIZED_BOTH == Frame.MAXIMIZED_BOTH

    /** То же, но для Compose: по нему прячутся ручки растягивания. */
    var isMaximized by mutableStateOf(false)
        private set

    /** Сколько раз окно возвращалось на экран (из трея или из свёрнутого) — для журнала первых кадров. */
    var shows by mutableStateOf(0)
        private set

    private fun states(s: Int) = buildList {
        if (s and Frame.ICONIFIED != 0) add("свёрнуто")
        if (s and Frame.MAXIMIZED_BOTH == Frame.MAXIMIZED_BOTH) add("развёрнуто")
        if (isEmpty()) add("обычное")
    }.joinToString("+")

    private fun awt(why: String) =
        WinDebug.log("AWT $why: ${window.width}x${window.height} @${window.x},${window.y}, ${states(window.extendedState)}")

    init {
        window.addWindowStateListener { e ->
            isMaximized = maximized
            WinDebug.mark()
            Log.i("win", "Состояние: ${states(e.oldState)} → ${states(e.newState)}; окно ${window.width}x${window.height} @${window.x},${window.y}")
        }
        window.addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent?) = awt("размер")
            override fun componentMoved(e: java.awt.event.ComponentEvent?) = awt("сдвиг")
            override fun componentShown(e: java.awt.event.ComponentEvent?) { WinDebug.mark(); WinDebug.log("AWT показано") }
            override fun componentHidden(e: java.awt.event.ComponentEvent?) { WinDebug.mark(); WinDebug.log("AWT спрятано") }
        })
        window.addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowIconified(e: java.awt.event.WindowEvent?) { WinDebug.mark(); WinDebug.log("событие: свернули") }
            override fun windowDeiconified(e: java.awt.event.WindowEvent?) { WinDebug.mark(); WinDebug.log("событие: развернули") }
            override fun windowActivated(e: java.awt.event.WindowEvent?) = WinDebug.log("событие: активно")
            override fun windowDeactivated(e: java.awt.event.WindowEvent?) = WinDebug.log("событие: неактивно")
        })
    }

    /** Окно с прошлого раза; если тот экран отключили — по центру текущего. Размер уже выставлен из [initialSize]. */
    fun restoreBounds() {
        window.minimumSize = Dimension(MinWindowSize.width.value.toInt(), MinWindowSize.height.value.toInt())
        val x = prefs.getInt("win.x", Int.MIN_VALUE)
        val y = prefs.getInt("win.y", Int.MIN_VALUE)
        val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration }
        val home = screens.firstOrNull { x != Int.MIN_VALUE && workArea(it).intersects(Rectangle(x, y, window.width, window.height)) }
        val area = workArea(home ?: window.graphicsConfiguration)
        // Сохранённый размер мог остаться от большого монитора — не даём окну вылезти за экран.
        val w = window.width.coerceAtMost(area.width)
        val h = window.height.coerceAtMost(area.height)
        if (home == null) {
            window.setBounds(area.x + (area.width - w) / 2, area.y + (area.height - h) / 2, w, h)
        } else {
            window.setBounds(x.coerceIn(area.x - w + 120, area.x + area.width - 120), y.coerceIn(area.y, area.y + area.height - 60), w, h)
        }
        if (prefs.getBoolean("win.max", false)) maximize()
    }

    /** Свернуть сразу, без затухания. */
    fun minimize() {
        WinDebug.mark()
        WinDebug.log("Кнопка «Свернуть»")
        window.extendedState = window.extendedState or Frame.ICONIFIED
    }

    /** Во весь экран, но без панели задач: у окна без рамки Windows иначе закрывает и её. */
    private fun maximize() {
        val a = workArea(window.graphicsConfiguration)
        // maximizedBounds считается от левого верхнего угла своего экрана.
        val b = window.graphicsConfiguration.bounds
        window.maximizedBounds = Rectangle(a.x - b.x, a.y - b.y, a.width, a.height)
        window.extendedState = window.extendedState or Frame.MAXIMIZED_BOTH
    }

    fun toggleMaximize() {
        if (maximized) window.extendedState = window.extendedState and Frame.MAXIMIZED_BOTH.inv() else maximize()
        prefs.putBoolean("win.max", maximized)
        runCatching { prefs.flush() }
    }

    fun save() {
        prefs.putBoolean("win.max", maximized)
        // Во весь экран — помним обычные размеры, чтобы было куда вернуться.
        if (!maximized && window.extendedState and Frame.ICONIFIED == 0) {
            prefs.putInt("win.x", window.x)
            prefs.putInt("win.y", window.y)
            prefs.putInt("win.w", window.width)
            prefs.putInt("win.h", window.height)
        }
        runCatching { prefs.flush() }
    }

    /** Окно снова на экране — просим перерисовать (без смены размера: она перекладывала бы вёрстку). */
    fun refresh() {
        WinDebug.mark()
        shows++
        javax.swing.SwingUtilities.invokeLater {
            if (window.isShowing && window.extendedState and Frame.ICONIFIED == 0) window.repaint()
        }
    }

    /** Масштаб интерфейса поменялся — окно растёт или сжимается вместе с ним, оставаясь на месте. */
    fun scaleBy(k: Float) {
        if (maximized || k == 1f) return
        val a = workArea(window.graphicsConfiguration)
        val min = window.minimumSize
        val w = (window.width * k).toInt().coerceIn(min.width, a.width)
        val h = (window.height * k).toInt().coerceIn(min.height, a.height)
        val cx = window.x + window.width / 2
        val cy = window.y + window.height / 2
        window.setBounds((cx - w / 2).coerceIn(a.x, a.x + a.width - w), (cy - h / 2).coerceIn(a.y, a.y + a.height - h), w, h)
        save()
    }

    /** Тащим окно за шапку. Развёрнутое сначала возвращается к обычному размеру и едет под мышкой. */
    fun dragStart(): (Int, Int) -> Unit {
        val m0 = MouseInfo.getPointerInfo().location
        var origin = window.location
        var unmax = maximized
        return { mx, my ->
            if (unmax) {
                unmax = false
                val wasW = window.width
                window.extendedState = window.extendedState and Frame.MAXIMIZED_BOTH.inv()
                val w = prefs.getInt("win.w", window.width)
                origin = java.awt.Point(m0.x - ((m0.x - window.x).toFloat() / wasW * w).toInt(), m0.y - 20)
                window.setSize(w, prefs.getInt("win.h", window.height))
            }
            window.setLocation(origin.x + mx - m0.x, origin.y + my - m0.y)
        }
    }

    /** Тянем за край или угол: противоположная сторона стоит на месте. */
    fun resizeStart(e: Edge): (Int, Int) -> Unit {
        val m0 = MouseInfo.getPointerInfo().location
        val b = window.bounds
        val min = window.minimumSize
        return { mx, my ->
            val dx = mx - m0.x
            val dy = my - m0.y
            var x = b.x
            var y = b.y
            var w = b.width
            var h = b.height
            if (e.right) w = maxOf(min.width, b.width + dx)
            if (e.left) { w = maxOf(min.width, b.width - dx); x = b.x + b.width - w }
            if (e.bottom) h = maxOf(min.height, b.height + dy)
            if (e.top) { h = maxOf(min.height, b.height - dy); y = b.y + b.height - h }
            window.setBounds(x, y, w, h)
        }
    }

    companion object {
        /** Размер на старте: прошлый, а в первый раз — стандартный, увеличенный под масштаб и не больше экрана. */
        fun initialSize(prefs: Preferences, scale: Float): DpSize {
            val a = workArea(defaultScreen())
            val w = prefs.getInt("win.w", (DefaultWindowSize.width.value * scale).toInt())
            val h = prefs.getInt("win.h", (DefaultWindowSize.height.value * scale).toInt())
            return DpSize(
                w.coerceIn(MinWindowSize.width.value.toInt(), maxOf(a.width, MinWindowSize.width.value.toInt())).dp,
                h.coerceIn(MinWindowSize.height.value.toInt(), maxOf(a.height, MinWindowSize.height.value.toInt())).dp,
            )
        }
    }
}

/** Край или угол окна, за который тянут. */
enum class Edge(val left: Boolean, val top: Boolean, val right: Boolean, val bottom: Boolean, val cursor: Int) {
    N(false, true, false, false, Cursor.N_RESIZE_CURSOR),
    S(false, false, false, true, Cursor.S_RESIZE_CURSOR),
    W(true, false, false, false, Cursor.W_RESIZE_CURSOR),
    E(false, false, true, false, Cursor.E_RESIZE_CURSOR),
    NW(true, true, false, false, Cursor.NW_RESIZE_CURSOR),
    NE(false, true, true, false, Cursor.NE_RESIZE_CURSOR),
    SW(true, false, false, true, Cursor.SW_RESIZE_CURSOR),
    SE(false, false, true, true, Cursor.SE_RESIZE_CURSOR),
}

/** Невидимые полоски по краям и уголки — за них окно растягивается. */
@Composable
fun BoxScope.ResizeEdges(frame: WindowFrame) {
    if (frame.isMaximized) return
    val t = 5.dp
    val c = 12.dp
    @Composable
    fun Handle(e: Edge, m: Modifier) = Box(
        m.pointerHoverIcon(PointerIcon(Cursor(e.cursor)))
            .screenDrag({ frame.resizeStart(e) }, onEnd = frame::save, threshold = 0),
    )
    Handle(Edge.N, Modifier.align(Alignment.TopCenter).fillMaxWidth().height(t))
    Handle(Edge.S, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(t))
    Handle(Edge.W, Modifier.align(Alignment.CenterStart).fillMaxHeight().width(t))
    Handle(Edge.E, Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(t))
    Handle(Edge.NW, Modifier.align(Alignment.TopStart).size(c))
    Handle(Edge.NE, Modifier.align(Alignment.TopEnd).size(c))
    Handle(Edge.SW, Modifier.align(Alignment.BottomStart).size(c))
    Handle(Edge.SE, Modifier.align(Alignment.BottomEnd).size(c))
}

/**
 * Жест «держу и тащу» в экранных координатах; кнопки внутри съедают нажатие сами.
 * Два щелчка подряд без сдвига — [onDoubleClick].
 */
private fun Modifier.screenDrag(
    start: () -> ((Int, Int) -> Unit),
    onEnd: () -> Unit = {},
    threshold: Int = 3,
    onDoubleClick: (() -> Unit)? = null,
): Modifier = pointerInput(Unit) {
    var lastTap = 0L
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        val m0 = MouseInfo.getPointerInfo().location
        var move: ((Int, Int) -> Unit)? = null
        var clean = true
        while (true) {
            val ev = awaitPointerEvent()
            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
            if (!ch.pressed) {
                if (ch.isConsumed) clean = false
                break
            }
            val m = MouseInfo.getPointerInfo().location
            if (move == null && (abs(m.x - m0.x) > threshold || abs(m.y - m0.y) > threshold)) move = start()
            move?.let {
                it(m.x, m.y)
                ch.consume()
            }
        }
        if (move != null) {
            onEnd()
            lastTap = 0L
        } else if (clean && onDoubleClick != null) {
            val now = System.currentTimeMillis()
            if (now - lastTap < 450) {
                lastTap = 0L
                onDoubleClick()
            } else {
                lastTap = now
            }
        }
    }
}

// ── Шапка ──────────────────────────────────────────────────────

@Composable
fun Header(state: AppState, frame: WindowFrame?, today: LocalDate, width: Dp, onHide: () -> Unit) {
    val snap = remember(state.tasks, today) { DaySnapshot(state.tasks, today) }
    val compact = width < 640.dp
    var mouse by remember { mutableStateOf<Offset?>(null) }
    Box(
        Modifier
            .fillMaxWidth()
            .entrance(60, dy = (-90).dp, duration = 600)
            .background(Brush.horizontalGradient(0f to Palette.Hay, 0.35f to Palette.Hay, 1f to Palette.Hay2))
            .drawBehind {
                // Косая штриховка, как тюк сена: проступает к правому краю.
                val step = 20.dp.toPx()
                val ink = Palette.OnHay
                var x = -size.height
                while (x < size.width) {
                    val a = ((x - size.width * 0.2f) / (size.width * 0.5f)).coerceIn(0f, 1f) * 0.05f
                    if (a > 0f) drawLine(ink.copy(alpha = a), Offset(x, size.height), Offset(x + size.height * 0.7f, 0f), 2.dp.toPx())
                    x += step
                }
                val w = 2.5.dp.toPx()
                drawLine(Palette.OnHay, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
            }
            .clipToBounds()
            .then(if (frame != null) Modifier.screenDrag({ frame.dragStart() }, onEnd = frame::save, onDoubleClick = frame::toggleMaximize) else Modifier)
            .onMouse { mouse = it },
    ) {
        val alive = lively()
        HayParticles(Modifier.matchParentSize().graphicsLayer(), paused = !alive || state.settingsOpen || state.editing != null) { mouse }
        val logo by animateDpAsState(if (compact) 46.dp else 58.dp, Motion.smooth(), label = "logo")
        val titleSize by animateFloatAsState(if (compact) 20f else 27f, Motion.smooth(), label = "title")
        val pad by animateDpAsState(if (compact) 10.dp else 14.dp, Motion.smooth(), label = "hpad")
        Row(
            Modifier.fillMaxWidth().padding(start = pad + 4.dp, end = 12.dp, top = pad, bottom = pad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.entrance(250, dy = 0.dp, scaleFrom = 0f, duration = 650)
                    .size(logo)
                    .brutal(Palette.HeaderBtn, radius = 16.dp, shadow = 3.dp, borderColor = Palette.OnHay, shadowColor = Palette.OnHay),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource("logo.xml"), null, Modifier.size(logo * 0.76f))
            }
            Spacer(Modifier.width(pad + 2.dp))
            Column(Modifier.widthIn(max = width * 0.42f).entrance(320)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ебись оно", fontFamily = Display, fontWeight = FontWeight.Black, fontSize = titleSize.sp, letterSpacing = (-0.6).sp, color = Palette.OnHay, maxLines = 1)
                    HorseEmoji(titleSize * 1.18f, alive)
                }
                AnimatedContent(
                    Copy.subtitle(snap.remaining.size),
                    transitionSpec = {
                        (fadeIn(tween(200)) + slideInVertically(Motion.snappy()) { it / 2 }) togetherWith
                            (fadeOut(tween(120)) + slideOutVertically(tween(160)) { -it / 2 })
                    },
                    label = "subtitle",
                ) { s ->
                    Text(s, fontWeight = FontWeight.Medium, fontSize = (titleSize * 0.52f).sp, color = Palette.HeaderSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // Карточка дня в середине; в узком окне плавно уходит, а место остаётся пружиной до кнопок.
            Box(Modifier.weight(1f).padding(horizontal = 12.dp).entrance(380), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(
                    width >= 900.dp,
                    enter = fadeIn(tween(220)) + scaleIn(Motion.snappy(), initialScale = 0.85f),
                    exit = fadeOut(tween(150)) + scaleOut(tween(180), targetScale = 0.85f),
                ) { DayCard(snap, wide = width >= 1080.dp) }
            }
            Row(Modifier.entrance(420), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.animation.AnimatedVisibility(
                    !compact,
                    enter = fadeIn(tween(200)) + expandHorizontally(Motion.smooth()) + scaleIn(Motion.snappy(), initialScale = 0.6f),
                    exit = fadeOut(tween(120)) + shrinkHorizontally(Motion.smooth()) + scaleOut(tween(150), targetScale = 0.6f),
                ) {
                    Row {
                        HeaderButton(onClick = { state.chooseTheme(if (Palette.IsDark) ThemeMode.Light else ThemeMode.Dark) }) {
                            Canvas(Modifier.size(24.dp)) { if (Palette.IsDark) sun(Palette.OnHay, Color(0xFFFFD447), 2.4.dp.toPx()) else moon(Palette.OnHay, 2.4.dp.toPx()) }
                        }
                        Spacer(Modifier.width(10.dp))
                    }
                }
                var turns by remember { mutableStateOf(0) }
                val rot by animateFloatAsState(turns * 90f, Motion.bouncy(), label = "gear")
                HeaderButton(onClick = { turns++; state.openSettings() }) {
                    Image(
                        painterResource("ic_gear.xml"), "Настройки",
                        colorFilter = ColorFilter.tint(Palette.OnHay),
                        modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = rot },
                    )
                }
                if (frame != null) {
                    Box(Modifier.padding(horizontal = if (compact) 8.dp else 14.dp).width(2.5.dp).height(34.dp).background(Palette.OnHay.copy(alpha = 0.25f), RoundedCornerShape(2.dp)))
                    WindowControls(frame, state.notif.tray, onHide)
                }
            }
        }
    }
}

/** Свернуть и закрыть: плоские, проявляются под мышкой; закрытие краснеет. */
@Composable
private fun WindowControls(frame: WindowFrame, tray: Boolean, onHide: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        WindowButton("Свернуть", onClick = frame::minimize) { drawLine(it, Offset(size.width * 0.18f, size.height * 0.5f), Offset(size.width * 0.82f, size.height * 0.5f), 2.4.dp.toPx(), StrokeCap.Round) }
        val max = frame.isMaximized
        WindowButton(if (max) "Восстановить" else "Во весь экран", onClick = frame::toggleMaximize) { c ->
            val sw = 2.2.dp.toPx()
            val r = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            val st = Stroke(sw)
            if (max) {
                // Два окна друг за другом: заднее выглядывает справа сверху.
                val w = size.width - sw
                val h = size.height - sw
                val o = sw / 2
                val s = w * 0.7f
                // Переднее окно — слева снизу, целиком.
                drawRoundRect(c, Offset(o, o + h - s), androidx.compose.ui.geometry.Size(s, s), r, style = st)
                // Заднее — только то, что не закрыто передним: верх и правый край.
                val bx = o + w - s
                drawLine(c, Offset(bx, o + h - s), Offset(bx, o), sw, StrokeCap.Round)
                drawLine(c, Offset(bx, o), Offset(o + w, o), sw, StrokeCap.Round)
                drawLine(c, Offset(o + w, o), Offset(o + w, o + s), sw, StrokeCap.Round)
                drawLine(c, Offset(o + w, o + s), Offset(o + s, o + s), sw, StrokeCap.Round)
            } else {
                drawRoundRect(c, Offset(sw / 2, sw / 2), androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw), r, style = st)
            }
        }
        WindowButton(if (tray) "Спрятать в трей" else "Закрыть", danger = true, onClick = onHide) { cross(it, 2.4.dp.toPx()) }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.onMouse(set: (Offset?) -> Unit) =
    onPointerEvent(PointerEventType.Move) { set(it.changes.first().position) }
        .onPointerEvent(PointerEventType.Exit) { set(null) }

/** Сколько закрыто сегодня — чтобы шапка не пустовала между названием и кнопками. */
@Composable
private fun DayCard(s: DaySnapshot, wide: Boolean) {
    val total = s.doneToday + s.remaining.size
    val p by animateFloatAsState(if (total == 0) 0f else s.doneToday.toFloat() / total, Motion.bouncy(), label = "day")
    Row(
        Modifier
            .background(Palette.HeaderBtn.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .border(2.dp, Palette.OnHay, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text("СЕГОДНЯ ЗАКРЫТО", fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.2.sp, color = Palette.HeaderSub)
            Text("${s.doneToday} из $total", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Palette.OnHay)
        }
        Box(
            Modifier.width(if (wide) 140.dp else 100.dp).height(14.dp)
                .background(Palette.HeaderBtn, RoundedCornerShape(50))
                .border(2.dp, Palette.OnHay, RoundedCornerShape(50))
                .padding(2.dp),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(p.coerceIn(0f, 1f)).background(Palette.Green, RoundedCornerShape(50)))
        }
        if (wide && s.overdue.isNotEmpty()) {
            Text("🪦 ${s.overdue.size} ${Copy.plural(s.overdue.size, "протухла", "протухли", "протухли")}", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Palette.OnHay)
        }
    }
}

/** Квадратная кнопка шапки с жёсткой тенью. */
@Composable
private fun HeaderButton(onClick: () -> Unit, size: Dp = 46.dp, content: @Composable () -> Unit) {
    BrutalButton(
        onClick = onClick,
        bg = Palette.HeaderBtn,
        radius = 13.dp,
        borderColor = Palette.OnHay,
        shadowColor = Palette.OnHay,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        modifier = Modifier.size(size),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

/** Кнопка окна: без фона, под мышкой — подложка с рамкой, у закрытия — красная. */
@Composable
private fun WindowButton(hint: String, danger: Boolean = false, onClick: () -> Unit, icon: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val pressed by src.collectIsPressedAsState()
    val bg by animateColorAsState(
        when {
            hovered && danger -> Color(0xFFE5383B)
            hovered -> Palette.HeaderBtn.copy(alpha = 0.75f)
            else -> Color.Transparent
        },
        tween(120), label = "wcBg",
    )
    val fg by animateColorAsState(if (hovered && danger) Color.White else Palette.OnHay, tween(120), label = "wcFg")
    val border by animateColorAsState(if (hovered) Palette.OnHay else Color.Transparent, tween(120), label = "wcBorder")
    val sc by animateFloatAsState(if (pressed) 0.88f else 1f, Motion.snappy(), label = "wcPress")
    TooltipArea(hint) {
        Box(
            Modifier
                .size(42.dp)
                .graphicsLayer { scaleX = sc; scaleY = sc }
                .background(bg, RoundedCornerShape(11.dp))
                .border(2.dp, border, RoundedCornerShape(11.dp))
                .clickable(src, null, onClick = onClick)
                .hand(),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(15.dp)) { icon(fg) }
        }
    }
}

/** Подсказка под мышкой — короткая, с задержкой, как у системных кнопок. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TooltipArea(text: String, content: @Composable () -> Unit) {
    androidx.compose.foundation.TooltipArea(
        tooltip = {
            Text(
                text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Palette.AccentFg,
                modifier = Modifier.background(Palette.AccentBg, RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 5.dp),
            )
        },
        delayMillis = 500,
        tooltipPlacement = androidx.compose.foundation.TooltipPlacement.CursorPoint(offset = androidx.compose.ui.unit.DpOffset(0.dp, 20.dp)),
    ) { content() }
}

/** 🐴 раз в 3.5 секунды дёргает головой — пока окном пользуются. */
@Composable
private fun HorseEmoji(size: Float, alive: Boolean) {
    val rot = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(alive) {
        if (!alive) {
            rot.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            kotlinx.coroutines.delay(2800)
            rot.animateTo(0f, keyframes {
                durationMillis = 700
                -14f at 175
                10f at 350
                -6f at 525
            })
        }
    }
    Text(
        "🐴", fontSize = size.sp, lineHeight = size.sp,
        modifier = Modifier.padding(start = 8.dp).graphicsLayer {
            rotationZ = rot.value
            transformOrigin = TransformOrigin(0.5f, 0.9f)
        },
    )
}

/** Сено летит справа налево и разлетается от мышки. Гаснет слева, чтобы не лезть под название. */
@Composable
private fun HayParticles(modifier: Modifier, paused: Boolean, mouse: () -> Offset?) {
    class P(var x: Float, var y: Float, val vx: Float, var a: Float, val va: Float, val r: Float, val straw: Boolean, val ph: Float, val o: Float) {
        var dx = 0f
        var dy = 0f
    }
    val parts = remember { mutableListOf<P>() }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(30)
            withFrameMillis { tick = it }
        }
    }
    fun spawn(w: Float, h: Float, anywhere: Boolean): P {
        val straw = Random.nextFloat() < 0.55f
        return P(
            x = if (anywhere) Random.nextFloat() * w else w + 20f, y = Random.nextFloat() * h,
            vx = -(0.3f + Random.nextFloat() * 0.9f), a = Random.nextFloat() * 3.14f, va = (Random.nextFloat() - 0.5f) * 0.04f,
            r = if (straw) 5f + Random.nextFloat() * 9f else 1f + Random.nextFloat() * 2.2f, straw = straw,
            ph = Random.nextFloat() * 6f, o = 0.12f + Random.nextFloat() * 0.28f,
        )
    }
    Canvas(modifier) {
        val t = tick
        val w = size.width
        val h = size.height
        val n = (w / 16.dp.toPx() * 1.2f).toInt()
        while (parts.size < n) parts.add(spawn(w, h, true))
        while (parts.size > n) parts.removeAt(parts.lastIndex)
        val m = mouse()
        val reach = 80.dp.toPx()
        val ink = Palette.OnHay
        for (p in parts) {
            p.x += p.vx * density / 1.5f
            p.a += p.va
            p.y += sin(t / 900f + p.ph) * 0.3f
            if (m != null) {
                val ddx = p.x - m.x
                val ddy = p.y - m.y
                val d = kotlin.math.sqrt(ddx * ddx + ddy * ddy)
                if (d < reach && d > 0.1f) {
                    val f = (1 - d / reach) * 4.5f
                    p.dx += ddx / d * f; p.dy += ddy / d * f
                }
            }
            p.dx *= 0.92f; p.dy *= 0.92f
            if (p.x < -30f) { val q = spawn(w, h, false); p.x = q.x; p.y = q.y }
            val x = p.x + p.dx
            val y = p.y + p.dy
            val fade = ((x - w * 0.28f) / (w * 0.25f)).coerceIn(0f, 1f)
            if (fade <= 0f) continue
            val c = ink.copy(alpha = p.o * fade)
            if (p.straw) {
                val ox = cos(p.a) * p.r * density / 1.5f
                val oy = sin(p.a) * p.r * density / 1.5f
                drawLine(c, Offset(x - ox, y - oy), Offset(x + ox, y + oy), 1.6.dp.toPx(), StrokeCap.Round)
            } else {
                drawCircle(c, p.r * density / 1.5f, Offset(x, y))
            }
        }
    }
}
