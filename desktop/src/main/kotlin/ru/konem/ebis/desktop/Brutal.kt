package ru.konem.ebis.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.konem.ebis.ui.Copy

/** Общий ритм движения — те же пружины, что на телефоне. */
object Motion {
    fun <T> snappy() = spring<T>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium)
    fun <T> bouncy() = spring<T>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)
    fun <T> smooth() = spring<T>(dampingRatio = 0.9f, stiffness = 420f)

    /** Выезд с лёгким перелётом, как cubic-bezier(.34, 1.56, .64, 1) в концепте. */
    val BackOut = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
}

/** Необрутализм: толстая рамка и жёсткая тень без размытия. */
fun Modifier.brutal(
    bg: Color,
    radius: Dp = 14.dp,
    shadow: Dp = 3.dp,
    border: Dp = 2.5.dp,
    borderColor: Color = Palette.Ink,
    shadowColor: Color = Palette.Shadow,
): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .drawBehind {
            if (shadow > 0.dp) {
                val s = shadow.toPx()
                drawRoundRect(shadowColor, Offset(s, s), size, CornerRadius(radius.toPx()))
            }
        }
        .clip(shape)
        .background(bg, shape)
        .then(if (border > 0.dp) Modifier.border(border, borderColor, shape) else Modifier)
}

fun Modifier.dashedBorder(width: Dp, color: Color, radius: Dp): Modifier = drawWithContent {
    drawContent()
    val w = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 3, w * 2.5f))),
    )
}

fun Modifier.hand() = pointerHoverIcon(PointerIcon.Hand)

// ── Появление при запуске ──────────────────────────────────────

/** Часы запуска: сколько миллисекунд прошло с момента, когда окно показалось. Перезапускаются из трея. */
class LaunchClock {
    /** Пока никто не запускал — считаем, что всё уже появилось (так рисуют тесты). */
    var ms by mutableFloatStateOf(DONE)
        private set
    var runs by mutableIntStateOf(0)
        private set

    fun restart() {
        ms = 0f
        runs++
    }

    @Composable
    fun Drive() {
        LaunchedEffect(runs) {
            val start = withFrameNanos { it }
            while (ms < DONE) {
                withFrameNanos { ms = (it - start) / 1_000_000f }
            }
        }
    }

    companion object {
        const val DONE = 2200f
    }
}

val LocalLaunch = compositionLocalOf { LaunchClock() }

/**
 * Человек сейчас с окном: трогал мышь или клавиатуру последние [IDLE_MS]. Вечные украшения (сено, конь) крутятся
 * только тогда — иначе окно перерисовывается десятки раз в секунду и ест процессор, даже когда им не пользуются.
 */
class Liveliness {
    @Volatile private var last = System.currentTimeMillis()
    var awake by mutableStateOf(true)
        private set

    /** Было движение или нажатие. Дёшево: на каждое движение мыши пишет только число. */
    fun poke() {
        last = System.currentTimeMillis()
        if (!awake) awake = true
    }

    @Composable
    fun Drive() {
        LaunchedEffect(awake) {
            while (awake) {
                val left = IDLE_MS - (System.currentTimeMillis() - last)
                if (left <= 0) awake = false else kotlinx.coroutines.delay(left)
            }
        }
    }

    companion object {
        const val IDLE_MS = 20_000L
    }
}

val LocalLively = compositionLocalOf { Liveliness() }

/** Окно в фокусе и человек им пользуется — можно анимировать вечные украшения. */
@Composable
fun lively(): Boolean = LocalWindowInfo.current.isWindowFocused && LocalLively.current.awake

/** Элемент въезжает при запуске с задержкой [delay] мс. Читает часы только при отрисовке — без лишних перекомпоновок. */
@Composable
fun Modifier.entrance(delay: Int, dx: Dp = 0.dp, dy: Dp = 16.dp, duration: Int = 480, scaleFrom: Float = 1f): Modifier {
    val clock = LocalLaunch.current
    return graphicsLayer {
        val t = ((clock.ms - delay) / duration).coerceIn(0f, 1f)
        if (clock.ms >= LaunchClock.DONE || t >= 1f) return@graphicsLayer
        val e = Motion.BackOut.transform(t)
        alpha = (t * 2.2f).coerceAtMost(1f)
        translationX = dx.toPx() * (1f - e)
        translationY = dy.toPx() * (1f - e)
        if (scaleFrom != 1f) {
            val sc = scaleFrom + (1f - scaleFrom) * e
            scaleX = sc; scaleY = sc
        }
    }
}

// ── Кнопки ─────────────────────────────────────────────────────

