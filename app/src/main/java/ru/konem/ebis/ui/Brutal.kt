package ru.konem.ebis.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ru.konem.ebis.ui.theme.Display
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.konem.ebis.ui.theme.Palette

/** Необрутализм: толстая чёрная рамка и жёсткая тень без размытия. */
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

/** Кнопка, которая при нажатии «вдавливается» в свою тень. */
@Composable
fun BrutalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bg: Color = Palette.Paper,
    radius: Dp = 18.dp,
    shadow: Dp = 5.dp,
    shadowColor: Color = Palette.Shadow,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    horizontalArrangement: androidx.compose.foundation.layout.Arrangement.Horizontal =
        androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    // Вдавливание пружинит, а не прыгает: сдвиг и тень меняются вместе.
    val p by animateFloatAsState(if (pressed) 1f else 0f, Motion.snappy(), label = "press")
    val shift = (shadow - 1.dp) * p
    Row(
        modifier = modifier
            .offset(shift, shift)
            .brutal(bg, radius, shadow = shadow - (shadow - 1.dp) * p, border = 3.dp, shadowColor = shadowColor)
            .clickable(src, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = horizontalArrangement,
        content = content,
    )
}

/**
 * Жёлтая шапка экрана. В тёмной теме полоса под статус-баром остаётся чёрной,
 * чтобы светлые системные иконки не терялись на сене.
 */
@Composable
fun HayBar(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(if (Palette.IsDark) Palette.Paper else Palette.Hay)
            .statusBarsPadding()
            .drawBehind {
                drawRect(Palette.Hay)
                val w = 3.dp.toPx()
                drawLine(Palette.OnHay, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
            }
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(color = Palette.OnHay)) {
            content()
        }
    }
}

/** Квадратная кнопка в шапке: ⚙️, ✕ и т.п. Пружинит при нажатии. */
@Composable
fun HeaderButton(onClick: () -> Unit, size: Dp = 38.dp, content: @Composable () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, Motion.snappy(), label = "hbScale")
    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .brutal(Palette.HeaderBtn, radius = 11.dp, shadow = 0.dp, borderColor = Palette.OnHay)
            .clickable(src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(color = Palette.OnHay)) {
            content()
        }
    }
}

/** Плавно «вдавливается» при нажатии. */
fun Modifier.pressScale(src: MutableInteractionSource, pressed: Float = 0.97f): Modifier = composed {
    val isPressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (isPressed) pressed else 1f, Motion.snappy(), label = "press")
    graphicsLayer { scaleX = s; scaleY = s }
}

/** Таблетка-переключатель: чёрная (в ночи — жёлтая), если выбрана. */
@Composable
fun SelectChip(text: String, selected: Boolean, dashed: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val src = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) Palette.AccentBg else Palette.Card, Motion.fade(), label = "chipBg")
    val fg by animateColorAsState(if (selected) Palette.AccentFg else Palette.Ink, Motion.fade(), label = "chipFg")
    Text(
        text,
        fontWeight = FontWeight.Bold,
        fontSize = 13.5.sp,
        color = fg,
        modifier = Modifier
            .pressScale(src, 0.93f)
            .background(bg, shape)
            .then(if (dashed) Modifier.dashedBorder(2.5.dp, Palette.Ink, 50.dp) else Modifier.border(2.5.dp, Palette.Ink, shape))
            .clickable(src, null, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(title: String, initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.brutal(Palette.Paper, radius = 24.dp, shadow = 6.dp, border = 3.dp).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 18.sp)
            TimePicker(state = state)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Отмена", fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 14.dp, vertical = 9.dp),
                )
                Text(
                    "Вот так", color = Palette.AccentFg, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Palette.AccentBg, RoundedCornerShape(12.dp))
                        .clickable { onPick(state.hour * 60 + state.minute) }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                )
            }
        }
    }
}

@Composable
fun BrutalSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val bg by animateColorAsState(if (on) Palette.Green else Palette.CardDone, Motion.fade(), label = "swBg")
    val x by animateDpAsState(if (on) 24.dp else 0.dp, Motion.snappy(Dp.VisibilityThreshold), label = "swX")
    // Кружок чуть сплющивается в полёте — как будто его реально тащат.
    val squash by animateFloatAsState(if (on) 1f else 0f, Motion.bouncy(), label = "swSq")
    Box(
        Modifier
            .width(56.dp)
            .height(32.dp)
            .background(bg, RoundedCornerShape(50))
            .border(2.5.dp, Palette.Ink, RoundedCornerShape(50))
            .clickable(remember { MutableInteractionSource() }, null) { onChange(!on) }
            .padding(4.dp),
    ) {
        val mid = 1f - kotlin.math.abs(squash - 0.5f) * 2f
        Box(
            Modifier
                .offset(x = x)
                .size(24.dp)
                .graphicsLayer { scaleX = 1f + 0.18f * mid; scaleY = 1f - 0.12f * mid }
                .background(if (Palette.IsDark && !on) Palette.Ink else Palette.Paper, CircleShape)
                .border(2.dp, Palette.Ink, CircleShape),
        )
    }
}
