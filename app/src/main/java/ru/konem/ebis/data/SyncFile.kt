package ru.konem.ebis.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Живая копия задач в папке, которую выбрал человек (например, её синхронизирует Syncthing с ПК).
 * Пишется после каждого изменения, но сначала в неё вливается то, что успело прийти снаружи, — чтобы не затереть
 * правки с ПК. Конфликтные копии Syncthing (ebis-tasks.sync-conflict-…json) тоже вливаются и удаляются.
 */
class SyncFile(context: Context) {
    private val ctx = context.applicationContext
    private val sp = ctx.getSharedPreferences("ebis_sync", Context.MODE_PRIVATE)

    /** Папка, выбранная через системный диалог. null — синхронизация выключена. */
    val folder: Uri? get() = sp.getString("tree", null)?.let(Uri::parse)

    /** Хеш того, что мы сами последний раз записали или прочитали: так отличаем чужие правки от своих. */
    private var lastHash: Int
        get() = sp.getInt("hash", 0)
        set(v) = sp.edit().putInt("hash", v).apply()

    /** Время изменения и размер файла при последнем чтении: совпали — файл не перечитываем. Только в памяти. */
    private var lastStamp: Pair<Long, Long>? = null

    /** Сколько проверок подряд пропустили по совпавшему штампу: изредка всё равно читаем — вдруг правка уложилась в ту же секунду и размер. */
    private var skipped = 0

    /** Время последней удачной записи, 0 — ещё не писали. */
    val lastWrite: Long get() = sp.getLong("lastWrite", 0L)

    /** Текст последней ошибки, null — всё хорошо. */
    val lastError: String? get() = sp.getString("error", null)

    /** Подключить папку. Возвращает задачи из уже лежащего там файла, если он есть и читается. */
    fun connect(tree: Uri): TaskData? {
        ctx.contentResolver.takePersistableUriPermission(
            tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        folder?.takeIf { it != tree }?.let { old ->
            runCatching { ctx.contentResolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        }
        lastStamp = null
        sp.edit().putString("tree", tree.toString()).remove("file").putInt("hash", 0).remove("error").apply()
        return runCatching { readText()?.let { TaskStore.parseData(it) } }.getOrNull()
    }

    fun disconnect() {
        folder?.let { tree ->
            runCatching { ctx.contentResolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        }
        lastStamp = null
        sp.edit().clear().apply()
    }

    /** Человекочитаемый путь папки, например «Documents/ebis». */
    fun folderLabel(): String? = folder?.let { tree ->
        runCatching { DocumentsContract.getTreeDocumentId(tree).substringAfter(':').ifEmpty { "корень памяти" } }.getOrNull()
    }

    /** Записать задачи, предварительно влив чужие правки. Возвращает то, что в итоге записано. */
    @Synchronized
    fun write(data: TaskData): TaskData {
        folder ?: return data
        val out = readIfChanged()?.let { mergeTasks(data, it) } ?: data
        val text = TaskStore.backup(out.copy(seeded = false))
        if (text.hashCode() == lastHash) return out
        // Вторая попытка — с поиском файла заново: его могли удалить или переименовать на ПК.
        var error: String? = null
        for (attempt in 1..2) {
            try {
                val uri = fileUri(create = true) ?: error("не смог создать файл")
                // "wt" — обрезать старое содержимое, иначе хвост длинного файла останется.
                ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                sp.edit().putInt("hash", text.hashCode()).putLong("lastWrite", System.currentTimeMillis()).remove("error").apply()
                return out
            } catch (e: Exception) {
                sp.edit().remove("file").apply()
                error = e.message ?: e.javaClass.simpleName
            }
        }
        sp.edit().putString("error", error).apply()
        return out
    }

    /**
     * Чужие правки: файл, если его поменяли снаружи с нашей последней записи, и конфликтные копии Syncthing
     * (прочитанные удаляются). Ничего нового — null.
     */
    @Synchronized
    fun readIfChanged(): TaskData? {
        folder ?: return null
        // Один запрос списка папки на проверку: из него и время/размер файла, и конфликтные копии.
        val list = runCatching { children() }.getOrNull()
        val stamp = list?.firstOrNull { it.name == FILE_NAME }?.stamp
        // Файл не трогали с прошлой проверки — не читаем его целиком (это раз в 3 секунды, пока приложение открыто).
        val same = stamp != null && stamp == lastStamp && skipped < FULL_READ_EVERY
        skipped = if (same) skipped + 1 else 0
        var out = if (same) null else readMain(stamp)
        takeConflicts(list.orEmpty()).forEach { c -> out = out?.let { mergeTasks(it, c) } ?: c }
        return out
    }

    private fun readMain(stamp: Pair<Long, Long>?): TaskData? {
        val text = runCatching { readText() }.onFailure { sp.edit().remove("file").apply() }.getOrNull() ?: return null
        if (text.hashCode() == lastHash) {
            lastStamp = stamp
            return null
        }
        // Синхронизатор мог записать файл не до конца — битый файл просто пропускаем.
        val data = runCatching { TaskStore.parseData(text) }.getOrNull() ?: return null
        lastHash = text.hashCode()
        lastStamp = stamp
        return data
    }

    /** Конфликтные копии в папке: прочитанные удаляем, недописанные оставляем до следующей проверки. */
    private fun takeConflicts(list: List<Child>): List<TaskData> = runCatching {
        list.filter { isSyncConflict(it.name) }.mapNotNull { (_, uri) ->
            val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return@mapNotNull null
            val data = runCatching { TaskStore.parseData(text) }.getOrNull() ?: return@mapNotNull null
            runCatching { DocumentsContract.deleteDocument(ctx.contentResolver, uri) }
            data
        }
    }.getOrDefault(emptyList())

    /** Файл в папке. [stamp] — время изменения и размер; null, если провайдер их не отдаёт (тогда читаем всегда). */
    private data class Child(val name: String, val uri: Uri, val stamp: Pair<Long, Long>? = null)

    /** Файлы в папке. */
    private fun children(): List<Child> {
        val tree = folder ?: return emptyList()
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        val out = mutableListOf<Child>()
        ctx.contentResolver.query(
            uri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val modified = if (c.isNull(2)) 0L else c.getLong(2)
                val size = if (c.isNull(3)) -1L else c.getLong(3)
                val stamp = if (modified > 0L && size >= 0L) modified to size else null
                out += Child(c.getString(1), DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)), stamp)
            }
        }
        return out
    }

    private fun readText(): String? {
        val uri = fileUri(create = false) ?: return null
        return ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    /** Документ ebis-tasks.json внутри папки: ищем по имени, при необходимости создаём. */
    private fun fileUri(create: Boolean): Uri? {
        val tree = folder ?: return null
        sp.getString("file", null)?.let { return Uri.parse(it) }
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val found = children().firstOrNull { it.name == FILE_NAME }?.uri
        val uri = found ?: if (create) DocumentsContract.createDocument(ctx.contentResolver, parent, "application/json", FILE_NAME) else null
        uri?.let { sp.edit().putString("file", it.toString()).apply() }
        return uri
    }

    companion object {
        const val FILE_NAME = "ebis-tasks.json"
        private const val FULL_READ_EVERY = 10
    }
}