/** Кнопка, которая при нажатии «вдавливается» в свою тень, а под мышкой чуть приподнимается. */
@Composable
fun BrutalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bg: Color = Palette.Card,
    radius: Dp = 14.dp,
    shadow: Dp = 3.dp,
    border: Dp = 2.5.dp,
    borderColor: Color = Palette.Ink,
    shadowColor: Color = Palette.Shadow,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val hovered by src.collectIsHoveredAsState()
    val p by animateFloatAsState(if (pressed) 1f else if (hovered) -0.35f else 0f, Motion.snappy(), label = "press")
    Row(
        modifier = modifier
            .offset(shadow * p, shadow * p)
            .brutal(bg, radius, shadow = shadow * (1f - p), border = border, borderColor = borderColor, shadowColor = shadowColor)
            .clickable(src, indication = null, onClick = onClick)
            .hand()
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** Таблетка-переключатель: тёмная (ночью — цвета шапки), если выбрана. */
@Composable
fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val bg by animateColorAsState(
        when {
            selected -> Palette.AccentBg
            hovered -> Palette.Hover
            else -> Palette.Card
        },
        tween(160), label = "chipBg",
    )
    val fg by animateColorAsState(if (selected) Palette.AccentFg else Palette.Ink, tween(160), label = "chipFg")
    Text(
        text,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = fg,
        modifier = Modifier
            .background(bg, shape)
            .border(2.dp, Palette.Ink, shape)
            .clickable(src, null, onClick = onClick)
            .hand()
            .padding(horizontal = 15.dp, vertical = 8.dp),
    )
}

@Composable
fun Badge(text: String, kind: Copy.BadgeKind) {
    val (bg, fg) = when (kind) {
        Copy.BadgeKind.Hot -> Palette.Hay to Palette.OnHay
        Copy.BadgeKind.Late -> Palette.Red to Color.White
        Copy.BadgeKind.Normal -> Palette.Paper to Palette.Ink
    }
    Text(
        text, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = fg,
        modifier = Modifier
            .background(bg, RoundedCornerShape(7.dp))
            .border(2.dp, Palette.Ink, RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun BrutalSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val bg by animateColorAsState(if (on) Palette.Green else Palette.CardDone, tween(160), label = "swBg")
    val x by animateDpAsState(if (on) 24.dp else 0.dp, Motion.bouncy(), label = "swX")
    Box(
        Modifier
            .width(56.dp)
            .height(32.dp)
            .background(bg, RoundedCornerShape(50))
            .border(2.5.dp, Palette.Ink, RoundedCornerShape(50))
            .clickable(remember { MutableInteractionSource() }, null) { onChange(!on) }
            .hand()
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = x)
                .size(21.dp)
                .background(if (Palette.IsDark && !on) Palette.Ink else Palette.Paper, CircleShape)
                .border(2.dp, Palette.Ink, CircleShape),
        )
    }
}

/** Сочетание клавиш «капсами»: каждая клавиша отдельной кнопкой, чтобы читалось издалека. */
@Composable
fun Keycaps(combo: String, modifier: Modifier = Modifier, small: Boolean = false, onDark: Boolean = false) {
    val keys = if (combo.endsWith("++")) combo.dropLast(2).split('+') + "+" else combo.split('+')
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        keys.forEachIndexed { i, k ->
            if (i > 0) Text("+", fontFamily = Mono, fontSize = if (small) 11.sp else 12.sp, color = if (onDark) Color.White.copy(alpha = 0.7f) else Palette.Mute)
            val shape = RoundedCornerShape(if (small) 6.dp else 7.dp)
            Text(
                k, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = if (small) 11.5.sp else 13.sp,
                color = if (onDark) Color.White else Palette.Ink,
                modifier = Modifier
                    .drawBehind { if (!onDark) drawRoundRect(Palette.Ink, Offset(0f, 2.dp.toPx()), size, CornerRadius(7.dp.toPx())) }
                    .background(if (onDark) Color.White.copy(alpha = 0.18f) else Palette.Paper, shape)
                    .border(2.dp, if (onDark) Color.White.copy(alpha = 0.6f) else Palette.Ink, shape)
                    .padding(horizontal = if (small) 6.dp else 8.dp, vertical = if (small) 2.dp else 4.dp),
            )
        }
    }
}

