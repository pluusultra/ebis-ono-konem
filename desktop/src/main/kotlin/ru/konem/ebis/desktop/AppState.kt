package ru.konem.ebis.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.konem.ebis.data.ChecklistItem
import ru.konem.ebis.data.OverdueAction
import ru.konem.ebis.data.Repeat
import ru.konem.ebis.data.TagOps
import ru.konem.ebis.data.Task
import ru.konem.ebis.data.TaskData
import ru.konem.ebis.data.TaskOps
import ru.konem.ebis.data.importTasks
import ru.konem.ebis.data.mergeTasks
import ru.konem.ebis.ui.Copy
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

enum class Tab(val icon: String, val title: String) { Today("🔥", "Сегодня"), All("📦", "Вся херня"), Done("✅", "Сделано") }

/** Тост внизу окна; [undo] — кнопка «Верни». */
class Toast(val text: String, val undo: (() -> Unit)? = null, val id: Long = System.nanoTime())

/** Всё состояние окна. Меняется только с UI-потока (в Compose Desktop это Swing EDT). */
class AppState(val store: DesktopStore) {
    var data by mutableStateOf(TaskData(emptyList()))
        private set
    val tasks get() = data.tasks

    var tab by mutableStateOf(Tab.Today)
    var newFirst by mutableStateOf(store.todayNewFirst)
        private set
    var theme by mutableStateOf(store.theme)
        private set
    var toast by mutableStateOf<Toast?>(null)
    var editing by mutableStateOf<Task?>(null)
    var tools by mutableStateOf<TaskTool?>(null)

    /** Будильник «Горит», который сейчас орёт (своё окно поверх всех). */
    var alarm by mutableStateOf<AlarmRing?>(null)
    var settingsOpen by mutableStateOf(false)

    /** Какой раздел настроек открыт. */
    var settingsPane by mutableStateOf(SettingsPane.Notify)

    val notif = NotifSettings(store.prefs)
    val keys = Keymap(store.prefs)
    private val lookStore = LookStore(store.prefs)
    var look by mutableStateOf(lookStore.load())
        private set

    /** Новое оформление со сменой масштаба: применяется, пока окно на миг гаснет (см. App). */
    var pendingLook by mutableStateOf<Look?>(null)

    /** Ждём нажатия нового сочетания для этого действия (настройки → клавиши). */
    var capturing by mutableStateOf<Action?>(null)

    /** Ctrl+Enter в окне правки: счётчик, на который подписан диалог. */
    var saveRequests by mutableStateOf(0)
    var folder by mutableStateOf(store.folder)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var lastSync by mutableStateOf(0L)
        private set

    init {
        if (folder != null) reload()
    }

    /** Выбрать папку. Если там уже есть файл (с телефона) — сливаем его со своими задачами. */
    fun connect(dir: File) {
        val before = data
        store.folder = dir
        folder = dir
        val there = store.load()
        val merged = if (there == null) before else mergeTasks(before, there)
        save(merged)
        val n = data.tasks.count { !it.done }
        show("Папка подключена. ${Copy.left(n)}")
    }

    private fun reload() {
        store.load()?.let { data = it }
        refreshStatus()
    }

    /** Раз в пару секунд: файл мог поменять телефон. Возвращает true, если что-то подтянули. */
    fun pull(external: TaskData): Boolean {
        val merged = mergeTasks(data, external)
        val changed = merged.tasks != data.tasks
        // Записываем всегда: если наша версия что-то добавила (или пришла конфликтная копия) — телефон это получит.
        save(merged)
        return changed
    }

    fun refreshStatus() {
        if (store.lastError != error) store.lastError?.let { Log.w("sync", "Ошибка синхронизации: $it") } ?: Log.i("sync", "Синхронизация снова в порядке")
        error = store.lastError
        lastSync = store.lastSync
    }

    private fun update(newTasks: List<Task>): List<Task> {
        if (newTasks == data.tasks) return data.tasks
        val next = TaskOps.stamp(data, newTasks)
        // save вливает то, что успело прийти с телефона, и возвращает итог.
        save(next)
        return next.tasks
    }

    /** Действие с «Верни» в тосте. */
    private fun withUndo(message: String, next: List<Task>) {
        val before = data.tasks
        val after = update(next)
        show(message) {
            update(TaskOps.undo(data.tasks, before, after))
            show(Copy.restored)
        }
    }

    fun show(text: String, undo: (() -> Unit)? = null) {
        toast = Toast(text, undo)
    }

    fun add(
        title: String, due: LocalDate, time: Int?, repeat: Repeat, hot: Boolean,
        desc: String = "", repeatDays: Set<DayOfWeek> = emptySet(), tagIds: Set<Long> = emptySet(),
        checklist: List<ChecklistItem> = emptyList(),
    ) {
        if (title.isBlank()) {
            show(Copy.emptyTitle)
            return
        }
        update(TaskOps.add(data.tasks, title.trim(), desc.trim(), due, time, repeat, hot, repeatDays, tagIds, checklist).first)
        show(Copy.saved)
    }

    fun edit(
        id: Long, title: String, desc: String, due: LocalDate, time: Int?, repeat: Repeat, hot: Boolean,
        repeatDays: Set<DayOfWeek>, tagIds: Set<Long>, checklist: List<ChecklistItem>? = null, original: Task? = null,
    ) {
        if (title.isBlank()) {
            show(Copy.emptyTitle)
            return
        }
        val next = TaskOps.edit(data.tasks, id, title.trim(), desc.trim(), due, time, repeat, hot, repeatDays, tagIds, checklist, original)
        withUndo(Copy.edited, next)
    }

