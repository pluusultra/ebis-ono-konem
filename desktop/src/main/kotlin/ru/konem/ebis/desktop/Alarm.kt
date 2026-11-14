package ru.konem.ebis.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.konem.ebis.notify.Alarm
import ru.konem.ebis.notify.NotifCopy
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import kotlin.math.log10

/** Что сейчас орёт. [id] < 0 — проверка из настроек. */
data class AlarmRing(val id: Long, val title: String, val at: String, val snoozes: Int, val snoozeMin: Int)

enum class AlarmAnswer { Done, Snooze, Tomorrow }

/**
 * Мелодия будильника по кругу. Встроенные wav лежат в ресурсах (общие с телефоном), mp3 декодирует JLayer:
 * Java сама умеет только wav. Всё тяжёлое — в своём потоке, не в окне.
 */
object AlarmPlayer {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "ebis-alarm").apply { isDaemon = true } }
    @Volatile private var clip: Clip? = null
    @Volatile private var gen = 0

    /** Свой звук: копия выбранного файла рядом с настройками. */
    val customDir get() = File(DesktopStore.defaultFolder(), "alarm")
    fun customFile(): File? = customDir.listFiles()?.firstOrNull { it.name.startsWith("custom.") }

    fun saveCustom(src: File): Boolean = runCatching {
        val ext = src.extension.lowercase()
        require(ext == "mp3" || ext == "wav")
        val bytes = src.readBytes()
        // Сначала убеждаемся, что это вообще играется.
        clipOf(stream(ext, bytes)).close()
        customDir.mkdirs()
        customDir.listFiles()?.filter { it.name.startsWith("custom.") }?.forEach { it.delete() }
        File(customDir, "custom.$ext").writeBytes(bytes)
    }.isSuccess

    fun available(): List<Alarm.Sound> = Alarm.Sound.entries.filter { AlarmPlayer::class.java.getResource("/${it.file}") != null }

    /** Играет по кругу до [stop]. [ramp] — громкость нарастает за 20 секунд. */
    fun start(id: String, ramp: Boolean) {
        val my = ++gen
        worker.execute {
            closeClip()
            val c = runCatching { clipOf(load(id)) }.onFailure { Log.w("alarm", "Звук «$id» не играется", it) }.getOrNull()
                ?: runCatching { clipOf(load(Alarm.DEFAULT_SOUND)) }.onFailure { Log.e("alarm", "И встроенный звук не играется", it) }.getOrNull()
                ?: return@execute
            if (my != gen) { c.close(); return@execute }
            clip = c
            val gain = runCatching { c.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl }.getOrNull()
            fun volume(v: Float) {
                gain ?: return
                gain.value = (20 * log10(v.coerceIn(0.01f, 1f))).coerceIn(gain.minimum, gain.maximum)
            }
            volume(if (ramp) 0.15f else 1f)
            c.loop(Clip.LOOP_CONTINUOUSLY)
            if (ramp) {
                val t0 = System.currentTimeMillis()
                while (my == gen) {
                    val k = ((System.currentTimeMillis() - t0).toFloat() / Alarm.RAMP_MS).coerceIn(0f, 1f)
                    volume(0.15f + 0.85f * k)
                    if (k >= 1f) break
                    Thread.sleep(300)
                }
            }
        }
    }

    fun stop() {
        gen++
        worker.execute { closeClip() }
    }

    /** Прослушать в настройках: тот же id ещё раз — стоп. Возвращает, что сейчас играет. */
    private var previewId: String? = null

    fun togglePreview(id: String): String? {
        val same = previewId == id
        stop()
        previewId = null
        if (same) return null
        start(id, ramp = false)
        previewId = id
        return id
    }

    fun stopPreview() {
        if (previewId != null) stop()
        previewId = null
    }

    private fun closeClip() {
        clip?.runCatching { stop(); close() }
        clip = null
    }

    private fun load(id: String): AudioInputStream {
        if (id == Alarm.CUSTOM) customFile()?.let { return stream(it.extension.lowercase(), it.readBytes()) }
        val s = Alarm.Sound.of(id)?.takeIf { AlarmPlayer::class.java.getResource("/${it.file}") != null } ?: Alarm.Sound.Gallop
        val bytes = AlarmPlayer::class.java.getResourceAsStream("/${s.file}")!!.use { it.readBytes() }
        return stream(s.file.substringAfterLast('.'), bytes)
    }

    private fun stream(ext: String, bytes: ByteArray): AudioInputStream =
        if (ext == "mp3") decodeMp3(bytes) else AudioSystem.getAudioInputStream(BufferedInputStream(ByteArrayInputStream(bytes)))

    /** Clip понимает не всё: 24 бита, float и прочее приводим к обычным 16 битам. */
    private fun clipOf(src: AudioInputStream): Clip {
        val f = src.format
        val pcm = if (f.encoding == AudioFormat.Encoding.PCM_SIGNED && f.sampleSizeInBits == 16) src
        else AudioSystem.getAudioInputStream(AudioFormat(AudioFormat.Encoding.PCM_SIGNED, f.sampleRate, 16, f.channels, f.channels * 2, f.sampleRate, false), src)
        return AudioSystem.getClip().apply { open(pcm) }
    }

    /** mp3 → PCM. Длинные песни режем до полутора минут: по кругу всё равно играет начало. */
    private fun decodeMp3(bytes: ByteArray, maxSec: Int = 90): AudioInputStream {
        val bs = javazoom.jl.decoder.Bitstream(ByteArrayInputStream(bytes))
        val dec = javazoom.jl.decoder.Decoder()
        val out = ByteArrayOutputStream()
        var rate = 44100
        var ch = 2
        while (true) {
            val h = bs.readFrame() ?: break
            val sb = dec.decodeFrame(h, bs) as javazoom.jl.decoder.SampleBuffer
            rate = sb.sampleFrequency
            ch = sb.channelCount
            val buf = sb.buffer
            for (i in 0 until sb.bufferLength) {
                val v = buf[i].toInt()
                out.write(v and 0xff)
                out.write((v shr 8) and 0xff)
            }
            bs.closeFrame()
            if (out.size() > maxSec * rate * ch * 2) break
        }
        bs.close()
        require(out.size() > 0) { "пустой mp3" }
        val data = out.toByteArray()
        return AudioInputStream(ByteArrayInputStream(data), AudioFormat(rate.toFloat(), 16, ch, true, false), (data.size / (2 * ch)).toLong())
    }
}

