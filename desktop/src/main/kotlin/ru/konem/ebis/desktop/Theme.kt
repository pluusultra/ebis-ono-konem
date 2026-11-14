package ru.konem.ebis.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Density

/** Те же цвета, что в Android-приложении (см. app/.../ui/theme/Theme.kt), плюс то, что нужно фону и шапке на ПК. */
data class Scheme(
    val isDark: Boolean,
    val hay: Color,
    val hay2: Color,
    val onHay: Color,
    val headerSub: Color,
    val headerBtn: Color,
    val paper: Color,
    val ink: Color,
    val shadow: Color,
    val red: Color,
    val green: Color,
    val card: Color,
    val cardDone: Color,
    val mute: Color,
    val mute2: Color,
    val faint: Color,
    val errorBg: Color,
    val accentBg: Color,
    val accentFg: Color,
    val hover: Color,
    /** Узор фона и контур «КОНЁМ» за списком. */
    val dots: Color,
    val watermark: Color,
    /** Мягкое пятно света в углу ленты задач. */
    val glow: Color,
)

val LightScheme = Scheme(
    isDark = false,
    hay = Color(0xFFFFD447), hay2 = Color(0xFFFFC23A), onHay = Color(0xFF1B1310), headerSub = Color(0xFF4A3826), headerBtn = Color(0xFFFFF8E6),
    paper = Color(0xFFFFF8E6), ink = Color(0xFF1B1310), shadow = Color(0xFF1B1310),
    red = Color(0xFFE5383B), green = Color(0xFF2FA866), card = Color(0xFFFFFFFF), cardDone = Color(0xFFF1EBDD),
    mute = Color(0xFF5D4D42), mute2 = Color(0xFF7A6A5D), faint = Color(0xFF85705F), errorBg = Color(0xFFFFECEC),
    accentBg = Color(0xFF1B1310), accentFg = Color(0xFFFFD447), hover = Color(0xFFFFF1C9),
    dots = Color(0x1C1B1310), watermark = Color(0x121B1310), glow = Color(0x59FFD447),
)

/** Ночь в конюшне: чёрный фон, белые рамки, красные тени, сено только в шапке и акцентах. */
val DarkScheme = Scheme(
    isDark = true,
    hay = Color(0xFFFFD447), hay2 = Color(0xFFFFC23A), onHay = Color(0xFF1B1310), headerSub = Color(0xFF4A3826), headerBtn = Color(0xFFFFF8E6),
    paper = Color(0xFF0B0B0B), ink = Color(0xFFFFFFFF), shadow = Color(0xFFE5383B),
    red = Color(0xFFF0484B), green = Color(0xFF2FA866), card = Color(0xFF161616), cardDone = Color(0xFF0F0F0F),
    mute = Color(0xFFB3B3B3), mute2 = Color(0xFF969696), faint = Color(0xFF8F8F8F), errorBg = Color(0xFF2A0E0F),
    accentBg = Color(0xFFFFD447), accentFg = Color(0xFF1B1310), hover = Color(0xFF232323),
    dots = Color(0x17FFFFFF), watermark = Color(0x0FFFFFFF), glow = Color(0x2EF0484B),
)

private fun mix(a: Color, b: Color, t: Float) = lerp(a, b, t)

/** Накладывает оформление на тему: свой фон (текст и карточки подстраиваются), узор, шапку. */
fun Scheme.with(look: Look): Scheme {
    var s = this
    look.bg(isDark)?.let { bg ->
        val dark = bg.luminance() < 0.18f
        val ink = if (dark) Color.White else Color(0xFF1B1310)
        s = s.copy(
            isDark = dark, paper = bg, ink = ink,
            card = if (dark) mix(bg, Color.White, 0.10f) else mix(bg, Color.White, 0.70f),
            cardDone = mix(bg, ink, 0.07f),
            mute = mix(bg, ink, 0.72f), mute2 = mix(bg, ink, 0.60f), faint = mix(bg, ink, 0.56f),
            hover = if (dark) mix(bg, Color.White, 0.16f) else mix(bg, Color(0xFFFFD447), 0.30f),
            shadow = if (dark) s.red else Color(0xFF1B1310),
            glow = if (dark) Color(0x10FFFFFF) else Color(0x80FFFFFF),
            errorBg = mix(bg, s.red, 0.15f),
        )
    }
    val head = look.head
    val darkHead = head.luminance() < 0.25f
    s = s.copy(
        hay = head,
        hay2 = mix(head, if (darkHead) Color.Black else Color(0xFF7A4A00), 0.18f),
        onHay = if (darkHead) Color.White else Color(0xFF1B1310),
        headerSub = if (darkHead) Color.White.copy(alpha = 0.78f) else Color(0xFF4A3826),
        headerBtn = if (darkHead) mix(head, Color.Black, 0.3f) else Color(0xFFFFF8E6),
    )
    // Акцент (выбранная вкладка, тосты, чипы) — цвет шапки на фоне текста или наоборот.
    s = if (darkHead) s.copy(accentBg = head, accentFg = Color.White)
    else if (s.isDark) s.copy(accentBg = head, accentFg = Color(0xFF1B1310))
    else s.copy(accentBg = Color(0xFF1B1310), accentFg = head)
    val dot = look.ink.color ?: s.ink
    return s.copy(dots = dot.copy(alpha = look.strength.alpha), watermark = dot.copy(alpha = look.strength.alpha * 0.6f))
}