    // ── Теги ──

    val tags get() = data.tags

    /** Фильтр ленты по тегам: пусто — показываем всё. Живёт до закрытия программы. */
    var tagFilter by mutableStateOf<Set<Long>>(emptySet())
        private set

    fun toggleTagFilter(id: Long) {
        tagFilter = if (id in tagFilter) tagFilter - id else tagFilter + id
    }

    fun clearTagFilter() {
        tagFilter = emptySet()
    }

    private fun save(next: TaskData) {
        data = store.save(next)
        tagFilter = tagFilter.intersect(data.tags.map { it.id }.toSet())
        refreshStatus()
    }

    /** Возвращает id нового тега или null, если название не годится (о причине — тостом). */
    fun addTag(name: String, color: Int): Long? {
        TagOps.problem(data.tags, name)?.let { show(it); return null }
        val (next, id) = TagOps.add(data, name, color)
        save(next)
        return id
    }

    fun editTag(id: Long, name: String, color: Int): Boolean {
        TagOps.problem(data.tags, name, exceptId = id)?.let { show(it); return false }
        save(TagOps.edit(data, id, name, color))
        return true
    }

    fun deleteTag(id: Long) {
        val tag = data.tags.firstOrNull { it.id == id } ?: return
        save(TagOps.delete(data, id))
        show("Тег «${tag.name}» — нахер")
    }

    fun restoreTask(id: Long) {
        val next = TaskOps.restoreDeleted(data, id)
        if (next != data) {
            save(next)
            show("Задача восстановлена")
        }
    }

    fun restoreTag(id: Long) {
        val next = TagOps.restoreDeleted(data, id)
        if (next == data) {
            show("Не удалось вернуть тег. Проверь, свободно ли его название")
            return
        }
        save(next)
        show("Тег восстановлен")
    }

    fun importFrom(incoming: TaskData) {
        runCatching {
            val next = importTasks(data, incoming)
            store.importBackup.save(data)
            save(next)
        }.onSuccess { show("Задачи загружены. Копия перед импортом сохранена") }
            .onFailure { show("Импорт не удался") }
    }

    fun restoreImportBackup() {
        runCatching { save(importTasks(data, store.importBackup.load(), restore = true)) }
            .onSuccess { show("Копия перед импортом восстановлена") }
            .onFailure { show("Не удалось восстановить копию") }
    }

    fun batch(ids: Set<Long>, action: OverdueAction, tags: Set<Long> = emptySet()) {
        val validTags = tags.intersect(this.tags.map { it.id }.toSet())
        withUndo("Изменения применены", TaskOps.applyToOverdue(tasks, ids, action, validTags))
    }

    fun toggle(id: Long) {
        val before = data.tasks
        val (next, done) = TaskOps.toggle(data.tasks, id)
        val after = update(next)
        val allDone = done && next.none { !it.done && !it.due.isAfter(LocalDate.now()) }
        val msg = when {
            allDone -> Copy.allDone(0, false)
            done -> Copy.praise.random()
            else -> Copy.undo.random()
        }
        show(msg) { update(TaskOps.undo(data.tasks, before, after)) }
    }

    fun postpone(id: Long) = withUndo(Copy.postponed.random(), TaskOps.postpone(data.tasks, id))

    fun delete(id: Long) = withUndo(Copy.deleted, TaskOps.delete(data.tasks, id))

    fun reorder(ids: List<Long>) {
        update(TaskOps.reorder(data.tasks, if (newFirst) ids.reversed() else ids))
    }

    fun toggleSort() {
        newFirst = !newFirst
        store.todayNewFirst = newFirst
    }

    fun changeLook(l: Look) {
        Log.i("look", "Оформление: фон день=${l.bgLight?.hex()} ночь=${l.bgDark?.hex()} шапка=${l.head.hex()} узор=${l.pattern} шрифт=${l.font} масштаб=${l.scale}")
        look = l
        lookStore.save(l)
    }

    /** Полная форма новой задачи: то же окно правки, но с пустым черновиком. */
    fun newTask() {
        tools = null
        settingsOpen = false
        if (editing?.id != DRAFT_ID) editing = Task(id = DRAFT_ID, title = "", desc = "", due = LocalDate.now())
    }

    fun openSettings(pane: SettingsPane? = null) {
        tools = null
        pane?.let { settingsPane = it }
        settingsOpen = true
    }

    fun chooseTheme(mode: ThemeMode) {
        Log.i("look", "Тема: $theme → $mode")
        theme = mode
        store.theme = mode
    }
}

enum class TaskTool { Search, Overdue, Trash }

private fun androidx.compose.ui.graphics.Color.hex() = "#%02X%02X%02X".format((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

/** id черновика в форме новой задачи: настоящие id строятся от времени и сюда не попадают. */
const val DRAFT_ID = -1L

enum class SettingsPane(val icon: String, val title: String) {
    General("🎛️", "Общее"), Tags("🏷️", "Теги"), Look("🎨", "Оформление"), Notify("🔔", "Уведомления"), Keys("⌨️", "Клавиши"), Sync("🔄", "Синхронизация"),
    Report("🐞", "Сломалось?"),
}