// Окно тревоги всегда «сенное», в любой теме: узнаётся с одного взгляда.
private val Hay = Color(0xFFFFD447)
private val Hay2 = Color(0xFFFFC23A)
private val OnHay = Color(0xFF1B1310)
private val HaySub = Color(0xFF4A3826)
private val Cream = Color(0xFFFFF8E6)
private val AlarmRed = Color(0xFFE5383B)

/** Содержимое окна тревоги: шапка с конём, задача, подкол и три ответа. */
@Composable
fun AlarmView(r: AlarmRing, onAnswer: (AlarmAnswer) -> Unit) {
    val msg = NotifCopy.alarm(r.at, r.snoozes)
    val shake = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            shake.animateTo(0f, keyframes {
                durationMillis = 550
                -12f at 110
                10f at 220
                -7f at 330
                4f at 440
            })
        }
    }
    Column(Modifier.fillMaxWidth().background(Palette.Paper)) {
        Row(
            Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(0.35f to Hay, 1f to Hay2))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(54.dp).graphicsLayer { rotationZ = shake.value }
                    .brutal(AlarmRed, radius = 15.dp, shadow = 3.dp, borderColor = OnHay, shadowColor = OnHay),
                contentAlignment = Alignment.Center,
            ) { Text("🐴", fontSize = 30.sp) }
            Column {
                Text(msg.title, fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 22.sp, color = OnHay)
                Text("⏰ ${r.at} · будильник", fontFamily = Mono, fontSize = 13.sp, color = HaySub)
            }
        }
        Box(Modifier.fillMaxWidth().height(2.5.dp).background(OnHay))
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(
                Modifier.fillMaxWidth().brutal(Palette.Card, radius = 14.dp, shadow = 3.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(r.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Palette.Ink)
                Text("🔥 горит · ${r.at}", fontFamily = Mono, fontSize = 12.5.sp, color = Palette.Mute)
            }
            Text(msg.text, fontSize = 15.sp, color = Palette.Mute)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AnswerButton("✅ ${NotifCopy.actionDone}", AlarmRed, Color.White, Modifier.weight(1.2f), display = true) { onAnswer(AlarmAnswer.Done) }
                AnswerButton("⏰ ${NotifCopy.alarmSnooze(r.snoozeMin, r.snoozes)}", Palette.Card, Palette.Ink, Modifier.weight(1f)) { onAnswer(AlarmAnswer.Snooze) }
                AnswerButton("📅 Завтра", Palette.Card, Palette.Ink, Modifier.weight(1f)) { onAnswer(AlarmAnswer.Tomorrow) }
            }
        }
    }
}

@Composable
private fun AnswerButton(text: String, bg: Color, fg: Color, modifier: Modifier, display: Boolean = false, onClick: () -> Unit) {
    BrutalButton(onClick, modifier, bg = bg, radius = 13.dp, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)) {
        Text(
            text, color = fg, textAlign = TextAlign.Center, maxLines = 1,
            fontFamily = if (display) Display else Body, fontWeight = FontWeight.Bold, fontSize = if (display) 13.sp else 14.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Ширина окна тревоги. Высота — по содержимому. */
val AlarmWidth = 560.dp
