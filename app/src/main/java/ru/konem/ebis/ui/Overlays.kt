package ru.konem.ebis.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.konem.ebis.R
import ru.konem.ebis.ui.theme.Display
import ru.konem.ebis.ui.theme.Mono
import ru.konem.ebis.ui.theme.Palette

/** «Размечтался»: ржущий конь на выбор даты «никогда». */
@Composable
fun NeighOverlay(visible: Boolean, onDismiss: () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Palette.Scrim)
                .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            val scale = remember { Animatable(0.6f) }
            val rot = remember { Animatable(-8f) }
            LaunchedEffect(Unit) {
                launch { scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
                rot.animateTo(-2f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
            }
            val inf = rememberInfiniteTransition(label = "lol")
            val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "t")

            Column(
                Modifier
                    .graphicsLayer { scaleX = scale.value; scaleY = scale.value; rotationZ = rot.value }
                    .brutal(Palette.Plaque, radius = 24.dp, shadow = 6.dp, border = 3.dp)
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Image(
                    painterResource(R.drawable.horse_lol), null,
                    Modifier
                        .size(170.dp)
                        .graphicsLayer {
                            rotationZ = -6f + 12f * t
                            translationY = -6.dp.toPx() * t
                            transformOrigin = TransformOrigin(0.5f, 0.85f)
                        },
                )
                Text("И-ГО-ГО-ГО-ГО", fontFamily = Mono, fontSize = 11.sp, color = Palette.VersionInk)
                Text(
                    "Размечтался",
                    style = TextStyle(
                        fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 28.sp,
                        color = Palette.Red, letterSpacing = (-0.5).sp,
                        shadow = Shadow(Palette.Shadow, Offset(4f, 4f), 0f),
                    ),
                )
                Text(
                    "«Никогда» — это не дата. Выбирай нормальную.",
                    fontSize = 13.5.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                )
                Text(
                    "Ладно, понял",
                    color = Palette.AccentFg, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .background(Palette.AccentBg, RoundedCornerShape(12.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                )
            }
        }
    }
}

/** Наш собственный вопрос перед системным запросом разрешения на уведомления. */
@Composable
fun PermOverlay(visible: Boolean, onYes: () -> Unit, onNo: () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Palette.Scrim)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            val scale = remember { Animatable(0.7f) }
            LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
            Column(
                Modifier
                    .graphicsLayer { scaleX = scale.value; scaleY = scale.value; rotationZ = 1.5f }
                    .brutal(Palette.Plaque, radius = 24.dp, shadow = 6.dp, border = 3.dp)
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(painterResource(R.drawable.horse_lol), null, Modifier.size(110.dp))
                Text(
                    "Разрешишь коню орать на тебя?",
                    fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 19.sp, textAlign = TextAlign.Center,
                )
                Text(
                    "Утром — сколько херни на сегодня, днём — нытьё, вечером — итог, а за просрочку — позорный столб. Без разрешения конь немой.",
                    fontSize = 13.5.sp, textAlign = TextAlign.Center,
                )
                Text(
                    "Ори, хер с тобой",
                    color = Palette.AccentFg, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .background(Palette.AccentBg, RoundedCornerShape(12.dp))
                        .clickable(onClick = onYes)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
                Text(
                    "Не смей", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Palette.Mute,
                    modifier = Modifier.clickable(onClick = onNo).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Stable
class ToastState {
    var message by mutableStateOf<String?>(null)
        private set
    var action by mutableStateOf<String?>(null)
        private set
    var onAction: (() -> Unit)? = null
        private set
    var key by mutableIntStateOf(0)
        private set
    var visible by mutableStateOf(false)
        internal set

    /** С action тост живёт дольше: чтобы успеть нажать «Верни». */
    fun show(msg: String, action: String? = null, onAction: (() -> Unit)? = null) {
        message = msg
        this.action = action
        this.onAction = onAction
        key++
        visible = true
    }

    fun hide() {
        visible = false
    }
}

@Composable
fun ToastHost(state: ToastState) {
    LaunchedEffect(state.key) {
        if (state.key > 0) {
            delay(if (state.action != null) 4000 else 2000)
            state.visible = false
        }
    }
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 90.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            state.visible,
            enter = fadeIn(tween(180)) + slideInVertically(Motion.bouncy(IntOffset(1, 1))) { it } +
                scaleIn(Motion.bouncy(), initialScale = 0.85f),
            exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 2 } + scaleOut(tween(220), targetScale = 0.9f),
        ) {
            // Новый тост поверх старого: старый уходит вверх, новый выезжает снизу.
            AnimatedContent(
                targetState = Triple(state.key, state.message.orEmpty(), state.action),
                contentKey = { it.first },
                transitionSpec = {
                    (fadeIn(tween(160)) + slideInVertically(Motion.snappy(IntOffset(1, 1))) { it / 2 }) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically(tween(160)) { -it / 2 }) using
                        SizeTransform(clip = false) { _, _ -> Motion.smooth(IntSize(1, 1)) }
                },
                label = "toast",
            ) { (_, msg, action) ->
                Row(
                    Modifier
                        .brutal(Palette.AccentBg, radius = 14.dp, shadow = 3.dp, border = 2.dp, borderColor = Palette.AccentBg)
                        .padding(start = 14.dp, end = if (action != null) 6.dp else 14.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        msg, color = Palette.AccentFg, fontWeight = FontWeight.Bold, fontSize = 13.5.sp,
                        modifier = Modifier.padding(vertical = 4.dp).weight(1f, fill = false),
                    )
                    if (action != null) {
                        val src = remember { MutableInteractionSource() }
                        Text(
                            action, color = Palette.AccentBg, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            modifier = Modifier
                                .pressScale(src, 0.9f)
                                .background(Palette.AccentFg, RoundedCornerShape(10.dp))
                                .clickable(src, null) {
                                    state.onAction?.invoke()
                                    state.hide()
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
