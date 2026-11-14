@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ru.konem.ebis.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CancellationException
import ru.konem.ebis.data.SyncFile
import ru.konem.ebis.data.TaskRepo
import ru.konem.ebis.data.TaskStore
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.provider.OpenableColumns
import ru.konem.ebis.notify.Alarm
import ru.konem.ebis.notify.AlarmService
import ru.konem.ebis.notify.AlarmSounds
import ru.konem.ebis.notify.NotifCopy
import ru.konem.ebis.notify.Notifier
import ru.konem.ebis.notify.Prefs
import ru.konem.ebis.notify.Scheduler
import ru.konem.ebis.notify.Slot
import ru.konem.ebis.ui.theme.Display
import ru.konem.ebis.ui.theme.Mono
import ru.konem.ebis.ui.theme.Palette
import ru.konem.ebis.ui.theme.ThemeMode
import java.time.LocalDate

@Composable
fun SettingsScreen(visible: Boolean, onClose: () -> Unit, toast: ToastState) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(Motion.screenOffset) { it } + fadeIn(tween(200)),
        exit = slideOutHorizontally(tween(260)) { it } + fadeOut(tween(260)),
    ) {
        // Жест «назад» тянет экран за пальцем, как в системе.
        val back = remember { Animatable(0f) }
        PredictiveBackHandler(enabled = visible) { progress ->
            try {
                progress.collect { e -> back.snapTo(e.progress) }
                onClose()
            } catch (e: CancellationException) {
                back.animateTo(0f, Motion.smooth())
            }
        }
        Box(
            Modifier.graphicsLayer {
                translationX = back.value * size.width * 0.12f
                val sc = 1f - 0.06f * back.value
                scaleX = sc; scaleY = sc
                shape = RoundedCornerShape((24 * back.value).dp)
                clip = back.value > 0f
            },
        ) {
            SettingsContent(onClose, toast)
        }
    }
}

