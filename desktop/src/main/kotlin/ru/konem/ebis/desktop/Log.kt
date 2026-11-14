package ru.konem.ebis.desktop

import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Подробности про окно (размеры, состояния, кадры) — только в течение нескольких секунд после сворачивания,
 * разворачивания, показа или скрытия. Иначе каждое движение окна забивало бы журнал.
 */
object WinDebug {
    @Volatile private var at = 0L

    fun mark() { at = System.currentTimeMillis() }

    val hot: Boolean get() = System.currentTimeMillis() - at < 3000

    fun log(msg: String) { if (hot) Log.i("win", msg) }
}

/**
 * Журнал на диске: что делал конь и где споткнулся. Чтобы человек с багом скинул файл, а не пересказывал.
 * Лежит в [dir] (на Windows — %APPDATA%\ebis-ono-konem\logs, на Linux — ~/.local/share/ebis-ono-konem/logs).
 * Названий задач и прочего личного сюда не пишем — только события, размеры, ошибки и сведения о системе.
 */
object Log {
    val dir: File get() = File(DesktopStore.defaultFolder(), "logs")
    val file: File get() = File(dir, "ebis.log")
    private val old: File get() = File(dir, "ebis.1.log")

    /** Больше — переименовываем в ebis.1.log и начинаем заново: два файла по полмегабайта максимум. */
    private const val MAX_BYTES = 512 * 1024

    private val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val lock = Any()
    private var out: PrintStream? = null
    private var written = 0L

    val version: String get() = System.getProperty("jpackage.app-version") ?: "dev"

    /** В самом начале main: открывает файл, пишет шапку с системой, ловит необработанные ошибки и всё из stderr. */
    fun start(args: Array<String>) {
        runCatching {
            dir.mkdirs()
            if (file.length() > MAX_BYTES) rotate()
            open()
        }
        header(args)
        val err = System.err
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, ex ->
            e("crash", "Необработанная ошибка в потоке «${t.name}»", ex)
            if (prev != null) prev.uncaughtException(t, ex) else ex.printStackTrace(err)
        }
        // Библиотеки (Compose, Skia, D-Bus) жалуются в stderr — копию тоже в журнал.
        System.setErr(PrintStream(Tee(err), true, Charsets.UTF_8))
    }

    fun i(tag: String, msg: String) = write("I", tag, msg, null)
    fun w(tag: String, msg: String, t: Throwable? = null) = write("W", tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = write("E", tag, msg, t)

    /** Последние строки журнала: показать в настройках или скопировать. */
    fun tail(lines: Int = 200): String = runCatching {
        synchronized(lock) { out?.flush() }
        file.readLines(Charsets.UTF_8).takeLast(lines).joinToString("\n")
    }.getOrDefault("")

    private fun header(args: Array<String>) {
        val env = listOf("XDG_CURRENT_DESKTOP", "XDG_SESSION_TYPE", "DESKTOP_SESSION", "WAYLAND_DISPLAY", "GDK_SCALE", "QT_SCALE_FACTOR", "SKIKO_RENDER_API")
            .mapNotNull { k -> System.getenv(k)?.takeIf { it.isNotBlank() }?.let { "$k=$it" } }
        val screens = runCatching {
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.joinToString("; ") { d ->
                val c = d.defaultConfiguration
                "${c.bounds.width}x${c.bounds.height} scale=${"%.2f".format(c.defaultTransform.scaleX)}"
            }
        }.getOrDefault("?")
        i("start", "────────── Ебись оно конём $version ──────────")
        i("start", "ОС: ${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}")
        i("start", "Java: ${System.getProperty("java.vendor")} ${System.getProperty("java.version")}, язык ${System.getProperty("user.language")}")
        i("start", "Экраны: $screens")
        if (env.isNotEmpty()) i("start", "Окружение: ${env.joinToString(" ")}")
        if (args.isNotEmpty()) i("start", "Аргументы: ${args.joinToString(" ")}")
        if (DesktopStore.PROFILE.isNotEmpty()) i("start", "Профиль: ${DesktopStore.PROFILE}")
    }

    private fun write(level: String, tag: String, msg: String, t: Throwable?) {
        val line = buildString {
            append(LocalDateTime.now().format(time)).append(' ').append(level).append(" [").append(tag).append("] ").append(msg)
            t?.let { append('\n').append(StringWriter().also { w -> it.printStackTrace(PrintWriter(w)) }.toString().trimEnd()) }
        }
        synchronized(lock) {
            val o = out ?: return
            runCatching {
                o.println(line)
                o.flush()
                written += line.length + 1
                if (written > MAX_BYTES) {
                    o.close()
                    rotate()
                    open()
                }
            }
        }
    }

    private fun open() {
        out = PrintStream(java.io.FileOutputStream(file, true), false, Charsets.UTF_8)
        written = file.length()
    }

    private fun rotate() {
        old.delete()
        file.renameTo(old)
    }

    /** Пишет и в настоящий stderr, и построчно в журнал. */
    private class Tee(private val orig: OutputStream) : OutputStream() {
        private val buf = java.io.ByteArrayOutputStream()

        override fun write(b: Int) {
            orig.write(b)
            if (b == '\n'.code) flushLine() else buf.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            orig.write(b, off, len)
            for (i in off until off + len) if (b[i] == '\n'.code.toByte()) flushLine() else buf.write(b[i].toInt())
        }

        override fun flush() = orig.flush()

        private fun flushLine() {
            val s = buf.toString(Charsets.UTF_8).trimEnd('\r')
            buf.reset()
            if (s.isNotBlank()) Log.w("stderr", s)
        }
    }
}
