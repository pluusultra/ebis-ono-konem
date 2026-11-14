@file:OptIn(ExperimentalTextApi::class)

package ru.konem.ebis.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import ru.konem.ebis.R

/**
 * Набор цветов одной темы. Имена — по роли:
 * Ink — текст и рамки, Paper — фон экрана, Card — карточки, Hay — фирменная жёлтая шапка (на ней OnHay),
 * Plaque — плашки-попапы и приветствие, Accent — выбранные чипы, тосты и главные кнопки,
 * Shadow — жёсткая тень необрутализма.
 */
data class Scheme(
    val isDark: Boolean,
    val hay: Color,
    val onHay: Color,
    val headerSub: Color,
    val headerBtn: Color,
    val paper: Color,
    val ink: Color,
    val shadow: Color,
    val chestnut: Color,
    val red: Color,
    val green: Color,
    val card: Color,
    val cardDone: Color,
    val mute: Color,
    val mute2: Color,
    val faint: Color,
    val banner: Color,
    val errorBg: Color,
    val versionInk: Color,
    val plaque: Color,
    val welcome: Color,
    val accentBg: Color,
    val accentFg: Color,
)

val LightScheme = Scheme(
    isDark = false,
    hay = Color(0xFFFFD447),
    onHay = Color(0xFF1B1310),
    headerSub = Color(0xFF4A3826),
    headerBtn = Color(0xFFFFF8E6),
    paper = Color(0xFFFFF8E6),
    ink = Color(0xFF1B1310),
    shadow = Color(0xFF1B1310),
    chestnut = Color(0xFFB4582A),
    red = Color(0xFFE5383B),
    green = Color(0xFF2FA866),
    card = Color(0xFFFFFFFF),
    cardDone = Color(0xFFF1EBDD),
    mute = Color(0xFF5D4D42),
    mute2 = Color(0xFF7A6A5D),
    faint = Color(0xFF85705F),
    banner = Color(0xFFDCEFF8),
    errorBg = Color(0xFFFFECEC),
    versionInk = Color(0xFF7A5A20),
    plaque = Color(0xFFFFD447),
    welcome = Color(0xFFFFD447),
    accentBg = Color(0xFF1B1310),
    accentFg = Color(0xFFFFD447),
)

/** AMOLED: чистый чёрный, белые рамки, красные тени, а сено — только в шапке и акцентах. */
val DarkScheme = Scheme(
    isDark = true,
    hay = Color(0xFFFFD447),
    onHay = Color(0xFF1B1310),
    headerSub = Color(0xFF4A3826),
    headerBtn = Color(0xFFFFF8E6),
    paper = Color(0xFF000000),
    ink = Color(0xFFFFFFFF),
    shadow = Color(0xFFE5383B),
    chestnut = Color(0xFFE5383B),
    red = Color(0xFFF0484B),
    green = Color(0xFF2FA866),
    card = Color(0xFF141414),
    cardDone = Color(0xFF0A0A0A),
    mute = Color(0xFFA3A3A3),
    mute2 = Color(0xFF8C8C8C),
    faint = Color(0xFF8C8C8C),
    banner = Color(0xFF0F1A22),
    errorBg = Color(0xFF2A0E0F),
    versionInk = Color(0xFFA3A3A3),
    plaque = Color(0xFF141414),
    welcome = Color(0xFF000000),
    accentBg = Color(0xFFFFD447),
    accentFg = Color(0xFF1B1310),
)

/** Читается и в композиции, и в отрисовке: смена темы сразу перекрашивает всё. */
object Palette {
    var current by mutableStateOf(LightScheme)
        internal set

