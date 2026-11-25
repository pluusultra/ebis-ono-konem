package ru.konem.ebis.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.konem.ebis.data.Repeat
import ru.konem.ebis.data.TagOps
import ru.konem.ebis.data.Task
import ru.konem.ebis.data.TaskData
import ru.konem.ebis.data.TaskOps
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
        data = store.save(merged)
        refreshStatus()
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
        data = store.save(merged)
        refreshStatus()
        return changed
    }

    fun refreshStatus() {
        if (store.lastError != error) store.lastError?.let { Log.w("sync", "Ошибка синхронизации: $it") } ?: Log.i("sync", "Синхронизация снова в порядке")
        error = store.lastError
        lastSync = store.lastSync
    }

    private fun update(newTasks: List<Task>) {
        if (newTasks == data.tasks) return
        val next = TaskOps.stamp(data, newTasks)
        // save вливает то, что успело прийти с телефона, и возвращает итог.
        data = store.save(next)
        refreshStatus()
    }

    /** Действие с «Верни» в тосте. */
    private fun withUndo(message: String, block: () -> Unit) {
        val before = data.tasks
        block()
        show(message) {
            update(before)
            show(Copy.restored)
        }
    }

    fun show(text: String, undo: (() -> Unit)? = null) {
        toast = Toast(text, undo)
    }

    fun add(
        title: String, due: LocalDate, time: Int?, repeat: Repeat, hot: Boolean,
        desc: String = "", repeatDays: Set<DayOfWeek> = emptySet(), tagIds: Set<Long> = emptySet(),
    ) {
        if (title.isBlank()) {
            show(Copy.emptyTitle)
            return
        }
        update(TaskOps.add(data.tasks, title.trim(), desc.trim(), due, time, repeat, hot, repeatDays, tagIds).first)
        show(Copy.saved)
    }

    fun edit(
        id: Long, title: String, desc: String, due: LocalDate, time: Int?, repeat: Repeat, hot: Boolean,
        repeatDays: Set<DayOfWeek>, tagIds: Set<Long>,
    ) {
        if (title.isBlank()) {
            show(Copy.emptyTitle)
            return
        }
        withUndo(Copy.edited) { update(TaskOps.edit(data.tasks, id, title.trim(), desc.trim(), due, time, repeat, hot, repeatDays, tagIds)) }
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

    private fun saveTags(next: TaskData) {
        data = store.save(next)
        refreshStatus()
    }

    /** Возвращает id нового тега или null, если название не годится (о причине — тостом). */
    fun addTag(name: String, color: Int): Long? {
        TagOps.problem(data.tags, name)?.let { show(it); return null }
        val (next, id) = TagOps.add(data, name, color)
        saveTags(next)
        return id
    }

    fun editTag(id: Long, name: String, color: Int): Boolean {
        TagOps.problem(data.tags, name, exceptId = id)?.let { show(it); return false }
        saveTags(TagOps.edit(data, id, name, color))
        return true
    }

    fun deleteTag(id: Long) {
        val tag = data.tags.firstOrNull { it.id == id } ?: return
        saveTags(TagOps.delete(data, id))
        tagFilter = tagFilter - id
        show("Тег «${tag.name}» — нахер")
    }

    fun toggle(id: Long) {
        val before = data.tasks
        val (next, done) = TaskOps.toggle(data.tasks, id)
        update(next)
        val allDone = done && next.none { !it.done && !it.due.isAfter(LocalDate.now()) }
        val msg = when {
            allDone -> Copy.allDone(0, false)
            done -> Copy.praise.random()
            else -> Copy.undo.random()
        }
        show(msg) { update(before) }
    }

    fun postpone(id: Long) = withUndo(Copy.postponed.random()) { update(TaskOps.postpone(data.tasks, id)) }

    fun delete(id: Long) = withUndo(Copy.deleted) { update(TaskOps.delete(data.tasks, id)) }

    fun reorder(ids: List<Long>) = update(TaskOps.reorder(data.tasks, if (newFirst) ids.reversed() else ids))

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
        settingsOpen = false
        if (editing?.id != DRAFT_ID) editing = Task(id = DRAFT_ID, title = "", desc = "", due = LocalDate.now())
    }

    fun openSettings(pane: SettingsPane? = null) {
        pane?.let { settingsPane = it }
        settingsOpen = true
    }

    fun chooseTheme(mode: ThemeMode) {
        Log.i("look", "Тема: $theme → $mode")
        theme = mode
        store.theme = mode
    }
}

private fun androidx.compose.ui.graphics.Color.hex() = "#%02X%02X%02X".format((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

/** id черновика в форме новой задачи: настоящие id строятся от времени и сюда не попадают. */
const val DRAFT_ID = -1L

enum class SettingsPane(val icon: String, val title: String) {
    General("🎛️", "Общее"), Tags("🏷️", "Теги"), Look("🎨", "Оформление"), Notify("🔔", "Уведомления"), Keys("⌨️", "Клавиши"), Sync("🔄", "Синхронизация"),
    Report("🐞", "Сломалось?"),
}
