package ru.konem.ebis.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Frame
import java.io.File
import javax.swing.JColorChooser
import javax.swing.JFileChooser
import javax.swing.UIManager
import kotlin.system.exitProcess

/**
 * Аргументы: ссылка из кнопки уведомления (ebis-konem://done/123) или --hidden (автозапуск: сразу в трей).
 * Если приложение уже открыто, команда уходит ему, а этот процесс закрывается.
 */
fun main(args: Array<String>) {
    val hidden = "--hidden" in args
    val cmd = args.firstOrNull { it.startsWith("${WinToast.SCHEME}:") }
    if (!SingleInstance.acquire(DesktopStore.defaultFolder(), cmd ?: if (hidden) "hidden" else "show")) exitProcess(0)
    Log.start(args)
    cmd?.let { SingleInstance.inbox.add(it) }

    // Системный вид у диалогов выбора папки и цвета: на Windows — родной, на Linux — GTK, если он есть.
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    val store = DesktopStore()
    // Linux: трей через D-Bus (StatusNotifierItem). Пункты вызывают то, что окно подставит ниже, когда соберётся.
    val trayAct = object {
        var show: () -> Unit = {}
        var toggle: () -> Unit = {}
        var quick: () -> Unit = {}
        var nag: () -> Unit = {}
        var quit: () -> Unit = {}
    }
    LinuxTray.start(
        "Ебись оно конём",
        listOf(
            LinuxTray.Item("Открыть") { trayAct.show() },
            LinuxTray.Item("Накинуть херню") { trayAct.quick() },
            LinuxTray.Item("Пнуть уведомлением") { trayAct.nag() },
            null,
            LinuxTray.Item("Выйти") { trayAct.quit() },
        ),
        onActivate = { trayAct.toggle() },
    )
    application {
        val state = remember { AppState(store) }
        val quickAdd = remember { FocusRequester() }
        val reminders = remember { Reminders(state) }
        val launch = remember { LaunchClock() }
        val lively = remember { Liveliness() }
        // Без трея прятаться некуда: автозапуск всё равно показывает окно.
        var visible by remember { mutableStateOf(!hidden || !Launcher.hasTray) }
        var shownOnce by remember { mutableStateOf(false) }
        var hide by remember { mutableStateOf({}) }
        var show by remember { mutableStateOf({}) }
        var toggle by remember { mutableStateOf({}) }
        val tray = rememberTrayState()

        fun quit() {
            SingleInstance.release()
            exitApplication()
        }
        LaunchedEffect(Unit) {
            trayAct.show = { show() }
            trayAct.toggle = { toggle() }
            trayAct.quick = {
                show()
                state.newTask()
            }
            trayAct.nag = { reminders.fire(Slot.Nag, force = true) }
            trayAct.quit = ::quit
        }

        LaunchedEffect(Unit) {
            WinToast.fallback = { t, x -> tray.sendNotification(Notification(t, x)) }
            // Проверочный профиль не трогает ярлык, ссылку ebis-konem:// и автозапуск настоящей версии.
            if (DesktopStore.PROFILE.isEmpty()) WinToast.setup(store.prefs, state.notif.autostart)
        }
        LaunchedEffect(Unit) { reminders.run() }
        // Команды от повторных запусков и кнопок уведомлений.
        LaunchedEffect(Unit) {
            snapshotFlow { SingleInstance.inbox.toList() }.collect { list ->
                if (list.isEmpty()) return@collect
                SingleInstance.inbox.clear()
                list.forEach { c -> if (state.handleCommand(c, reminders)) show() }
            }
        }

        if (Launcher.hasTray && !LinuxTray.active) Tray(
            icon = painterResource("logo_app.xml"),
            state = tray,
            tooltip = "Ебись оно конём",
            onAction = { toggle() },
            menu = {
                Item("Открыть", onClick = { show() })
                Item("Накинуть херню", onClick = {
                    show()
                    state.newTask()
                })
                Item("Пнуть уведомлением", onClick = { reminders.fire(Slot.Nag, force = true) })
                Separator()
                Item("Выйти", onClick = ::quit)
            },
        )

        // Будильник «Горит»: своё окно поверх всех, даже когда главное спрятано в трей.
        state.alarm?.let { r ->
            val alarmState = rememberWindowState(size = DpSize(AlarmWidth, Dp.Unspecified), position = WindowPosition(Alignment.Center))
            Window(
                onCloseRequest = {},
                state = alarmState,
                undecorated = true,
                resizable = false,
                alwaysOnTop = true,
                title = "Горит: ${r.title}",
                icon = painterResource("logo_app.xml"),
            ) {
                LaunchedEffect(r) {
                    window.toFront()
                    Log.i("alarm", "Окно будильника: ${window.width}x${window.height}, повтор №${r.snoozes}, проверка=${r.id < 0}")
                }
                // Закрыли задачу в списке или с телефона — замолкаем.
                LaunchedEffect(state.tasks) {
                    if (r.id >= 0 && state.tasks.none { it.id == r.id && !it.done }) reminders.stopAlarm()
                }
                // Высоту «по содержимому» Compose прикидывает до первой отрисовки. На Linux (другие шрифты, масштаб)
                // она выходила меньше нужной, и кнопки внизу сплющивались в пустые полоски. Поэтому меряем
                // содержимое без ограничения по высоте и подгоняем окно под то, что вышло на самом деле.
                val outer = LocalDensity.current
                EbisTheme(state.theme, state.look) {
                    Box(
                        Modifier
                            .wrapContentHeight(Alignment.Top, unbounded = true)
                            .onSizeChanged { px ->
                                val need = with(outer) { px.height.toDp() }
                                val have = alarmState.size.height
                                if (have == Dp.Unspecified || (need - have).value > 0.5f || (have - need).value > 0.5f) {
                                    Log.i("alarm", "Подгоняю высоту окна будильника: было $have, надо $need")
                                    alarmState.size = DpSize(AlarmWidth, need)
                                }
                            }
                            .border(2.5.dp, Color(0xFF1B1310)),
                    ) {
                        AlarmView(r) { reminders.answer(it) }
                    }
                }
            }
        }

        val windowState = rememberWindowState(size = WindowSize, position = WindowPosition(Alignment.Center))
        Window(
            onCloseRequest = { hide() },
            visible = visible,
            undecorated = true,
            resizable = false,
            title = "Ебись оно конём",
            icon = painterResource("logo_app.xml"),
            state = windowState,
            onPreviewKeyEvent = { e ->
                lively.poke()
                onKey(e, state) { hide() }
            },
        ) {
            val frame = remember { WindowFrame(window, store.prefs).also { it.restoreBounds() } }
            val scope = rememberCoroutineScope()
            show = {
                visible = true
                window.extendedState = window.extendedState and Frame.ICONIFIED.inv()
                // Въезд элементов — только при самом первом показе (автозапуск начинает со скрытого окна).
                if (!shownOnce) { shownOnce = true; launch.restart() }
                // Windows не даёт фоновому процессу просто так вылезти наверх — подмигиваем «поверх всех».
                // window.requestFocus() тут нельзя: Windows отказывает фоновому процессу, запрос фокуса в AWT
                // повисает, и окно потом не берёт клавиатуру даже по клику.
                window.isAlwaysOnTop = true
                window.toFront()
                window.isAlwaysOnTop = false
                // Если Windows так и не дала окну стать активным — сворачиваем и разворачиваем: так активирует всегда.
                scope.launch {
                    delay(150)
                    if (visible && !window.isActive) {
                        window.extendedState = window.extendedState or Frame.ICONIFIED
                        window.extendedState = window.extendedState and Frame.ICONIFIED.inv()
                    }
                }
            }
            hide = {
                if (!state.notif.tray) frame.exit = ::quit
                else if (Launcher.hasTray) frame.exit = { visible = false }
                else frame.minimize()
            }

            // Когда окно последний раз было активным. Клик по трею сам отнимает у окна фокус (активной становится
            // панель задач), поэтому «окно было активно только что» = человек смотрел в него и хочет спрятать.
            var activeAt by remember { mutableStateOf(0L) }
            DisposableEffect(window) {
                val l = object : WindowAdapter() {
                    override fun windowDeactivated(e: WindowEvent?) { activeAt = System.currentTimeMillis() }
                }
                window.addWindowListener(l)
                onDispose { window.removeWindowListener(l) }
            }
            // Двойной клик по значку в трее (на Linux — одинарный): открыто и на виду — прячем в трей, иначе показываем.
            toggle = {
                val iconified = window.extendedState and Frame.ICONIFIED != 0
                val recent = window.isActive || System.currentTimeMillis() - activeAt < 1500
                if (visible && !iconified && recent) {
                    Log.i("tray", "Клик по трею: прячу окно")
                    frame.exit = { visible = false }
                } else {
                    Log.i("tray", "Клик по трею: показываю окно (видно=$visible, свёрнуто=$iconified)")
                    show()
                }
            }

            LaunchedEffect(Unit) {
                // Первый показ: даём окну отрисоваться и запускаем появление.
                delay(16)
                if (visible && !shownOnce) { shownOnce = true; launch.restart() }
            }

            CompositionLocalProvider(LocalLaunch provides launch, LocalLively provides lively) {
                launch.Drive()
                lively.Drive()
                EbisTheme(state.theme, state.look) {
                    App(
                        state, quickAdd, frame,
                        onHide = { hide() },
                        pickColor = { chooseColor(window, it) },
                        pickSound = { chooseSound(window) },
                        reminders = reminders,
                    ) { chooseFolder(window, state.folder) }
                }
            }
        }
    }
    // Соединение с D-Bus держит свои потоки — без явного выхода процесс остался бы висеть.
    LinuxTray.stop()
    exitProcess(0)
}

