package ru.konem.ebis.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image as SkImage
import org.junit.Test
import ru.konem.ebis.data.Repeat
import ru.konem.ebis.data.Task
import ru.konem.ebis.data.TaskData
import ru.konem.ebis.data.TaskJson
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.time.LocalDate
import java.util.prefs.Preferences

/** Рисует окно и иконки в файлы, не открывая окна: так можно глянуть на интерфейс и пересобрать иконки. */
class RenderTest {
    private val projectDir = File(System.getProperty("ebis.projectDir") ?: ".")

    private fun render(width: Int, height: Int, scale: Float = 1f, frames: Int = 40, content: @androidx.compose.runtime.Composable () -> Unit): SkImage {
        val scene = ImageComposeScene(width, height, Density(scale), content = content)
        var img = scene.render(0)
        // Прокручиваем время, чтобы пружины и появления доиграли.
        for (i in 1..frames) img = scene.render(i * 50_000_000L)
        scene.close()
        return img
    }

    private fun sample(today: LocalDate): TaskData {
        var id = 1L
        fun t(title: String, due: LocalDate, desc: String = "", hot: Boolean = false, time: Int? = null, repeat: Repeat = Repeat.None, done: Boolean = false) =
            Task(id++, title, desc, due, done = done, doneAt = if (done) System.currentTimeMillis() else 0L, time = time, repeat = repeat, hot = hot, updatedAt = 1)
        return TaskData(
            listOf(
                t("Оплатить интернет, пока не отрубили", today, hot = true, time = 18 * 60),
                t("Позвонить маме", today, "Она уже третий раз спрашивает, жив ли", repeat = Repeat.Weekly),
                t("Разобрать почту", today.minusDays(2)),
                t("Скинуть Стасу отчёт", today, time = 15 * 60),
                t("Записаться к зубному", today.plusDays(1)),
                t("Купить корм коню", today, done = true),
            )
        )
    }

    private fun screenshot(name: String, theme: ThemeMode) {
        val dir = Files.createTempDirectory("ebis-preview").toFile()
        File(dir, DesktopStore.FILE_NAME).writeText(TaskJson.pretty(sample(LocalDate.now())))
        val node = "ru/konem/ebis-desktop-preview"
        val store = DesktopStore(node).apply { folder = dir; todayNewFirst = false }
        val state = AppState(store)
        val img = render(1000, 720, scale = 1f) {
            EbisTheme(theme) { App(state, remember { FocusRequester() }) { null } }
        }
        val out = File(projectDir, "build/preview/$name.png")
        out.parentFile.mkdirs()
        out.writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        Preferences.userRoot().node(node).removeNode()
        dir.deleteRecursively()
        println("preview: ${out.absolutePath}")
    }

    @Test
    fun renderWindow() {
        screenshot("light", ThemeMode.Light)
        screenshot("dark", ThemeMode.Dark)
    }

    /** Окно будильника и раздел «Сломалось?» — глянуть глазами. */
    @Test
    fun renderAlarmAndReport() {
        val alarm = render(560, 330, frames = 4) {
            EbisTheme(ThemeMode.Light) { AlarmView(AlarmRing(-1, "Позвонить в налоговую", "15:00", 0, 30)) {} }
        }
        File(projectDir, "build/preview/alarm.png").apply { parentFile.mkdirs() }.writeBytes(alarm.encodeToData(EncodedImageFormat.PNG)!!.bytes)

        val node = "ru/konem/ebis-desktop-preview-report"
        val dir = Files.createTempDirectory("ebis-preview").toFile()
        val state = AppState(DesktopStore(node).apply { folder = dir }).apply { openSettings(SettingsPane.Report) }
        val img = render(1000, 720) { EbisTheme(ThemeMode.Dark) { App(state, remember { FocusRequester() }) { null } } }
        File(projectDir, "build/preview/report.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        state.openSettings(SettingsPane.General)
        val gen = render(1000, 720) { EbisTheme(ThemeMode.Dark) { App(state, remember { FocusRequester() }) { null } } }
        File(projectDir, "build/preview/general.png").writeBytes(gen.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        Preferences.userRoot().node(node).removeNode()
        dir.deleteRecursively()
    }

    /** Иконки для установщиков из того же logo_app.xml: png для Linux и ico (PNG внутри) для Windows. */
    @Test
    fun renderIcons() {
        val sizes = listOf(16, 32, 48, 256)
        val pngs = sizes.associateWith { s ->
            render(s, s, frames = 1) { Image(painterResource("logo_app.xml"), null, Modifier.fillMaxSize()) }
                .encodeToData(EncodedImageFormat.PNG)!!.bytes
        }
        val dir = File(projectDir, "icons").apply { mkdirs() }
        File(dir, "icon.png").writeBytes(pngs.getValue(256))
        File(dir, "icon.ico").writeBytes(ico(pngs))
        // Тот же значок — в уведомления Windows.
        val notif = File(projectDir, "src/main/resources/notif").apply { mkdirs() }
        File(notif, "logo.png").writeBytes(pngs.getValue(256))
        File(notif, "icon.ico").writeBytes(ico(pngs))
    }

    private fun ico(pngs: Map<Int, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(6 + 16 * pngs.size).order(ByteOrder.LITTLE_ENDIAN)
        header.putShort(0).putShort(1).putShort(pngs.size.toShort())
        var offset = 6 + 16 * pngs.size
        pngs.forEach { (s, data) ->
            val b = if (s >= 256) 0 else s
            header.put(b.toByte()).put(b.toByte()).put(0).put(0).putShort(1).putShort(32).putInt(data.size).putInt(offset)
            offset += data.size
        }
        out.write(header.array())
        pngs.values.forEach { out.write(it) }
        return out.toByteArray()
    }
}
