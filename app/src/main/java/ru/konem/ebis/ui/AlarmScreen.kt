package ru.konem.ebis.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.konem.ebis.notify.AlarmService
import ru.konem.ebis.notify.NotifCopy
import ru.konem.ebis.ui.theme.Display
import ru.konem.ebis.ui.theme.Mono

// Экран тревоги всегда «сенной», в любой теме: так он узнаётся с одного взгляда.
private val Hay = Color(0xFFFFD447)
private val OnHay = Color(0xFF1B1310)
private val HaySub = Color(0xFF4A3826)
private val Cream = Color(0xFFFFF8E6)
private val Red = Color(0xFFE5383B)

@Composable
fun AlarmScreen(r: AlarmService.Ringing, onAnswer: (AlarmService.Answer) -> Unit) {
    val msg = NotifCopy.alarm(r.at, r.snoozes)
    val shake = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            shake.animateTo(0f, keyframes {
                durationMillis = 550
                -10f at 110
                9f at 220
                -6f at 330
                4f at 440
            })
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Hay)
            .drawBehind {
                // Косая штриховка, как тюк сена в шапке приложения.
                val step = 18.dp.toPx()
                var x = -size.height
                while (x < size.width) {
                    drawLine(OnHay.copy(alpha = 0.06f), Offset(x, size.height), Offset(x + size.height * 0.7f, 0f), 2.dp.toPx())
                    x += step
                }
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("⏰ ${r.at}", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = HaySub, modifier = Modifier.padding(top = 18.dp))
        Box(
            Modifier
                .padding(top = 8.dp)
                .size(140.dp)
                .graphicsLayer { rotationZ = shake.value }
                .drawBehind { drawCircle(OnHay, radius = size.minDimension / 2, center = center + Offset(5.dp.toPx(), 5.dp.toPx())) }
                .background(Red, CircleShape)
                .border(3.dp, OnHay, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("🐴", fontSize = 72.sp) }
        Text(msg.title, fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 36.sp, lineHeight = 38.sp, color = OnHay, textAlign = TextAlign.Center)
        Text(msg.text, fontSize = 16.sp, color = HaySub, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRoundRect(OnHay, Offset(3.dp.toPx(), 3.dp.toPx()), size, androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()))
                }
                .background(Cream, RoundedCornerShape(16.dp))
                .border(2.5.dp, OnHay, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(r.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OnHay)
            Text("🔥 горит · ${r.at}", fontFamily = Mono, fontSize = 12.sp, color = HaySub)
        }

        Spacer(Modifier.weight(1f))

        AlarmButton("✅ ${NotifCopy.alarmDone}", Red, Color.White, big = true) { onAnswer(AlarmService.Answer.Done) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AlarmButton("⏰ ${NotifCopy.alarmSnooze(r.snoozeMin, r.snoozes)}", Cream, OnHay, Modifier.weight(1f)) { onAnswer(AlarmService.Answer.Snooze) }
            AlarmButton("📅 ${NotifCopy.actionTomorrow}", Cream, OnHay, Modifier.weight(1f)) { onAnswer(AlarmService.Answer.Tomorrow) }
        }
    }
}

/** Кнопка со сдвинутой тенью, всегда в «сенных» цветах (BrutalButton берёт рамку из темы, а тут тема не важна). */
@Composable
private fun AlarmButton(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier.fillMaxWidth(), big: Boolean = false, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val shift = if (pressed) 3.dp else 0.dp
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .offset(shift, shift)
            .drawBehind { if (!pressed) drawRoundRect(OnHay, Offset(4.dp.toPx(), 4.dp.toPx()), size, androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())) }
            .background(bg, shape)
            .border(3.dp, OnHay, shape)
            .clickable(src, null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = if (big) 18.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = fg, textAlign = TextAlign.Center,
            fontFamily = if (big) Display else null,
            fontWeight = FontWeight.Bold, fontSize = if (big) 16.sp else 14.5.sp,
        )
    }
}
