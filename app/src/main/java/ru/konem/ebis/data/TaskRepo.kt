package ru.konem.ebis.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * Единственный источник задач на весь процесс: его читают и экран, и кнопки в уведомлениях,
 * поэтому «Сделал» из шторки сразу видно в открытом приложении.
 */
object TaskRepo {
    private lateinit var store: TaskStore
    private val io = Executors.newSingleThreadExecutor()

    var tasks by mutableStateOf<List<Task>>(emptyList())
        private set

    /** Надгробия удалённых задач: id → когда удалили. Нужны для слияния с файлом с ПК. */
    private var deleted: Map<Long, Long> = emptyMap()

    /** В списке только нетронутые обучалки первого запуска. */
    private var seeded = false

    private val main = Handler(Looper.getMainLooper())

    /** Вызывается после каждого изменения: приложение перевыставляет по нему напоминания. */
    var onChanged: ((List<Task>) -> Unit)? = null

    @Synchronized
    fun init(context: Context) {
        if (::store.isInitialized) return
        store = TaskStore(context.applicationContext)
        val data = store.load() ?: TaskData(seed(), seeded = true).also { store.save(it) }
        tasks = data.tasks
        deleted = data.deleted
        seeded = data.seeded
    }

    fun add(title: String, desc: String, due: LocalDate, time: Int? = null, repeat: Repeat = Repeat.None, hot: Boolean = false): Long {
        val (next, id) = TaskOps.add(tasks, title, desc, due, time, repeat, hot)
        update(next)
        return id
    }

    /** Правка: меняются только поля, которые есть в форме. */
    fun edit(id: Long, title: String, desc: String, due: LocalDate, time: Int?, repeat: Repeat, hot: Boolean) =
        update(TaskOps.edit(tasks, id, title, desc, due, time, repeat, hot))

    /** Возвращает новое состояние: true — задача теперь сделана. */
    fun toggle(id: Long, sync: Boolean = false): Boolean {
        val (next, done) = TaskOps.toggle(tasks, id)
        update(next, sync)
        return done
    }

    fun setDone(id: Long, sync: Boolean = false) = update(TaskOps.setDone(tasks, id), sync)

    fun postpone(id: Long, sync: Boolean = false) = update(TaskOps.postpone(tasks, id), sync)

    fun delete(id: Long, sync: Boolean = false) = update(TaskOps.delete(tasks, id), sync)

    fun reorder(ids: List<Long>) = update(TaskOps.reorder(tasks, ids))

    /** Для «Верни»: снимок до действия. */
    fun snapshot(): List<Task> = tasks

    fun restore(snapshot: List<Task>) = update(snapshot)

    fun replaceAll(newTasks: List<Task>) = update(newTasks)

    /** Дождаться, пока всё поставленное в очередь записано в файл (для ресиверов перед finish()). */
    fun flush() {
        runCatching { io.submit {}.get(3, java.util.concurrent.TimeUnit.SECONDS) }
    }

    val sync: SyncFile get() = store.sync

    /**
     * Подключает папку синхронизации. Если там уже лежит файл с задачами (с ПК или до переустановки) —
     * сливает его со своими (обучалки первого запуска просто заменяются) и отдаёт в [onFound] снимок «до»;
     * иначе сразу кладёт туда текущие задачи.
     */
    fun connectSync(tree: Uri, onFound: (count: Int, before: List<Task>) -> Unit, onDone: (Boolean) -> Unit) = io.execute {
        val found = runCatching { store.sync.connect(tree) }
        if (found.isFailure) { main.post { onDone(false) }; return@execute }
        val existing = found.getOrNull()
        if (existing == null) {
            store.sync.write(TaskData(tasks, deleted))
            main.post { onDone(store.sync.lastError == null) }
            return@execute
        }
        main.post {
            val before = tasks
            val merged = if (seeded) existing.copy(seeded = false) else mergeTasks(TaskData(tasks, deleted), existing)
            apply(merged)
            onFound(merged.tasks.size, before)
        }
    }

    /** Файл поменяли снаружи (синхронизация с ПК) — сливаем по задачам. Вызывается при возврате в приложение и раз в пару секунд, пока оно открыто. */
    fun pullExternal(onPulled: () -> Unit = {}) = io.execute {
        val ext = store.sync.readIfChanged() ?: return@execute
        main.post {
            val merged = mergeTasks(TaskData(tasks, deleted), ext)
            val changed = merged.tasks != tasks
            apply(merged)
            if (changed) onPulled()
        }
    }

    /** Принять уже готовое состояние как есть, без новых отметок времени. */
    private fun apply(data: TaskData) {
        tasks = data.tasks
        deleted = data.deleted
        seeded = false
        persist(TaskData(data.tasks, data.deleted), now = false)
        onChanged?.invoke(data.tasks)
    }

    /**
     * sync = true для ресиверов: процесс могут прибить сразу после onReceive.
     * Изменённые и новые задачи получают updatedAt = сейчас, пропавшие — надгробие.
     */
    private fun update(newTasks: List<Task>, sync: Boolean = false) {
        if (newTasks == tasks) return
        val data = TaskOps.stamp(tasks, newTasks, deleted)
        tasks = data.tasks
        deleted = data.deleted
        seeded = false
        persist(data, now = sync)
        onChanged?.invoke(data.tasks)
    }

    /**
     * Записать. Перед записью в файл синхронизации вливаются правки с ПК; если они что-то добавили —
     * принимаем итог и в память, чтобы экран и виджеты увидели его сразу.
     */
    private fun persist(data: TaskData, now: Boolean) {
        val job = {
            val out = store.save(data)
            if (out.tasks != data.tasks || out.deleted != data.deleted) main.post { adopt(out) }
        }
        if (now) job() else io.execute(job)
    }

    private fun adopt(written: TaskData) {
        val merged = mergeTasks(TaskData(tasks, deleted), written)
        if (merged.tasks == tasks && merged.deleted == deleted) return
        tasks = merged.tasks
        deleted = merged.deleted
        persist(merged, now = false)
        onChanged?.invoke(merged.tasks)
    }

    private fun seed(): List<Task> {
        val today = LocalDate.now()
        return listOf(
            Task(1, "Отметить эту задачу", "Нажми на квадратик слева или смахни карточку вправо. Конь порадуется.", today),
            Task(2, "Поставить задаче дату «никогда»", "Жми «Накинуть» и попробуй. Сам напросился.", today),
            Task(3, "Смахнуть меня влево", "Так задача уезжает на завтра. Тап по карточке — правка, долгий тап — перетащить.", today),
            Task(4, "Удалить эти обучалки", "Кнопка «нахер» справа. Не жалко.", today.plusDays(1)),
        )
    }
}