/** Крестик закрытия диалога: заметный в обеих темах, под мышкой краснеет и поворачивается. */
@Composable
fun CloseButton(onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val pressed by src.collectIsPressedAsState()
    val bg by animateColorAsState(if (hovered) Palette.Red else Palette.Card, tween(150), label = "xBg")
    val fg by animateColorAsState(if (hovered) Color.White else Palette.Ink, tween(150), label = "xFg")
    val rot by animateFloatAsState(if (hovered) 90f else 0f, Motion.snappy(), label = "xRot")
    val p by animateFloatAsState(if (pressed) 1f else 0f, Motion.snappy(), label = "xPress")
    Box(
        Modifier
            .size(46.dp)
            .offset(3.dp * p, 3.dp * p)
            .brutal(bg, radius = 13.dp, shadow = 3.dp * (1f - p))
            .clickable(src, null, onClick = onClick)
            .hand(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp).graphicsLayer { rotationZ = rot }) { cross(fg, 2.6.dp.toPx()) }
    }
}

// ── Значки, нарисованные линиями (без шрифтов и картинок) ───────

fun DrawScope.cross(c: Color, w: Float) {
    val a = size.width * 0.18f
    val b = size.width * 0.82f
    drawLine(c, Offset(a, a), Offset(b, b), w, StrokeCap.Round)
    drawLine(c, Offset(b, a), Offset(a, b), w, StrokeCap.Round)
}

fun DrawScope.plus(c: Color, w: Float) {
    val m = size.width / 2
    drawLine(c, Offset(m, size.height * 0.15f), Offset(m, size.height * 0.85f), w, StrokeCap.Round)
    drawLine(c, Offset(size.width * 0.15f, m), Offset(size.width * 0.85f, m), w, StrokeCap.Round)
}

fun DrawScope.check(c: Color, w: Float, progress: Float = 1f, path: Path = Path()) {
    val s = size.width / 24f
    path.reset()
    path.moveTo(4.5f * s, 12.5f * s); path.lineTo(9.5f * s, 17.5f * s); path.lineTo(19.5f * s, 6.5f * s)
    if (progress >= 1f) {
        drawPath(path, c, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        return
    }
    val m = androidx.compose.ui.graphics.PathMeasure()
    m.setPath(path, false)
    val seg = Path()
    m.getSegment(0f, m.length * progress, seg, true)
    drawPath(seg, c, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** «→|» — перенести на завтра. */
fun DrawScope.later(c: Color, w: Float) {
    val s = size.width / 16f
    drawLine(c, Offset(2 * s, 8 * s), Offset(11 * s, 8 * s), w, StrokeCap.Round)
    drawLine(c, Offset(8 * s, 4.5f * s), Offset(11.5f * s, 8 * s), w, StrokeCap.Round)
    drawLine(c, Offset(8 * s, 11.5f * s), Offset(11.5f * s, 8 * s), w, StrokeCap.Round)
    drawLine(c, Offset(14 * s, 3 * s), Offset(14 * s, 13 * s), w, StrokeCap.Round)
}

/** Мусорка — «нахер». */
fun DrawScope.trash(c: Color, w: Float) {
    val s = size.width / 16f
    val st = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawLine(c, Offset(2.5f * s, 4.5f * s), Offset(13.5f * s, 4.5f * s), w, StrokeCap.Round)
    val lid = Path().apply { moveTo(6 * s, 4.5f * s); lineTo(6 * s, 3 * s); lineTo(10 * s, 3 * s); lineTo(10 * s, 4.5f * s) }
    drawPath(lid, c, style = st)
    val body = Path().apply { moveTo(4 * s, 4.5f * s); lineTo(4.7f * s, 13.5f * s); lineTo(11.3f * s, 13.5f * s); lineTo(12 * s, 4.5f * s) }
    drawPath(body, c, style = st)
}

fun DrawScope.sun(c: Color, fill: Color, w: Float) {
    val ctr = center
    val r = size.width * 0.19f
    drawCircle(fill, r, ctr)
    drawCircle(c, r, ctr, style = Stroke(w))
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0)
        val d1 = size.width * 0.32f
        val d2 = size.width * 0.45f
        drawLine(c, ctr + Offset((Math.cos(a) * d1).toFloat(), (Math.sin(a) * d1).toFloat()), ctr + Offset((Math.cos(a) * d2).toFloat(), (Math.sin(a) * d2).toFloat()), w, StrokeCap.Round)
    }
}

fun DrawScope.moon(c: Color, w: Float) {
    val s = size.width / 24f
    val p = Path().apply {
        moveTo(20 * s, 14.5f * s)
        cubicTo(17.5f * s, 19.5f * s, 10.5f * s, 21.5f * s, 6.5f * s, 18f * s)
        cubicTo(2.5f * s, 14.5f * s, 2.5f * s, 7.5f * s, 9.5f * s, 4f * s)
        cubicTo(7.5f * s, 9f * s, 11f * s, 16.5f * s, 20 * s, 14.5f * s)
        close()
    }
    drawPath(p, c.copy(alpha = 0.15f))
    drawPath(p, c, style = Stroke(w, join = StrokeJoin.Round))
}
