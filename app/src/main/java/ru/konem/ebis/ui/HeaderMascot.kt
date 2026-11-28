package ru.konem.ebis.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import ru.konem.ebis.R
import ru.konem.ebis.ui.theme.Palette

private enum class Expression { Rest, EyeRoll, Sigh }

@Composable
internal fun HeaderMascot() {
    val lifecycle = LocalLifecycleOwner.current
    val rotation = remember { Animatable(0f) }
    val breath = remember { Animatable(0f) }
    var expression by remember { mutableStateOf(Expression.Rest) }
    LaunchedEffect(lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            expression = Expression.Rest
            rotation.snapTo(0f)
            breath.snapTo(0f)
            while (true) {
                delay(3600)
                expression = Expression.EyeRoll
                rotation.animateTo(0f, keyframes {
                    durationMillis = 680
                    -5f at 200
                    -5f at 450
                })
                expression = Expression.Rest
                delay(3600)
                rotation.animateTo(0f, keyframes {
                    durationMillis = 700
                    -14f at 175
                    10f at 350
                    -6f at 525
                })
                delay(3600)
                breath.animateTo(-1f, tween(260))
                expression = Expression.Sigh
                breath.animateTo(1f, tween(560))
                expression = Expression.Rest
                breath.animateTo(0f, tween(300))
            }
        }
    }

    val shape = RoundedCornerShape(16.dp)
    val logoSize = 60.dp
    Box(Modifier.size(logoSize)) {
        Box(Modifier.matchParentSize().brutal(Palette.HeaderBtn, radius = 16.dp, shadow = 3.dp, border = 0.dp, shadowColor = Palette.OnHay))
        Box(
            Modifier.matchParentSize().clip(shape).border(2.5.dp, Palette.OnHay, shape),
        ) {
            Box(
                Modifier.matchParentSize()
                    .offset(
                        x = logoSize * SleepyIconPlacement.HORIZONTAL_OFFSET,
                        y = logoSize * SleepyIconPlacement.VERTICAL_OFFSET,
                    )
                    .graphicsLayer {
                        rotationZ = rotation.value
                        translationY = 1.8.dp.toPx() * breath.value
                        scaleY = 1f - 0.065f * breath.value
                        transformOrigin = TransformOrigin(0.5f, 0.9f)
                    },
            ) {
                Image(painterResource(R.drawable.mascot_sleepy), null, Modifier.matchParentSize())
                val eyeRoll = painterResource(R.drawable.mascot_sleepy_eye_roll)
                val sigh = painterResource(R.drawable.mascot_sleepy_sigh)
                Crossfade(expression, Modifier.matchParentSize(), animationSpec = tween(160), label = "mascotExpression") { frame ->
                    val painter = when (frame) {
                        Expression.Rest -> null
                        Expression.EyeRoll -> eyeRoll
                        Expression.Sigh -> sigh
                    }
                    if (painter != null) {
                        Image(painter, null, Modifier.fillMaxSize().drawWithContent {
                            clipRect(bottom = size.height * 0.77f) { this@drawWithContent.drawContent() }
                        })
                    }
                }
            }
        }
    }
}