object Palette {
    var current by mutableStateOf(LightScheme)
        internal set
    var look by mutableStateOf(Look())
        internal set

    val IsDark get() = current.isDark
    val Hay get() = current.hay
    val Hay2 get() = current.hay2
    val OnHay get() = current.onHay
    val HeaderSub get() = current.headerSub
    val HeaderBtn get() = current.headerBtn
    val Paper get() = current.paper
    val Ink get() = current.ink
    val Shadow get() = current.shadow
    val Red get() = current.red
    val Green get() = current.green
    val Card get() = current.card
    val CardDone get() = current.cardDone
    val Mute get() = current.mute
    val Mute2 get() = current.mute2
    val Faint get() = current.faint
    val ErrorBg get() = current.errorBg
    val AccentBg get() = current.accentBg
    val AccentFg get() = current.accentFg
    val Hover get() = current.hover
    val Dots get() = current.dots
    val Watermark get() = current.watermark
    val Glow get() = current.glow
    val Scrim = Color(0x99000000)
}

/** 0 — как в системе, 1 — день, 2 — ночь. На Linux система тему часто не сообщает, поэтому есть ручной выбор. */
enum class ThemeMode(val label: String) { System("Как в системе"), Light("День"), Dark("Ночь") }

/**
 * Compose Desktop не умеет выбирать толщину у вариативного шрифта, поэтому в resources/fonts лежат
 * статические начертания: свои из app/src/main/res/font (нарезаны fontTools) и Roboto, Montserrat, Nunito с Google Fonts.
 */
private fun font(name: String, w: Int) = Font("fonts/$name-$w.ttf", FontWeight(w))
private fun family(name: String) = FontFamily(font(name, 400), font(name, 500), font(name, 700))

val Display = FontFamily(font("unbounded", 500), font("unbounded", 700), font("unbounded", 900))
val Mono = FontFamily(font("jbmono", 400), font("jbmono", 700))
private val bodies = mapOf(
    BodyFont.Onest to family("onest"), BodyFont.Roboto to family("roboto"),
    BodyFont.Montserrat to family("montserrat"), BodyFont.Nunito to family("nunito"),
)

fun BodyFont.family(): FontFamily = bodies.getValue(this)

/** Шрифт задач и интерфейса — тот, что выбран в «Оформлении». */
val Body: FontFamily get() = Palette.look.font.family()

@Composable
private fun animateScheme(t: Scheme): Scheme {
    val spec = tween<Color>(400)
    @Composable
    fun a(c: Color) = animateColorAsState(c, spec, label = "theme").value
    return Scheme(
        isDark = t.isDark,
        hay = a(t.hay), hay2 = a(t.hay2), onHay = a(t.onHay), headerSub = a(t.headerSub), headerBtn = a(t.headerBtn),
        paper = a(t.paper), ink = a(t.ink), shadow = a(t.shadow), red = a(t.red), green = a(t.green),
        card = a(t.card), cardDone = a(t.cardDone), mute = a(t.mute), mute2 = a(t.mute2), faint = a(t.faint),
        errorBg = a(t.errorBg), accentBg = a(t.accentBg), accentFg = a(t.accentFg), hover = a(t.hover),
        dots = a(t.dots), watermark = a(t.watermark), glow = a(t.glow),
    )
}

/** Ночь ли сейчас по выбранной теме (без учёта своего фона). */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.Dark -> true
    ThemeMode.Light -> false
    ThemeMode.System -> isSystemInDarkTheme()
}

/**
 * Масштаб интерфейса поверх системного. «Авто» подбирает его по размеру экрана: Windows на 2560×1440 часто
 * оставляет 100%, и окно, свёрстанное под 1080p, выглядит крошечным. Считаем по рабочей области в логических
 * пикселях (уже с учётом системного масштаба), так что на 2560×1440 при 125–150% добавки почти нет.
 */
object UiScale {
    /** Подобранный для экрана, где сейчас окно. */
    var auto by mutableStateOf(1f)

    fun of(look: Look): Float = if (look.scale > 0f) look.scale else auto

    fun compute(gc: java.awt.GraphicsConfiguration): Float {
        val a = workArea(gc)
        val s = minOf(a.height / 1000f, a.width / 1600f)
        return (kotlin.math.round(s * 20f) / 20f).coerceIn(1f, 2f)
    }

    fun update(gc: java.awt.GraphicsConfiguration?) {
        gc ?: return
        val s = runCatching { compute(gc) }.getOrNull() ?: return
        if (s != auto) {
            Log.i("look", "Автомасштаб для экрана ${gc.bounds.width}x${gc.bounds.height}: ${(s * 100).toInt()}%")
            auto = s
        }
    }
}

@Composable
fun EbisTheme(mode: ThemeMode, look: Look = Look(), content: @Composable () -> Unit) {
    val dark = mode.isDark()
    if (Palette.look != look) Palette.look = look
    val s = animateScheme((if (dark) DarkScheme else LightScheme).with(look))
    if (Palette.current != s) Palette.current = s
    val base = LocalDensity.current
    MaterialTheme(colorScheme = if (s.isDark) darkColorScheme() else lightColorScheme()) {
        CompositionLocalProvider(
            LocalTextStyle provides TextStyle(fontFamily = look.font.family(), color = s.ink),
            LocalDensity provides Density(base.density * UiScale.of(look), base.fontScale),
        ) {
            content()
        }
    }
}
