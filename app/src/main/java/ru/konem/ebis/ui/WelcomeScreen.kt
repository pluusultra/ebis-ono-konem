package ru.konem.ebis.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.konem.ebis.R
import ru.konem.ebis.ui.theme.Body
import ru.konem.ebis.ui.theme.Display
import ru.konem.ebis.ui.theme.Mono
import ru.konem.ebis.ui.theme.Palette

@Composable
fun WelcomeScreen(onChoose: (work: Boolean) -> Unit) {
    val inf = rememberInfiniteTransition(label = "peek")
    val bob by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Welcome)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Image(
                painter = painterResource(R.drawable.horse_lol),
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 18.dp)
                    .size(150.dp)
                    .align(Alignment.CenterHorizontally)
                    .graphicsLayer {
                        translationY = -6.dp.toPx() * bob
                        rotationZ = -3f * bob
                    },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Ну здарова, бедолага.",
                fontFamily = Display, fontWeight = FontWeight.Black,
                fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = (-0.6).sp,
            )
            Text(
                "Зачем на этот раз зашёл?",
                fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 17.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Choice(
                emoji = "😔",
                title = buildAnnotatedString {
                    append("по")
                    withStyle(SpanStyle(color = Palette.Red, fontWeight = FontWeight.Black)) { append("РАБ") }
                    append("отать")
                },
                hint = "сделать вид, что всё под контролем",
                onClick = { onChoose(true) },
            )
            Choice(
                emoji = "😭",
                title = buildAnnotatedString { append("поплакать") },
                hint = "посмотреть, сколько всего навалилось",
                onClick = { onChoose(false) },
            )
            Text(
                "ебись оно конём · v0.0.1-нахуя",
                fontFamily = Mono, fontSize = 10.sp, color = Palette.VersionInk,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Choice(
    emoji: String,
    title: androidx.compose.ui.text.AnnotatedString,
    hint: String,
    onClick: () -> Unit,
) {
    BrutalButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(emoji, fontSize = 34.sp)
        Column {
            Text(title, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Palette.Ink)
            Text(hint, fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Palette.Mute)
        }
    }
}
