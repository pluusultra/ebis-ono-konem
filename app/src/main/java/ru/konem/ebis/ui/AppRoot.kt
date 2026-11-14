package ru.konem.ebis.ui

import android.Manifest
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import ru.konem.ebis.Incoming
import ru.konem.ebis.TasksViewModel
import ru.konem.ebis.notify.Notifier
import ru.konem.ebis.data.TaskRepo
import ru.konem.ebis.notify.Prefs
import ru.konem.ebis.widget.Widgets
import ru.konem.ebis.ui.theme.Palette
import java.time.LocalDate

enum class Stage { Welcome, App }
enum class Tab { Today, All, Done }

/** Через сколько в фоне снова здороваться. */
private const val WELCOME_AGAIN_MS = 5 * 60_000L

/** Как часто, пока приложение открыто, проверять файл синхронизации (ПК проверяет раз в полторы секунды). */
private const val SYNC_POLL_MS = 3_000L

@Composable
fun AppRoot(vm: TasksViewModel = viewModel()) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var stage by rememberSaveable { mutableStateOf(if (prefs.welcomeOn) Stage.Welcome else Stage.App) }
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    var banner by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var prefillTitle by rememberSaveable { mutableStateOf("") }
    var prefillDesc by rememberSaveable { mutableStateOf("") }
    var neigh by remember { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var askPerm by remember { mutableStateOf(false) }
    // Серия читается из настроек; счётчик заставляет перечитать после закрытия всего.
    var streakTick by remember { mutableIntStateOf(0) }
    var newFirst by remember { mutableStateOf(prefs.todayNewFirst) }
    val toast = remember { ToastState() }
    val haptic = LocalHapticFeedback.current

    fun openSheet(task: ru.konem.ebis.data.Task?, title: String = "", desc: String = "") {
        editingId = task?.id
        prefillTitle = title
        prefillDesc = desc
        sheetOpen = true
    }

    // Ярлык «Накинуть» и «Поделиться» — сразу в форму, минуя приветствие.
    val incoming = Incoming.prefill
    LaunchedEffect(incoming) {
        val p = incoming ?: return@LaunchedEffect
        Incoming.prefill = null
        settingsOpen = false
        if (stage == Stage.Welcome) {
            stage = Stage.App
            delay(350)
        }
        openSheet(null, p.title, p.desc)
    }

    // Строка «✓ сделано сегодня» на виджете — сразу во вкладку «Сделано», тоже минуя приветствие.
    val openDone = Incoming.openDone
    LaunchedEffect(openDone) {
        if (!openDone) return@LaunchedEffect
        Incoming.openDone = false
        settingsOpen = false
        sheetOpen = false
        stage = Stage.App
        tab = Tab.Done
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        toast.show(if (granted) "Конь будет орать. Сам напросился 🐴" else "Ладно, конь будет орать молча")
    }
    // Спрашиваем про уведомления один раз, когда человек уже попал в трекер.
    LaunchedEffect(stage) {
        if (stage == Stage.App && Build.VERSION.SDK_INT >= 33 && !prefs.askedPermission && !Notifier.canPost(ctx)) {
            delay(1200)
            askPerm = true
        }
    }

    val owner = LocalLifecycleOwner.current
    // Пока приложение на экране, правки с ПК подтягиваются сами, без сворачивания.
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(SYNC_POLL_MS)
                TaskRepo.pullExternal { toast.show("Подтянул свежие задачи из папки синхронизации") }
            }
        }
    }
    DisposableEffect(owner) {
        var stoppedAt = 0L
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> stoppedAt = SystemClock.elapsedRealtime()
                Lifecycle.Event.ON_START -> {
                    // Файл могли поменять на ПК, пока приложение было в фоне.
                    TaskRepo.pullExternal { toast.show("Подтянул свежие задачи из папки синхронизации") }
                    if (
                        prefs.welcomeOn && Incoming.prefill == null && !Incoming.openDone &&
                        stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > WELCOME_AGAIN_MS
                    ) {
                        sheetOpen = false
                        neigh = false
                        stage = Stage.Welcome
                    }
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    /** Действие с кнопкой «Верни» в тосте: откатывает список задач к снимку. */
    fun withUndo(message: String, block: () -> Unit) {
        val before = vm.snapshot()
        block()
        toast.show(message, Copy.undoLabel) {
            vm.restore(before)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.Paper)) {
        AnimatedVisibility(
            visible = stage == Stage.App,
            enter = fadeIn(tween(400, delayMillis = 150)) +
                slideInVertically(Motion.smooth(IntOffset(1, 1))) { it / 12 } +
                scaleIn(Motion.smooth(), initialScale = 0.97f),
            exit = fadeOut(tween(200)),
        ) {
            val today = LocalDate.now().toEpochDay()
            val streak = remember(streakTick, today) { prefs.liveStreak(today) }
            TrackerScreen(
                tasks = vm.tasks,
                tab = tab,
                onTab = { tab = it },
                banner = banner,
                onDismissBanner = { banner = null },
                actions = TrackerActions(
                    onToggle = { id ->
                        val before = vm.snapshot()
                        val done = vm.toggle(id)
                        Notifier.cancelForTask(ctx, id)
                        val all = if (done) Notifier.checkAllDone(ctx) else null
                        if (all != null) {
                            streakTick++
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        val msg = when {
                            all != null -> Copy.allDone(all.streak, all.record)
                            done -> Copy.praise.random()
                            else -> Copy.undo.random()
                        }
                        toast.show(msg, Copy.undoLabel) { vm.restore(before) }
                    },
                    onPostpone = { id ->
                        withUndo(Copy.postponed.random()) {
                            vm.postpone(id)
                            Notifier.cancelForTask(ctx, id)
                        }
                    },
                    onDelete = { id ->
                        withUndo(Copy.deleted) {
                            vm.delete(id)
                            Notifier.cancelForTask(ctx, id)
                        }
                    },
                    onEdit = { openSheet(it) },
                    onReorder = { vm.reorder(it) },
                ),
                onAdd = { openSheet(null) },
                onSettings = { settingsOpen = true },
                streak = streak,
                bestStreak = prefs.bestStreak,
                newFirst = newFirst,
                onToggleSort = {
                    newFirst = !newFirst
                    prefs.todayNewFirst = newFirst
                    Widgets.refresh(ctx)
                },
            )
        }

        AnimatedVisibility(
            visible = stage == Stage.Welcome,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(400)) + scaleOut(tween(500), targetScale = 0.92f) +
                slideOutVertically(tween(500)) { -it / 25 },
        ) {
            WelcomeScreen(onChoose = { work ->
                banner = if (work) Copy.moodWork else Copy.moodCry
                tab = Tab.Today
                stage = Stage.App
            })
        }

        NewTaskSheet(
            visible = sheetOpen,
            editing = editingId?.let { id -> vm.tasks.firstOrNull { it.id == id } },
            prefillTitle = prefillTitle,
            prefillDesc = prefillDesc,
            backEnabled = !neigh,
            onClose = { sheetOpen = false },
            onNever = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                neigh = true
            },
            onSave = { d ->
                sheetOpen = false
                if (d.id != null) {
                    withUndo(Copy.edited) { vm.edit(d.id, d.title, d.desc, d.due, d.time, d.repeat, d.hot) }
                } else {
                    vm.add(d.title, d.desc, d.due, d.time, d.repeat, d.hot)
                    tab = if (!d.due.isAfter(LocalDate.now())) Tab.Today else Tab.All
                    toast.show(Copy.saved)
                }
            },
        )

        SettingsScreen(visible = settingsOpen, onClose = { settingsOpen = false }, toast = toast)

        ToastHost(toast)
        NeighOverlay(visible = neigh, onDismiss = { neigh = false })
        PermOverlay(
            visible = askPerm,
            onYes = {
                prefs.askedPermission = true
                askPerm = false
                permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onNo = {
                prefs.askedPermission = true
                askPerm = false
                toast.show("Ладно. Передумаешь — ⚙️ в шапке")
            },
        )
    }

    // Шторка и настройки ловят «назад» сами (с предпросмотром жеста). Конь поверх всего — последним.
    BackHandler(enabled = neigh) { neigh = false }
}