/** Горячие клавиши окна. Сначала — режим «жми новое сочетание» из настроек. */
private fun onKey(e: KeyEvent, state: AppState, hide: () -> Unit): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    state.capturing?.let { a ->
        if (e.key == Key.Escape) {
            state.capturing = null
            return true
        }
        val c = Keymap.combo(e) ?: return true
        if (!Keymap.usable(c)) {
            state.show("Нужен Ctrl или Alt, иначе сработает посреди набора текста")
            return true
        }
        val clash = state.keys.set(a, c)
        state.capturing = null
        state.show(clash?.let { "$c было у «${it.label}» — поменял местами" } ?: "«${a.label}» теперь на $c")
        return true
    }
    if (e.key == Key.Escape) {
        return when {
            state.editing != null -> { state.editing = null; true }
            state.settingsOpen -> { state.settingsOpen = false; true }
            else -> false
        }
    }
    val c = Keymap.combo(e) ?: return false
    when (state.keys.find(c)) {
        Action.Quick -> state.newTask()
        Action.Today -> state.tab = Tab.Today
        Action.All -> state.tab = Tab.All
        Action.Done -> state.tab = Tab.Done
        Action.Settings -> if (state.settingsOpen) state.settingsOpen = false else state.openSettings()
        Action.Theme -> state.chooseTheme(if (Palette.IsDark) ThemeMode.Light else ThemeMode.Dark)
        Action.Save -> if (state.editing != null) state.saveRequests++ else return false
        Action.Hide -> hide()
        null -> return false
    }
    return true
}

/** Swing-диалог выбора папки: одинаково работает на Windows и Linux. */
private fun chooseFolder(parent: java.awt.Component, current: File?): File? {
    val chooser = JFileChooser(current ?: File(System.getProperty("user.home"))).apply {
        dialogTitle = "Папка, где лежит (или будет лежать) ${DesktopStore.FILE_NAME}"
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
    }
    return if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

/** Свой звук будильника: mp3 или wav. */
private fun chooseSound(parent: java.awt.Component): File? {
    val chooser = JFileChooser(File(System.getProperty("user.home"))).apply {
        dialogTitle = "Чем орать будильнику"
        fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Звук (mp3, wav)", "mp3", "wav")
    }
    return if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

/** Системная палитра для своего цвета фона и шапки. */
private fun chooseColor(parent: java.awt.Component, current: Color?): Color? {
    val init = current?.let { java.awt.Color(it.red, it.green, it.blue) } ?: java.awt.Color(0xFF, 0xD4, 0x47)
    val c = JColorChooser.showDialog(parent, "Свой цвет", init) ?: return null
    return Color(c.red, c.green, c.blue)
}
