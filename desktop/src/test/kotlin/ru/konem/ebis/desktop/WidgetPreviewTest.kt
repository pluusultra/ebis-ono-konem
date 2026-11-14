package ru.konem.ebis.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import java.io.File

/**
 * Картинки виджетов для списка виджетов в лаунчере (android:previewImage).
 * Живые виджеты рисует Glance на телефоне; здесь — их копия в том же стиле, чтобы в лаунчере было видно, что ставишь.
 * Пересобрать: ./gradlew :desktop:test --tests '*WidgetPreview*'
 */
class WidgetPreviewTest {
    private val out = File(System.getProperty("ebis.projectDir") ?: ".", "../app/src/main/res/drawable-nodpi")

    private fun save(name: String, wDp: Int, hDp: Int, content: @Composable () -> Unit) {
        val scale = 3f
        val scene = ImageComposeScene((wDp * scale).toInt(), (hDp * scale).toInt(), Density(scale)) {
            EbisTheme(ThemeMode.Light) { Box(Modifier.fillMaxSize().padding(1.dp)) { content() } }
        }
        val img = scene.render(0)
        scene.close()
        out.mkdirs()
        File(out, "widget_preview_$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }

    @Composable
    private fun Tag(text: String, red: Boolean = false) {
        Text(
            text, fontFamily = Mono, fontSize = 9.sp, color = if (red) Color.White else Palette.Ink,
            modifier = Modifier.background(if (red) Palette.Red else Palette.Paper, RoundedCornerShape(4.dp))
                .border(1.5.dp, Palette.Ink, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
        )
    }

    @Composable
    private fun Check() {
        Box(Modifier.size(18.dp).background(Palette.Paper, RoundedCornerShape(4.dp)).border(2.dp, Palette.Ink, RoundedCornerShape(4.dp)))
    }

    @Test
    fun render() {
        save("counter", 150, 150) {
            Column(Modifier.fillMaxSize().brutal(Palette.Hay, radius = 16.dp, shadow = 2.dp, border = 2.dp).padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 12.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Image(painterResource("logo.xml"), null, Modifier.size(28.dp))
                    Spacer(Modifier.weight(1f))
                    Tag("−1 протухла", red = true)
                }
                Spacer(Modifier.weight(1f))
                Text("4", fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 38.sp, lineHeight = 40.sp, color = Palette.OnHay)
                Text("херни осталось\nна сегодня", fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 13.sp, color = Palette.HeaderSub)
            }
        }
        save("streak", 150, 150) {
            Column(Modifier.fillMaxSize().brutal(Palette.Paper, radius = 16.dp, shadow = 2.dp, border = 2.dp).padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔥", fontSize = 18.sp)
                    Spacer(Modifier.width(4.dp))
                    Text("5", fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 26.sp)
                    Spacer(Modifier.width(5.dp))
                    Text("дней\nподряд", fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 11.sp, color = Palette.Mute)
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth()) {
                    Text("Закрыл", fontWeight = FontWeight.Bold, fontSize = 10.5.sp, modifier = Modifier.weight(1f))
                    Text("3/7", fontFamily = Mono, fontSize = 10.5.sp)
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(11.dp).background(Palette.Card, RoundedCornerShape(6.dp)).border(1.5.dp, Palette.Ink, RoundedCornerShape(6.dp)).padding(2.dp)) {
                    Box(Modifier.fillMaxWidth(0.43f).fillMaxSize().background(Palette.Green, RoundedCornerShape(3.dp)))
                }
                Spacer(Modifier.height(6.dp))
                Text("Ещё 4 херни — и серия живёт", fontSize = 10.sp, lineHeight = 12.sp, color = Palette.Mute)
            }
        }
        save("today", 320, 150) {
            Column(Modifier.fillMaxSize().brutal(Palette.Paper, radius = 16.dp, shadow = 2.dp, border = 2.dp)) {
                Row(
                    Modifier.fillMaxWidth().background(Palette.Hay).padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Сегодня", fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 12.sp, color = Palette.OnHay)
                    Text("  · 2 из 6", fontFamily = Mono, fontSize = 10.5.sp, color = Palette.HeaderSub)
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.weight(1f).height(10.dp).background(Palette.Card, RoundedCornerShape(6.dp)).border(1.5.dp, Palette.Ink, RoundedCornerShape(6.dp)).padding(2.dp)) {
                        Box(Modifier.fillMaxWidth(0.33f).fillMaxSize().background(Palette.Green, RoundedCornerShape(3.dp)))
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(22.dp).background(Palette.Red, RoundedCornerShape(6.dp)).border(2.dp, Palette.OnHay, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                        Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                Box(Modifier.fillMaxWidth().height(2.dp).background(Palette.Ink))
                Column(Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) {
                    listOf(
                        Triple("Оплатить интернет", "горит", true),
                        Triple("Позвонить маме", "18:00", false),
                        Triple("Разобрать почту", "−2 дн", true),
                        Triple("Скинуть Стасу отчёт", "", false),
                    ).forEach { (title, tag, red) ->
                        val done = tag.isEmpty()
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (done) {
                                Box(Modifier.size(18.dp).background(Palette.Green, RoundedCornerShape(4.dp)).border(2.dp, Palette.Ink, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                                    Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            } else Check()
                            Text(
                                title, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, modifier = Modifier.weight(1f),
                                color = if (done) Palette.Faint else Palette.Ink,
                                textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                            )
                            if (!done) Tag(tag, red)
                        }
                    }
                    Text("✓ сделано сегодня: 2", fontFamily = Mono, fontSize = 10.5.sp, color = Palette.Faint, modifier = Modifier.padding(start = 26.dp, top = 2.dp))
                }
            }
        }
        save("random", 320, 150) {
            Row(
                Modifier.fillMaxSize().brutal(Palette.Paper, radius = 16.dp, shadow = 2.dp, border = 2.dp).padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(painterResource("horse_stare.xml"), null, Modifier.size(96.dp))
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f).fillMaxSize().padding(top = 8.dp)) {
                    Text("А какой смысл, если и так откажешься?", fontFamily = Mono, fontSize = 10.sp, lineHeight = 12.sp, color = Palette.Red)
                    Spacer(Modifier.height(3.dp))
                    Text("Разобрать почту", fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                    Spacer(Modifier.weight(1f))
                    Row {
                        Box(Modifier.size(30.dp).brutal(Palette.Green, radius = 9.dp, shadow = 2.dp, border = 2.dp), contentAlignment = Alignment.Center) {
                            Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Не, давай другое", fontWeight = FontWeight.Bold, fontSize = 11.5.sp,
                            modifier = Modifier.brutal(Palette.Card, radius = 9.dp, shadow = 2.dp, border = 2.dp).padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
        }
        save("hot", 320, 64) {
            Row(
                Modifier.fillMaxSize().brutal(Palette.Card, radius = 16.dp, shadow = 2.dp, border = 2.dp, borderColor = Palette.Red, shadowColor = Palette.Red)
                    .padding(start = 12.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🔥", fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("горит", fontFamily = Mono, fontSize = 9.5.sp, color = Palette.Red)
                    Text("Оплатить интернет", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
                Box(Modifier.size(32.dp).brutal(Palette.Green, radius = 9.dp, shadow = 2.dp, border = 2.dp), contentAlignment = Alignment.Center) {
                    Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(32.dp).brutal(Palette.Hay, radius = 9.dp, shadow = 2.dp, border = 2.dp), contentAlignment = Alignment.Center) {
                    Text("⏭", fontSize = 13.sp)
                }
            }
        }
        save("quick", 320, 64) {
            Row(
                Modifier.fillMaxSize().brutal(Palette.OnHay, radius = 28.dp, shadow = 2.dp, border = 2.dp, borderColor = Palette.OnHay, shadowColor = Palette.Hay)
                    .padding(start = 16.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("+", color = Palette.Red, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Text("Накинуть херню…", color = Palette.Hay, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                Text("🎤", fontSize = 16.sp)
            }
        }
    }
}
