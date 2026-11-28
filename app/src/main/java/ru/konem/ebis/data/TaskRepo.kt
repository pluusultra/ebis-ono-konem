package ru.konem.ebis.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * Единственный источник задач на весь процесс: его читают и экран, и кнопки в уведомлениях,
 * поэтому «Сделал» из шторки сразу видно в открытом приложении.
 */
object TaskRepo {
    private lateinit var store: TaskStore
    private val io = Executors.newSingleThreadExecutor()

    var data by mutableStateOf(TaskData(emptyList()))
        private set

    val tasks get() = data.tasks
    val tags get() = data.tags
    private fun current() = data.copy(seeded = false)

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
        this.data = data
        seeded = data.seeded
    }

    fun add(
        title: String, desc: String, due: LocalDate, time: Int? = null,
        repeat: Repeat = Repeat.None, hot: Boolean = false, repeatDays: Set<DayOfWeek> = emptySet(),
        tagIds: Set<Long> = emptySet(), checklist: List<ChecklistItem> = emptyList(),
    ): Long {
        val (next, id) = TaskOps.add(tasks, title, desc, due, time, repeat, hot, repeatDays, tagIds, checklist)
        update(next)
        return id
    }

    /** Правка: меняются только поля, которые есть в форме. */
    fun edit(
        id: Long, title: String, desc: String, due: LocalDate, time: Int?, repeat: Repeat, hot: Boolean,
        repeatDays: Set<DayOfWeek>, tagIds: Set<Long>, checklist: List<ChecklistItem>? = null, original: Task? = null,
    ) = update(TaskOps.edit(tasks, id, title, desc, due, time, repeat, hot, repeatDays, tagIds, checklist, original))

    /** Теги меняются сразу с отметкой времени (см. [TagOps]), задачи при этом не трогаются. */
    private fun updateTags(next: TaskData) {
        data = next
        seeded = false
        persist(next, now = false)
    }

    /** id нового тега; название проверяют заранее через [TagOps.problem]. */
    fun addTag(name: String, color: Int): Long {
        val (next, id) = TagOps.add(current(), name, color)
        updateTags(next)
        return id
    }

    fun editTag(id: Long, name: String, color: Int) = updateTags(TagOps.edit(current(), id, name, color))

    fun deleteTag(id: Long) = updateTags(TagOps.delete(current(), id))

    fun restoreTask(id: Long): Boolean {
        val next = TaskOps.restoreDeleted(current(), id)
        if (next == current()) return false
        apply(next)
        return true
    }

    fun restoreTag(id: Long): Boolean {
        val next = TagOps.restoreDeleted(current(), id)
        if (next == current()) return false
        apply(next)
        return true
    }

    val hasImportBackup get() = store.importBackup.available

    fun importFrom(incoming: TaskData): Boolean = runCatching {
        val before = current()
        val next = importTasks(before, incoming)
        store.importBackup.save(before)
        apply(next)
    }.isSuccess

    fun restoreImportBackup(): Boolean = runCatching {
        apply(importTasks(current(), store.importBackup.load(), restore = true))
    }.isSuccess

    fun batch(ids: Set<Long>, action: OverdueAction, tags: Set<Long> = emptySet()) =
        update(TaskOps.applyToOverdue(tasks, ids, action, tags.intersect(this.tags.map { it.id }.toSet())))

    fun rescheduleOverdue(ids: Set<Long>, due: LocalDate) =
        update(TaskOps.rescheduleOverdue(tasks, ids, due, LocalDate.now()))

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

    fun undo(before: List<Task>, after: List<Task>) = update(TaskOps.undo(tasks, before, after))

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
            store.sync.write(current())
            main.post { onDone(store.sync.lastError == null) }
            return@execute
        }
        main.post {
            val before = tasks
            val merged = if (seeded) existing.copy(seeded = false) else mergeTasks(current(), existing)
            apply(merged)
            onFound(merged.tasks.size, before)
        }
    }

    /** Файл поменяли снаружи (синхронизация с ПК) — сливаем по задачам. Вызывается при возврате в приложение и раз в пару секунд, пока оно открыто. */
    fun pullExternal(onPulled: () -> Unit = {}) = io.execute {
        val ext = store.sync.readIfChanged() ?: return@execute
        main.post {
            val merged = mergeTasks(current(), ext)
            val changed = merged.tasks != tasks || merged.tags != tags
            apply(merged)
            if (changed) onPulled()
        }
    }

    /** Принять уже готовое состояние как есть, без новых отметок времени. */
    private fun apply(data: TaskData) {
        this.data = data.copy(seeded = false)
        seeded = false
        persist(data.copy(seeded = false), now = false)
        onChanged?.invoke(data.tasks)
    }

    /**
     * sync = true для ресиверов: процесс могут прибить сразу после onReceive.
     * Изменённые и новые задачи получают updatedAt = сейчас, пропавшие — надгробие.
     */
    private fun update(newTasks: List<Task>, sync: Boolean = false) {
        if (newTasks == tasks) return
        val data = TaskOps.stamp(current(), newTasks)
        this.data = data
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
            if (out.copy(seeded = false) != data.copy(seeded = false)) main.post { adopt(out) }
        }
        if (now) job() else io.execute(job)
    }

    private fun adopt(written: TaskData) {
        val merged = mergeTasks(current(), written)
        if (merged == current()) return
        data = merged
        persist(merged, now = false)
        onChanged?.invoke(merged.tasks)
    }

    private fun seed(): List<Task> {
        val today = LocalDate.now()
        return listOf(
            Task(1, "Отметить эту задачу", "Нажми на квадратик слева или смахни карточку вправо. Конь порадуется.", today),
            Task(2, "Поставить задаче дату «никогда»", "Жми «Накинуть» и попробуй. Сам напросился.", today),
            Task(3, "Смахнуть меня влево", "Так задача уезжает на завтра. Тап по карточке — правка, долгий тап — перетащить.", today),
            Task(4, "Удалить эти обучалки", "Кнопка «в корзину» справа. Не жалко.", today.plusDays(1)),
        )
    }
}