@Composable
private fun SettingsContent(onClose: () -> Unit, toast: ToastState) {
    val onToast: (String) -> Unit = { toast.show(it) }
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val today = LocalDate.now().toEpochDay()

    var morning by remember { mutableIntStateOf(prefs.morningMin) }
    var nagCount by remember { mutableIntStateOf(prefs.nagCount) }
    var shameOn by remember { mutableStateOf(prefs.shameOn) }
    var eveningOn by remember { mutableStateOf(prefs.eveningOn) }
    var evening by remember { mutableIntStateOf(prefs.eveningMin) }
    var mutedDay by remember { mutableLongStateOf(prefs.mutedDay) }
    var picking by remember { mutableStateOf<String?>(null) }
    var welcomeOn by remember { mutableStateOf(prefs.welcomeOn) }
    var alarmOn by remember { mutableStateOf(prefs.alarmOn) }
    var alarmSound by remember { mutableStateOf(prefs.alarmSound) }
    var customName by remember { mutableStateOf(prefs.alarmCustomName.takeIf { AlarmSounds.hasCustom(ctx) }) }
    var snoozeMin by remember { mutableIntStateOf(prefs.snoozeMin) }
    var alarmRamp by remember { mutableStateOf(prefs.alarmRamp) }
    var alarmVibrate by remember { mutableStateOf(prefs.alarmVibrate) }
    var previewing by remember { mutableStateOf<String?>(null) }
    val sounds = remember { AlarmSounds.available(ctx) }
    DisposableEffect(Unit) { onDispose { AlarmSounds.stopPreview() } }

    fun pickSound(id: String) {
        alarmSound = id
        prefs.alarmSound = id
        previewing = AlarmSounds.togglePreview(ctx, id)
    }
    val soundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        AlarmSounds.stopPreview()
        previewing = null
        if (!AlarmSounds.saveCustom(ctx, uri)) {
            onToast("Это не звук. Конь такое не сыграет")
            return@rememberLauncherForActivityResult
        }
        val name = runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: "свой звук"
        prefs.alarmCustomName = name
        customName = name
        pickSound(Alarm.CUSTOM)
        onToast("Теперь будильник орёт «$name»")
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            ctx.contentResolver.openOutputStream(uri)!!.use { it.write(TaskStore.backup(TaskRepo.tasks).toByteArray()) }
        }.isSuccess
        val n = TaskRepo.tasks.size
        onToast(if (ok) "Сохранил $n ${Copy.plural(n, "задачу", "задачи", "задач")}. Береги файл" else "Не смог записать файл. Конь в шоке")
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val loaded = runCatching {
            TaskStore.parse(ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) })
        }.getOrNull()
        if (loaded == null) {
            onToast("Это не бэкап коня. Подсунь нормальный файл")
        } else {
            val before = TaskRepo.snapshot()
            TaskRepo.replaceAll(loaded)
            val n = loaded.size
            toast.show("Загрузил $n ${Copy.plural(n, "задачу", "задачи", "задач")}", Copy.undoLabel) { TaskRepo.restore(before) }
        }
    }

    val sync = TaskRepo.sync
    var syncLabel by remember { mutableStateOf(sync.folderLabel()) }
    var syncError by remember { mutableStateOf(sync.lastError) }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree == null) return@rememberLauncherForActivityResult
        TaskRepo.connectSync(
            tree,
            onFound = { n, before ->
                // В папке уже есть задачи (с ПК или до переустановки) — слили со своими, «Верни» откатит.
                syncLabel = sync.folderLabel()
                syncError = null
                toast.show("В папке уже был файл — теперь $n ${Copy.plural(n, "задача", "задачи", "задач")}", Copy.undoLabel) {
                    TaskRepo.restore(before)
                }
            },
            onDone = { ok ->
                syncLabel = sync.folderLabel()
                syncError = sync.lastError
                onToast(if (ok) "Готово. Теперь каждая херня сразу летит в ${SyncFile.FILE_NAME}" else "Не смог писать в эту папку. Выбери другую")
            },
        )
    }

    // Разрешения могут поменяться, пока мы в системных настройках — перечитываем при возврате.
    var enabled by remember { mutableStateOf(Notifier.canPost(ctx)) }
    val pm = remember { ctx.getSystemService(PowerManager::class.java) }
    var batteryOk by remember { mutableStateOf(pm?.isIgnoringBatteryOptimizations(ctx.packageName) ?: true) }
    var fullScreenOk by remember { mutableStateOf(Notifier.canFullScreen(ctx)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                enabled = Notifier.canPost(ctx)
                syncError = sync.lastError
                batteryOk = pm?.isIgnoringBatteryOptimizations(ctx.packageName) ?: true
                fullScreenOk = Notifier.canFullScreen(ctx)
            }
            // Свернул приложение посреди прослушивания — мелодия не должна играть дальше.
            if (e == Lifecycle.Event.ON_PAUSE) {
                AlarmSounds.stopPreview()
                previewing = null
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val openNotifSettings = {
        ctx.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        enabled = Notifier.canPost(ctx)
        if (!granted) openNotifSettings()
    }

    fun save(block: Prefs.() -> Unit) {
        prefs.block()
        Scheduler.scheduleAll(ctx)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Paper)
            .clickable(remember { MutableInteractionSource() }, null) {},
    ) {
        HayBar {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Настройки коня", fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.weight(1f))
                HeaderButton(onClick = onClose, size = 34.dp) { Text("✕", fontSize = 16.sp) }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!enabled) {
                Warn(
                    "🔇 Уведомления выключены",
                    "Конь немой, пока не разрешишь. Ни пинков, ни нытья, ни позора.",
                    "Разрешить",
                ) {
                    if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else openNotifSettings()
                }
            }
            if (mutedDay == today) {
                Warn("🤐 Сегодня конь заткнут", "Ты нажал «Отъебись на сегодня». До завтрашнего утра тишина.", "Размьютить") {
                    save { this.mutedDay = -1 }
                    mutedDay = -1
                }
            }

            Section("🌗 Тема", "Днём — сено, ночью — чёрная конюшня") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ThemeMode.SYSTEM to "Как в системе",
                        ThemeMode.LIGHT to "☀️ День",
                        ThemeMode.DARK to "🌙 Ночь в конюшне",
                    ).forEach { (mode, label) ->
                        SelectChip(label, ThemeMode.mode == mode) {
                            ThemeMode.mode = mode
                            prefs.themeMode = mode
                        }
                    }
                }
            }

            Section("👋 Приветствие", "«Ну здарова, бедолага» при запуске и после 5 минут в фоне") {
                BrutalSwitch(welcomeOn) {
                    welcomeOn = it
                    prefs.welcomeOn = it
                }
            }

            Section(
                "🔄 Синхронизация с ПК",
                syncLabel?.let { "Задачи сразу пишутся в $it/${SyncFile.FILE_NAME}. Поменял файл на ПК — подтянется, когда вернёшься в приложение" }
                    ?: "Выбери папку, и задачи будут сразу сохраняться в ${SyncFile.FILE_NAME}. Синхронизируй её с ПК чем угодно — Syncthing, облаком",
            ) {
                syncError?.let {
                    Text("⚠️ Последняя запись не удалась: $it", fontSize = 12.sp, color = Palette.Red)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip(if (syncLabel == null) "📁 Выбрать папку" else "📁 Сменить папку", false) {
                        folderLauncher.launch(sync.folder)
                    }
                    if (syncLabel != null) {
                        SelectChip("Отключить", false) {
                            sync.disconnect()
                            syncLabel = null
                            syncError = null
                            onToast("Отвязал папку. Файл там остался, но больше не обновляется")
                        }
                    }
                }
            }

            Section("💾 Бэкап", "Все задачи в один файл. Переустановил или сменил телефон — загрузи обратно") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip("⬆️ Сохранить в файл", false) {
                        exportLauncher.launch("ebis-backup-${LocalDate.now()}.json")
                    }
                    SelectChip("⬇️ Загрузить из файла", false) {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    }
                }
            }

            Text(
                "УВЕДОМЛЕНИЯ", fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.5.sp, color = Palette.Mute2,
                modifier = Modifier.padding(top = 6.dp),
            )

            Section("☀️ Утренний пинок", "Сколько херни на сегодня и сколько протухло") {
                TimeChip(morning) { picking = "morning" }
            }

            Section("😩 Дневное нытьё", "Про конкретную задачу. Замолкает, когда всё сделано") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Не ныть", 1 to "1 раз", 2 to "2 раза", 3 to "3 раза").forEach { (n, label) ->
                        SelectChip(label, nagCount == n) {
                            nagCount = n
                            save { this.nagCount = n }
                        }
                    }
                }
                val times = Prefs.nagTimes(nagCount)
                if (times.isNotEmpty()) {
                    Text("в " + times.joinToString(", ") { Prefs.fmt(it) }, fontFamily = Mono, fontSize = 11.sp, color = Palette.Mute2)
                }
            }

            Section("🪦 Позорный столб", "В ${Prefs.fmt(Prefs.SHAME_MIN)} про самую протухшую задачу, если такая есть") {
                BrutalSwitch(shameOn) {
                    shameOn = it
                    save { this.shameOn = it }
                }
            }

            Section("🌙 Вечерний итог", "Что сделал, что проебал, что ждёт завтра") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BrutalSwitch(eveningOn) {
                        eveningOn = it
                        save { this.eveningOn = it }
                    }
                    if (eveningOn) TimeChip(evening) { picking = "evening" }
                }
            }

            Text(
                "БУДИЛЬНИК «ГОРИТ»", fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.5.sp, color = Palette.Mute2,
                modifier = Modifier.padding(top = 6.dp),
            )

            Section("🔥 Орать, пока не отвечу", "Задача со временем и «Горит» в назначенную минуту орёт мелодией. В беззвучном режиме молчит, в режиме вибрации только вибрирует") {
                BrutalSwitch(alarmOn) {
                    alarmOn = it
                    save { this.alarmOn = it }
                }
            }

            if (alarmOn) {
                if (!fullScreenOk) {
                    Warn(
                        "📵 Тревога не выскочит на весь экран",
                        "Android не даёт показывать её поверх блокировки, пока не разрешишь. Без этого будет только уведомление со звуком.",
                        "Разрешить",
                    ) {
                        runCatching {
                            ctx.startActivity(
                                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }.onFailure { openNotifSettings() }
                    }
                }

                Section("🎵 Мелодия", "Нажми — услышишь, ещё раз — замолчит. Своя — любой mp3, ogg или wav с телефона") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        sounds.forEach { s ->
                            SelectChip((if (previewing == s.id) "⏹ " else "▶ ") + s.label, alarmSound == s.id) { pickSound(s.id) }
                        }
                        val own = customName
                        if (own != null) {
                            SelectChip((if (previewing == Alarm.CUSTOM) "⏹ " else "▶ ") + own, alarmSound == Alarm.CUSTOM) { pickSound(Alarm.CUSTOM) }
                        }
                        SelectChip(if (own == null) "📁 Свой звук…" else "📁 Другой файл…", false, dashed = true) {
                            soundLauncher.launch(arrayOf("audio/*"))
                        }
                    }
                }

                Section("⏰ «Потом» — это через", "Кнопка на будильнике. Чем чаще жмёшь, тем злее конь") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Alarm.snoozeOptions.forEach { m ->
                            SelectChip(NotifCopy.minutes(m), snoozeMin == m) {
                                snoozeMin = m
                                prefs.snoozeMin = m
                            }
                        }
                    }
                }

                Section("📈 Громкость нарастает", "От тихого к полному за 20 секунд, чтобы не подскочить") {
                    BrutalSwitch(alarmRamp) {
                        alarmRamp = it
                        prefs.alarmRamp = it
                    }
                }

                Section("📳 Вибрация", "Вместе с мелодией") {
                    BrutalSwitch(alarmVibrate) {
                        alarmVibrate = it
                        prefs.alarmVibrate = it
                    }
                }

                Section("🧪 Заорать сейчас", "Покажет тревогу с примером задачи. Закрыть — любой кнопкой") {
                    SelectChip("🔥 Заорать", false) {
                        AlarmSounds.stopPreview()
                        previewing = null
                        if (!AlarmService.test(ctx)) onToast("Android не дал запустить будильник. Конь в шоке")
                    }
                }
            }

            Section("🧪 Проверить прямо сейчас", "Пришлёт уведомление с твоими задачами") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Slot.Morning to "Утро", Slot.Nag1 to "Нытьё", Slot.Shame to "Позор", Slot.Evening to "Итог").forEach { (slot, label) ->
                        SelectChip(label, false) {
                            if (!enabled) onToast("Сначала разреши уведомления, бедолага")
                            else Notifier.fire(ctx, slot, force = true)
                        }
                    }
                }
            }

            if (!batteryOk) {
                Warn(
                    "🔋 Батарея может душить коня",
                    "На Xiaomi, Samsung и Huawei система убивает фоновые будильники. Разреши работу без ограничений, иначе уведомления будут опаздывать или пропадать.",
                    "Разрешить",
                ) {
                    try {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: Exception) {
                        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }

            // ColorOS/MIUI и прочие не дают будильнику поднять убитое приложение без «автозапуска».
            // Проверить его из кода нельзя, поэтому на этих прошивках подсказка висит всегда.
            if (Build.MANUFACTURER.lowercase() in AUTOSTART_VENDORS) {
                Warn(
                    "🚀 Включи автозапуск",
                    "На ${Build.MANUFACTURER} смахнутое из недавних приложение не может проснуться само — будильник срабатывает, а конь молчит. " +
                        "Открой «Использование батареи» и включи «Автозапуск» и «Работа в фоне».",
                    "Открыть",
                ) {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    }

    picking?.let { which ->
        TimeDialog(
            title = "Во сколько пинать?",
            initial = if (which == "morning") morning else evening,
            onDismiss = { picking = null },
            onPick = { m ->
                if (which == "morning") { morning = m; save { morningMin = m } }
                else { evening = m; save { eveningMin = m } }
                picking = null
            },
        )
    }
}

private val AUTOSTART_VENDORS = setOf("oneplus", "oppo", "realme", "xiaomi", "redmi", "poco", "huawei", "honor", "vivo", "meizu")

@Composable
private fun Section(title: String, hint: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().brutal(Palette.Card, radius = 14.dp, shadow = 3.dp).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column {
            Text(title, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(hint, fontSize = 12.5.sp, color = Palette.Mute, modifier = Modifier.padding(top = 2.dp))
        }
        content()
    }
}

@Composable
private fun Warn(title: String, text: String, button: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.ErrorBg, RoundedCornerShape(14.dp))
            .dashedBorder(2.dp, Palette.Red, 14.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(text, fontSize = 13.sp, color = Palette.Mute)
        Text(
            button, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.5.sp,
            modifier = Modifier
                .background(Palette.Red, RoundedCornerShape(10.dp))
                .border(2.dp, Palette.Ink, RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun TimeChip(min: Int, onClick: () -> Unit) {
    Text(
        "⏰ ${Prefs.fmt(min)}",
        fontFamily = Mono, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.OnHay,
        modifier = Modifier
            .brutal(Palette.Hay, radius = 12.dp, shadow = 2.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}



