package ru.konem.ebis.desktop

import androidx.compose.runtime.mutableStateListOf
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

/**
 * Одна копия приложения. Работающая копия слушает порт на 127.0.0.1 (номер — в app.port) и держит блокировку app.lock.
 * Новый запуск (повторный клик, кнопка уведомления) сначала стучится в этот порт: ответили — отдаёт команду и выходит.
 * Блокировка только страхует от двух копий, стартующих одновременно.
 */
object SingleInstance {
    private var channel: FileChannel? = null
    private var lock: FileLock? = null
    private var server: ServerSocket? = null

    /** Команды от других запусков: «show» или ссылка из уведомления вида ebis-konem://done/123. Читать с UI-потока. */
    val inbox = mutableStateListOf<String>()

    /** true — мы единственная копия. false — команда передана работающей, этот процесс надо закрыть. */
    fun acquire(dir: File, message: String): Boolean {
        dir.mkdirs()
        val portFile = File(dir, "app.port")
        if (send(portFile, message)) return false

        val ch = runCatching { RandomAccessFile(File(dir, "app.lock"), "rw").channel }.getOrNull()
        val l = ch?.let { c -> runCatching { c.tryLock() }.onFailure { log(dir, "tryLock: $it") }.getOrNull() }
        if (ch != null && l == null) {
            // Блокировку держит копия, которая как раз запускается и ещё не открыла порт, — даём ей пару секунд.
            repeat(10) {
                Thread.sleep(300)
                if (send(portFile, message)) {
                    runCatching { ch.close() }
                    return false
                }
            }
            log(dir, "lock held, but nobody answers on the port — starting anyway")
        }
        channel = ch
        lock = l

        val srv = ServerSocket()
        srv.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
        server = srv
        runCatching { portFile.writeText(srv.localPort.toString()) }.onFailure { log(dir, "port file: $it") }
        thread(isDaemon = true, name = "ebis-instance") {
            while (!srv.isClosed) {
                val s = runCatching { srv.accept() }.getOrNull() ?: continue
                runCatching {
                    s.soTimeout = 2000
                    val line = s.getInputStream().bufferedReader(Charsets.UTF_8).readLine()?.trim()
                    // Отвечаем, чтобы новый запуск знал: команду приняла живая копия.
                    s.getOutputStream().write("ok\n".toByteArray())
                    s.getOutputStream().flush()
                    if (!line.isNullOrEmpty()) SwingUtilities.invokeLater { inbox.add(line) }
                }
                runCatching { s.close() }
            }
        }
        return true
    }

    /** Отдать команду работающей копии. true — она ответила. */
    private fun send(portFile: File, message: String): Boolean {
        val port = runCatching { portFile.readText().trim().toInt() }.getOrNull() ?: return false
        return runCatching {
            Socket().use { s ->
                s.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 700)
                s.soTimeout = 2000
                s.getOutputStream().write((message + "\n").toByteArray(Charsets.UTF_8))
                s.getOutputStream().flush()
                s.getInputStream().bufferedReader().readLine() == "ok"
            }
        }.getOrDefault(false)
    }

    private fun log(dir: File, msg: String) {
        runCatching { File(dir, "instance.log").appendText("${java.time.LocalDateTime.now()} $msg\n") }
    }

    fun release() {
        runCatching { server?.close() }
        runCatching { lock?.release() }
        runCatching { channel?.close() }
    }
}
