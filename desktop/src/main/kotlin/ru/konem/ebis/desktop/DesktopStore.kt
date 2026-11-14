package ru.konem.ebis.desktop

import ru.konem.ebis.data.TaskData
import ru.konem.ebis.data.TaskJson
import ru.konem.ebis.data.isSyncConflict
import ru.konem.ebis.data.mergeTasks
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.prefs.Preferences

/**
 * Где лежит ebis-tasks.json и как его читать/писать. На ПК файл и есть хранилище: телефон пишет
 * в ту же синхронизируемую папку, и обе стороны сливают изменения по задачам.
 */
class DesktopStore(prefsNode: String = "ru/konem/ebis-desktop$PROFILE") {
    /** Общий узел настроек: тут же живут уведомления, оформление и клавиши (см. Settings.kt). */
    val prefs: Preferences = Preferences.userRoot().node(prefsNode)

    /** Папка с файлом. null — человек ещё не выбрал (первый запуск). */
    var folder: File?
        get() = prefs.get("folder", null)?.let(::File)
        set(v) {
            if (v == null) prefs.remove("folder") else prefs.put("folder", v.absolutePath)
            prefs.flush()
            lastHash = 0
            lastStamp = 0L to 0L
        }

    var theme: ThemeMode
        get() = runCatching { ThemeMode.valueOf(prefs.get("theme", ThemeMode.System.name)) }.getOrDefault(ThemeMode.System)
        set(v) { prefs.put("theme", v.name); prefs.flush() }

    var todayNewFirst: Boolean
        get() = prefs.getBoolean("todayNewFirst", false)
        set(v) { prefs.putBoolean("todayNewFirst", v); prefs.flush() }

    val file: File? get() = folder?.let { File(it, FILE_NAME) }

    /** Хеш того, что мы сами записали или прочитали последним: так отличаем чужие правки от своих. */
    private var lastHash = 0

    /** Время изменения и размер файла при последней проверке — чтобы не перечитывать его каждые полторы секунды. */
    private var lastStamp = 0L to 0L

    var lastError: String? = null
        private set

    /** Когда последний раз удачно записали или подтянули файл. */
    var lastSync: Long = 0L
        private set

    @Synchronized
    fun load(): TaskData? {
        val f = file ?: return null
        if (!f.exists()) return TaskData(emptyList())
        return try {
            val text = f.readText()
            remember(f, text)
            TaskJson.parseData(text)
        } catch (e: Exception) {
            lastError = "Не смог прочитать ${f.name}: ${e.message}"
            null
        }
    }

    /**
     * Записать задачи. Сначала вливает то, что успело прийти снаружи (файл с телефона, конфликтные копии Syncthing),
     * чтобы не затереть чужие правки. Возвращает то, что в итоге записано, — его и надо показывать.
     */
    @Synchronized
    fun save(data: TaskData): TaskData {
        val f = file ?: return data
        val out = readIfChanged()?.let { mergeTasks(data, it) } ?: data
        val text = TaskJson.pretty(out)
        if (text.hashCode() == lastHash && f.exists()) return out
        try {
            f.parentFile?.mkdirs()
            // Пишем рядом и подменяем одним движением: синхронизатор не увидит полуфайл.
            val tmp = File(f.parentFile, ".$FILE_NAME.tmp")
            tmp.writeText(text)
            try {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            remember(f, text)
            lastError = null
        } catch (e: Exception) {
            lastError = "Не смог записать ${f.name}: ${e.message}"
        }
        return out
    }

    /**
     * Чужие правки: файл, если его поменяли снаружи (телефон через синхронизацию), и конфликтные копии Syncthing —
     * они вливаются и удаляются. Ничего нового — null.
     */
    @Synchronized
    fun readIfChanged(): TaskData? {
        val f = file ?: return null
        var out = readMain(f)
        takeConflicts(f.parentFile).forEach { c -> out = out?.let { mergeTasks(it, c) } ?: c }
        return out
    }

    private fun readMain(f: File): TaskData? {
        if (!f.exists()) return null
        val stamp = f.lastModified() to f.length()
        if (stamp == lastStamp) return null
        val text = runCatching { f.readText() }.getOrNull() ?: return null
        lastStamp = stamp
        if (text.hashCode() == lastHash) return null
        // Синхронизатор мог записать файл не до конца — битый пропускаем, следующая проверка возьмёт целый.
        val data = runCatching { TaskJson.parseData(text) }.getOrNull() ?: run { lastStamp = 0L to 0L; return null }
        lastHash = text.hashCode()
        lastSync = System.currentTimeMillis()
        return data
    }

    /** Конфликтные копии рядом с файлом: прочитанные удаляем, недописанные оставляем до следующей проверки. */
    private fun takeConflicts(dir: File?): List<TaskData> {
        val copies = dir?.listFiles { c -> c.isFile && isSyncConflict(c.name) } ?: return emptyList()
        return copies.mapNotNull { c ->
            val data = runCatching { TaskJson.parseData(c.readText()) }.getOrNull() ?: return@mapNotNull null
            runCatching { c.delete() }
            lastSync = System.currentTimeMillis()
            data
        }
    }

    private fun remember(f: File, text: String) {
        lastHash = text.hashCode()
        lastStamp = f.lastModified() to f.length()
        lastSync = System.currentTimeMillis()
    }

    companion object {
        const val FILE_NAME = "ebis-tasks.json"

        /** Отдельный профиль для проверок (-Debis.profile=test): свои настройки и папка, настоящие не трогаются. */
        val PROFILE: String = System.getProperty("ebis.profile")?.takeIf { it.isNotBlank() }?.let { "-$it" } ?: ""

        /** Папка «по умолчанию», если синхронизировать пока не с чем: %APPDATA% на Windows, ~/.local/share на Linux. */
        fun defaultFolder(): File {
            val os = System.getProperty("os.name").lowercase()
            val home = System.getProperty("user.home")
            val base = when {
                os.contains("win") -> System.getenv("APPDATA")?.let(::File) ?: File(home, "AppData/Roaming")
                os.contains("mac") -> File(home, "Library/Application Support")
                else -> System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let(::File) ?: File(home, ".local/share")
            }
            return File(base, "ebis-ono-konem$PROFILE")
        }
    }
}