    val IsDark get() = current.isDark
    val Hay get() = current.hay
    val OnHay get() = current.onHay
    val HeaderBtn get() = current.headerBtn
    val Paper get() = current.paper
    val Ink get() = current.ink
    val Shadow get() = current.shadow
    val Chestnut get() = current.chestnut
    val Red get() = current.red
    val Green get() = current.green
    val Card get() = current.card
    val CardDone get() = current.cardDone
    val Mute get() = current.mute
    val Mute2 get() = current.mute2
    val Faint get() = current.faint
    val HeaderSub get() = current.headerSub
    val Banner get() = current.banner
    val ErrorBg get() = current.errorBg
    val VersionInk get() = current.versionInk
    val Plaque get() = current.plaque
    val Welcome get() = current.welcome
    val AccentBg get() = current.accentBg
    val AccentFg get() = current.accentFg
    val Scrim = Color(0x8C000000)
}

/** Смена темы перетекает цветами, а не перекрашивает всё скачком. */
@Composable
private fun animateScheme(t: Scheme): Scheme {
    val spec = tween<Color>(450)
    @Composable
    fun a(c: Color) = animateColorAsState(c, spec, label = "theme").value
    return Scheme(
        isDark = t.isDark,
        hay = a(t.hay), onHay = a(t.onHay), headerSub = a(t.headerSub), headerBtn = a(t.headerBtn),
        paper = a(t.paper), ink = a(t.ink), shadow = a(t.shadow), chestnut = a(t.chestnut),
        red = a(t.red), green = a(t.green), card = a(t.card), cardDone = a(t.cardDone),
        mute = a(t.mute), mute2 = a(t.mute2), faint = a(t.faint), banner = a(t.banner),
        errorBg = a(t.errorBg), versionInk = a(t.versionInk), plaque = a(t.plaque), welcome = a(t.welcome),
        accentBg = a(t.accentBg), accentFg = a(t.accentFg),
    )
}

/** Выбор темы: 0 — как в системе, 1 — день, 2 — ночь. */
object ThemeMode {
    const val SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2

    var mode by mutableIntStateOf(SYSTEM)
}

private fun varFont(res: Int, w: Int) =
    Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val Display = FontFamily(varFont(R.font.unbounded, 500), varFont(R.font.unbounded, 700), varFont(R.font.unbounded, 900))
val Body = FontFamily(varFont(R.font.onest, 400), varFont(R.font.onest, 500), varFont(R.font.onest, 700))
val Mono = FontFamily(varFont(R.font.jbmono, 500))

/** paintWindow = false — для прозрачного окна поверх рабочего стола (быстрое добавление с виджета). */
@Composable
fun EbisTheme(paintWindow: Boolean = true, content: @Composable () -> Unit) {
    val dark = when (ThemeMode.mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    val s = animateScheme(if (dark) DarkScheme else LightScheme)
    // Сравнение по значению: пока анимация стоит, лишних перерисовок нет.
    if (Palette.current != s) Palette.current = s

    // Иконки статус-бара и навигации: тёмные на светлом, светлые на тёмном.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            if (paintWindow) window.decorView.setBackgroundColor(s.paper.toArgb())
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    val scheme = if (dark) {
        darkColorScheme(
            primary = s.hay, onPrimary = s.onHay,
            primaryContainer = s.hay, onPrimaryContainer = s.onHay,
            secondaryContainer = s.hay, onSecondaryContainer = s.onHay,
            tertiaryContainer = s.hay, onTertiaryContainer = s.onHay,
            background = s.paper, onBackground = s.ink,
            surface = s.card, onSurface = s.ink,
            surfaceContainerHigh = s.card, surfaceContainerHighest = s.cardDone,
            onSurfaceVariant = s.mute, outline = s.ink,
        )
    } else {
        lightColorScheme(
            primary = s.ink, onPrimary = s.hay,
            primaryContainer = s.hay, onPrimaryContainer = s.onHay,
            secondaryContainer = s.hay, onSecondaryContainer = s.onHay,
            tertiaryContainer = s.hay, onTertiaryContainer = s.onHay,
            background = s.paper, onBackground = s.ink,
            surface = s.paper, onSurface = s.ink,
            surfaceContainerHigh = s.paper, surfaceContainerHighest = s.cardDone,
            onSurfaceVariant = s.mute, outline = s.ink,
        )
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalTextStyle provides TextStyle(fontFamily = Body, color = s.ink),
            content = content,
        )
    }
}
