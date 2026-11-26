package ru.konem.ebis.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.konem.ebis.data.TaskJson
import ru.konem.ebis.ui.UPDATES_URL
import java.time.LocalDate
import java.util.Properties
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter
import androidx.compose.ui.unit.sp
import ru.konem.ebis.ui.Copy
import ru.konem.ebis.notify.Alarm
import ru.konem.ebis.notify.NotifCopy
import java.io.File

@Composable
fun BoxScope.SettingsOverlay(state: AppState, pickFolder: () -> File?, pickColor: (Color?) -> Color?, reminders: Reminders?, compact: Boolean, pickSound: () -> File? = { null }) {
    val close = {
        state.capturing = null
        state.settingsOpen = false
    }
    Overlay(state.settingsOpen, close, keepAlive = true) {
        Column(
            Modifier
                .widthIn(max = 880.dp).fillMaxWidth()
                .heightIn(max = 680.dp).fillMaxHeight()
                .brutal(Palette.Paper, radius = 24.dp, shadow = 7.dp, border = 3.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 20.dp, top = 20.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Настройки коня", fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.weight(1f))
                CloseButton(close)
            }
            Row(Modifier.weight(1f)) {
                val navW by androidx.compose.animation.core.animateDpAsState(if (compact) 76.dp else 220.dp, Motion.smooth(), label = "navW")
                Column(
                    Modifier.width(navW).padding(start = 18.dp, end = if (compact) 6.dp else 14.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SettingsPane.entries.forEach { p -> NavItem(p, state.settingsPane == p, compact) { state.capturing = null; state.settingsPane = p } }
                }
                AnimatedContent(
                    state.settingsPane,
                    transitionSpec = { (fadeIn(tween(220)) + slideInVertically(Motion.snappy()) { 30 }) togetherWith fadeOut(tween(100)) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    label = "pane",
                ) { pane ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 8.dp, end = 24.dp, bottom = 26.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when (pane) {
                            SettingsPane.General -> GeneralPane(state)
                            SettingsPane.Tags -> TagsPane(state)
                            SettingsPane.Look -> LookPane(state, pickColor)
                            SettingsPane.Notify -> NotifyPane(state, reminders, pickSound)
                            SettingsPane.Keys -> KeysPane(state)
                            SettingsPane.Sync -> SyncPane(state, pickFolder)
                            SettingsPane.Report -> ReportPane(state)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavItem(p: SettingsPane, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val bg by animateColorAsState(if (selected) Palette.Card else if (hovered) Palette.Hover else Color.Transparent, tween(150), label = "navBg")
    val border by animateColorAsState(if (selected) Palette.Ink else Color.Transparent, tween(150), label = "navBorder")
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .brutal(bg, radius = 13.dp, shadow = if (selected) 3.dp else 0.dp, border = 2.dp, borderColor = border)
            .clickable(src, null, onClick = onClick)
            .hand()
            .padding(horizontal = if (compact) 0.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (compact) Arrangement.Center else Arrangement.spacedBy(10.dp),
    ) {
        Text(p.icon, fontSize = 17.sp)
        androidx.compose.animation.AnimatedVisibility(!compact, enter = fadeIn(tween(200, delayMillis = 80)) + androidx.compose.animation.expandHorizontally(Motion.smooth()), exit = fadeOut(tween(90)) + androidx.compose.animation.shrinkHorizontally(Motion.smooth())) {
            Text(p.title, fontWeight = FontWeight.Bold, fontSize = 15.5.sp, maxLines = 1, softWrap = false)
        }
    }
}

/** Строка настройки: название с пояснением и управление справа (или снизу, если [stack]). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Item(title: String, sub: String? = null, stack: Boolean = false, controls: @Composable () -> Unit) {
    val m = Modifier.fillMaxWidth().brutal(Palette.Card, radius = 16.dp, shadow = 0.dp, border = 2.dp).padding(horizontal = 18.dp, vertical = 14.dp)
    if (stack) {
        Column(m, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ItemText(title, sub)
            controls()
        }
    } else {
        Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f)) { ItemText(title, sub) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { controls() }
        }
    }
}

@Composable
private fun ItemText(title: String, sub: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        sub?.let { Text(it, fontSize = 14.sp, color = Palette.Mute) }
    }
}

@Composable
private fun Section(text: String) {
    Box(Modifier.padding(top = 10.dp, bottom = 2.dp)) { Label(text) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

// ── Общее ──────────────────────────────────────────────────────

@Composable
private fun GeneralPane(state: AppState) {
    val uri = LocalUriHandler.current
    val version = remember {
        AppState::class.java.getResourceAsStream("/ebis-version.properties")?.use { stream ->
            Properties().apply { load(stream) }.getProperty("version")
        }
    }
    Item(
        "Тема",
        if (Launcher.isWindows) "День, ночь или как в Windows"
        else "День, ночь или как в системе — если твой DE вообще в курсе, какая у него тема. Не в курсе — жми сам, ты же линуксоид, тебе не привыкать",
        stack = true,
    ) {
        Chips { ThemeMode.entries.forEach { m -> SelectChip(m.label, state.theme == m) { state.chooseTheme(m) } } }
    }
    Item("Масштаб интерфейса", "«Авто» подбирает под размер экрана. Если всё ещё мелко — крути. Окно растягивается за края", stack = true) {
        Chips {
            Scales.forEach { (s, label) ->
                // У «Авто» показываем, что он подобрал для этого экрана.
                val l = if (s == 0f) "$label · ${kotlin.math.round(UiScale.auto * 100).toInt()}%" else label
                SelectChip(l, (state.pendingLook ?: state.look).scale == s) { if (state.look.scale != s) state.pendingLook = state.look.copy(scale = s) }
            }
        }
    }
    Item("«Сегодня»: новые сверху", "Свежие задачи в начале списка, а не в конце") {
        BrutalSwitch(state.newFirst) { state.toggleSort() }
    }
    Item("Обновления", version?.let { "Версия $it" } ?: "Версия неизвестна", stack = true) {
        GhostChip("Скачать обновление") {
            runCatching { uri.openUri(UPDATES_URL) }.onFailure { state.show("Не удалось открыть GitHub в браузере") }
        }
    }
    Item("Резервные копии", "Перед импортом сохраняется локальная копия задач и тегов", stack = true) {
        Chips {
            GhostChip("Корзина") { state.tools = TaskTool.Trash }
            GhostChip("Сохранить в файл") { exportBackup(state) }
            GhostChip("Загрузить из файла") { loadBackup(state) }
            if (state.store.importBackup.available) {
                GhostChip("Вернуть копию до импорта") {
                    if (confirmReplace("Восстановить копию перед импортом?")) state.restoreImportBackup()
                }
            }
        }
    }
}

private fun backupChooser() = JFileChooser().apply {
    fileFilter = FileNameExtensionFilter("Бэкап задач (*.json)", "json")
}

private fun confirmReplace(title: String) = JOptionPane.showConfirmDialog(
    null, "Текущий список задач будет заменён.", title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE,
) == JOptionPane.OK_OPTION

private fun exportBackup(state: AppState) {
    val chooser = backupChooser().apply { selectedFile = File("ebis-backup-" + LocalDate.now() + ".json") }
    if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return
    val selected = chooser.selectedFile
    val file = if (selected.extension.equals("json", ignoreCase = true)) selected else File(selected.path + ".json")
    runCatching {
        require(file.canonicalFile != state.store.file?.canonicalFile)
        if (file.exists() && JOptionPane.showConfirmDialog(
                null, "Заменить выбранный файл?", "Бэкап", JOptionPane.OK_CANCEL_OPTION,
            ) != JOptionPane.OK_OPTION) return
        ru.konem.ebis.data.ImportBackup(file).save(state.data)
    }.onSuccess { state.show("Бэкап сохранён") }.onFailure { state.show("Не удалось сохранить бэкап") }
}

private fun loadBackup(state: AppState) {
    val chooser = backupChooser()
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return
    val incoming = runCatching { TaskJson.parseData(chooser.selectedFile.readText()) }.getOrElse {
        state.show("Не удалось прочитать файл задач")
        return
    }
    if (confirmReplace("Импортировать задачи из файла?")) state.importFrom(incoming)
}

// ── Оформление ─────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LookPane(state: AppState, pickColor: (Color?) -> Color?) {
    val l = state.look
    fun set(n: Look) = state.changeLook(n)
    val night = state.theme.isDark()
    val bg = l.bg(night)
    // Фон хранится отдельно для дня и ночи. Выбрал тёмный цвет днём — переключаем на ночь, иначе его не увидеть.
    fun setBg(c: Color?) {
        set(l.withBg(c, night))
        if (c != null && Look.isDarkBg(c) != night) {
            state.chooseTheme(if (Look.isDarkBg(c)) ThemeMode.Dark else ThemeMode.Light)
            state.show(if (Look.isDarkBg(c)) "Тёмный фон — включил ночь" else "Светлый фон — включил день")
        }
    }
    Item("Узор фона", "За списком задач", stack = true) {
        Chips { Pattern.entries.forEach { p -> PatternTile(p, l.pattern == p) { set(l.copy(pattern = p)) } } }
    }
    Item("Цвет фона", if (night) "Для ночи. Днём фон свой, отдельный — светлые цвета включат день" else "Для дня. Ночью фон свой, отдельный — тёмные цвета включат ночь", stack = true) {
        Chips {
            Swatch(null, bg == null, "Как в теме") { setBg(null) }
            BgPresets.forEach { (name, c) -> Swatch(c, bg == c, name) { setBg(c) } }
            CustomSwatch(bg != null && BgPresets.none { it.second == bg }) { pickColor(bg ?: Palette.Paper)?.let { setBg(it) } }
        }
    }
    Item("Цвет узора", "И насколько он заметен", stack = true) {
        Chips {
            PatternInk.entries.forEach { k -> Swatch(k.color ?: Palette.Ink, l.ink == k, k.label) { set(l.copy(ink = k)) } }
        }
        Chips { PatternStrength.entries.forEach { s -> SelectChip(s.label, l.strength == s) { set(l.copy(strength = s)) } } }
    }
    Item("Цвет шапки", "И акцентов: выбранная вкладка, кнопки, тосты", stack = true) {
        Chips {
            HeadPresets.forEach { (name, c) -> Swatch(c, l.head == c, name) { set(l.copy(head = c)) } }
            CustomSwatch(HeadPresets.none { it.second == l.head }) { pickColor(l.head)?.let { set(l.copy(head = it)) } }
        }
    }
    Item("Шрифт", "Для задач и всего интерфейса. Название остаётся фирменным", stack = true) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 2) {
            BodyFont.entries.forEach { f -> FontCard(f, l.font == f, Modifier.weight(1f)) { set(l.copy(font = f)) } }
        }
    }
    Row(Modifier.padding(top = 6.dp)) {
        BrutalButton({ set(Look(scale = l.scale)); state.show("Оформление как с завода") }) { Text("Вернуть как было", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
    }
}

@Composable
private fun PatternTile(p: Pattern, selected: Boolean, onClick: () -> Unit) {
    val lift by animateFloatAsState(if (selected) 1f else 0f, Motion.snappy(), label = "tile")
    Box(
        Modifier
            .size(100.dp, 70.dp)
            .graphicsLayer { translationX = -2.dp.toPx() * lift; translationY = -2.dp.toPx() * lift }
            .brutal(Palette.Paper, radius = 13.dp, shadow = 4.dp * lift, border = 2.5.dp)
            .clickable(onClick = onClick)
            .hand(),
    ) {
        Box(Modifier.fillMaxSize().patternBackground(p, glowRadius = 160.dp))
        Text(
            p.label, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(7.dp)
                .background(Palette.Card, RoundedCornerShape(6.dp)).border(1.5.dp, Palette.Ink, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

/** Кружок цвета; [c] = null — «как в теме» (половина дня, половина ночи). */
@Composable
private fun Swatch(c: Color?, selected: Boolean, hint: String, onClick: () -> Unit) {
    TooltipArea(hint) {
        val src = remember { MutableInteractionSource() }
        val hovered by src.collectIsHoveredAsState()
        val s by animateFloatAsState(if (hovered) 1.08f else 1f, Motion.snappy(), label = "sw")
        Box(
            Modifier
                .size(48.dp)
                .graphicsLayer { scaleX = s; scaleY = s }
                .border(2.5.dp, if (selected) Palette.Ink else Color.Transparent, CircleShape)
                .padding(4.dp)
                .clip(CircleShape)
                .background(
                    if (c == null) Brush.linearGradient(0.5f to Color(0xFFFFF8E6), 0.5f to Color(0xFF0B0B0B), start = Offset.Zero, end = Offset(100f, 100f))
                    else SolidColor(c),
                )
                .border(2.5.dp, Palette.Ink, CircleShape)
                .clickable(src, null, onClick = onClick)
                .hand(),
        )
    }
}

/** «+» на радуге — свой цвет через системную палитру. */
@Composable
private fun CustomSwatch(selected: Boolean, onClick: () -> Unit) {
    TooltipArea("Свой цвет") {
        Box(
            Modifier
                .size(48.dp)
                .border(2.5.dp, if (selected) Palette.Ink else Color.Transparent, CircleShape)
                .padding(4.dp)
                .clip(CircleShape)
                .background(Brush.sweepGradient(listOf(Color(0xFFE5383B), Color(0xFFFFD447), Color(0xFF2FA866), Color(0xFF4DA3FF), Color(0xFFB06CFF), Color(0xFFE5383B))))
                .border(2.5.dp, Palette.Ink, CircleShape)
                .clickable(onClick = onClick)
                .hand(),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(16.dp)) {
                plus(Color.Black.copy(alpha = 0.5f), 4.5.dp.toPx())
                plus(Color.White, 2.6.dp.toPx())
            }
        }
    }
}

@Composable
private fun FontCard(f: BodyFont, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Palette.AccentBg else Palette.Paper, tween(150), label = "fcBg")
    val fg by animateColorAsState(if (selected) Palette.AccentFg else Palette.Ink, tween(150), label = "fcFg")
    Column(
        modifier
            .brutal(bg, radius = 14.dp, shadow = if (selected) 3.dp else 0.dp, border = 2.5.dp)
            .clickable(onClick = onClick)
            .hand()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(f.label, fontFamily = f.family(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = fg)
        Text("${f.hint} · Купить коню овёс", fontFamily = f.family(), fontSize = 14.sp, color = fg.copy(alpha = 0.75f))
    }
}

// ── Уведомления ────────────────────────────────────────────────

@Composable
private fun NotifyPane(state: AppState, reminders: Reminders?, pickSound: () -> File?) {
    val n = state.notif
    val win = Launcher.isWindows
    Row(
        Modifier.fillMaxWidth()
            .background(androidx.compose.ui.graphics.lerp(Palette.Paper, Palette.Green, 0.16f), RoundedCornerShape(14.dp))
            .border(2.dp, Palette.Green, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(if (n.on) "✅" else "🔕", fontSize = 16.sp)
        Text(
            if (!n.on) "Уведомления выключены. Конь молчит."
            else if (win) "Приходят в центр уведомлений Windows с кнопками, как на телефоне. Работают, пока конь висит в трее."
            else "Приходят системными уведомлениями (notify-send). Работают, пока конь запущен.",
            fontSize = 14.5.sp, fontWeight = FontWeight.Medium,
        )
    }
    Item("Уведомления", "Утро, нытьё, позор, итог и напоминалки") { BrutalSwitch(n.on) { n.on = it } }
    Item("Прятаться в трей", if (Launcher.hasTray) "Крестик не закрывает, а прячет: конь продолжает пинать" else "Трея в системе нет — крестик сворачивает окно") { BrutalSwitch(n.tray) { n.tray = it } }
    Item(
        if (win) "Запускаться вместе с Windows" else "Запускаться вместе с системой",
        if (Launcher.exe != null) "Тихо, сразу в трей — чтобы утренний пинок пришёл сам"
        else if (win) "Работает в собранной версии (EbisOnoKonem.exe)" else "Работает в установленной версии (из .deb)",
    ) {
        BrutalSwitch(n.autostart && Launcher.exe != null) {
            if (Launcher.exe == null) state.show(if (win) "Автозапуск есть только у собранного exe" else "Автозапуск есть только у установленной версии")
            else { n.autostart = it; WinToast.setAutostart(it) }
        }
    }

    Section("Когда пинать")
    Item("☀️ Утренний пинок", "Сколько херни на сегодня и сколько протухло") { TimeField(n.morningMin) { n.morningMin = it } }
    Item("😩 Дневное нытьё", "Про конкретную задачу. Замолкает, когда всё сделано", stack = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Chips { listOf(0 to "Не ныть", 1 to "1 раз", 2 to "2 раза", 3 to "3 раза").forEach { (k, l) -> SelectChip(l, n.nagCount == k) { n.nagCount = k } } }
        }
        val times = NotifSettings.nagTimes(n.nagCount)
        Text(if (times.isEmpty()) "молчит" else "в " + times.joinToString(", ") { Copy.fmtTime(it) }, fontFamily = Mono, fontSize = 13.sp, color = Palette.Mute2)
    }
    Item("🪦 Позорный столб", "В ${Copy.fmtTime(NotifSettings.SHAME_MIN)} про самую протухшую задачу, если такая есть") { BrutalSwitch(n.shame) { n.shame = it } }
    Item("🌙 Вечерний итог", "Что сделал, что проебал, что ждёт завтра") {
        if (n.evening) TimeField(n.eveningMin) { n.eveningMin = it }
        BrutalSwitch(n.evening) { n.evening = it }
    }
    Item("⏰ Напоминалки по задачам", "Задачи со временем: орёт ровно в назначенную минуту") { BrutalSwitch(n.remind) { n.remind = it } }
    Item("🔊 Со звуком", if (win) "Обычный звук уведомления Windows" else "Обычный звук уведомления системы") { BrutalSwitch(n.sound) { n.sound = it } }

    Section("Будильник «Горит»")
    Item("🔥 Орать, пока не отвечу", "Задача со временем и «Горит» в свою минуту выскакивает окном поверх всех и орёт мелодией") {
        BrutalSwitch(n.alarmOn) { n.alarmOn = it }
    }
    if (n.alarmOn) {
        Item("🎵 Мелодия", "Нажми — услышишь, ещё раз — замолчит. Своя — любой mp3 или wav", stack = true) {
            var previewing by remember { mutableStateOf<String?>(null) }
            DisposableEffect(Unit) { onDispose { AlarmPlayer.stopPreview() } }
            // Настройки после закрытия остаются собранными (keepAlive), поэтому глушим по самому закрытию.
            LaunchedEffect(state.settingsOpen) {
                if (!state.settingsOpen) {
                    AlarmPlayer.stopPreview()
                    previewing = null
                }
            }
            fun pick(id: String) {
                n.alarmSound = id
                previewing = AlarmPlayer.togglePreview(id)
            }
            fun mark(id: String) = if (previewing == id) "⏹ " else "▶ "
            Chips {
                AlarmPlayer.available().forEach { s -> SelectChip(mark(s.id) + s.label, n.alarmSound == s.id) { pick(s.id) } }
                val own = n.alarmCustomName?.takeIf { AlarmPlayer.customFile() != null }
                if (own != null) SelectChip(mark(Alarm.CUSTOM) + own, n.alarmSound == Alarm.CUSTOM) { pick(Alarm.CUSTOM) }
                SelectChip(if (own == null) "📁 Свой звук…" else "📁 Другой файл…", false) {
                    AlarmPlayer.stopPreview()
                    previewing = null
                    val f = pickSound() ?: return@SelectChip
                    if (AlarmPlayer.saveCustom(f)) {
                        n.alarmCustomName = f.name
                        pick(Alarm.CUSTOM)
                        state.show("Теперь будильник орёт «${f.name}»")
                    } else {
                        state.show("Это не mp3 и не wav. Конь такое не сыграет")
                    }
                }
            }
        }
        Item("⏰ «Потом» — это через", "Кнопка на будильнике. Чем чаще жмёшь, тем злее конь", stack = true) {
            Chips { Alarm.snoozeOptions.forEach { m -> SelectChip(NotifCopy.minutes(m), n.snoozeMin == m) { n.snoozeMin = m } } }
        }
        Item("📈 Громкость нарастает", "От тихого к полному за 20 секунд, чтобы не подскочить со стула") { BrutalSwitch(n.alarmRamp) { n.alarmRamp = it } }
        Item("🧪 Заорать сейчас", "Покажет тревогу с примером задачи. Закрыть — любой кнопкой") {
            SelectChip("🔥 Заорать", false) {
                AlarmPlayer.stopPreview()
                reminders?.ring(null)
            }
        }
    }

    Section("Проверить прямо сейчас")
    Item("🧪 Прислать уведомление", "С твоими задачами, как придёт по-настоящему", stack = true) {
        Chips {
            Slot.entries.forEach { s ->
                SelectChip(s.label, false) {
                    if (reminders == null) return@SelectChip
                    reminders.fire(s, force = true)
                    state.show("Отправил «${s.label}» — глянь в угол экрана")
                }
            }
            SelectChip("Напоминалка", false) {
                reminders?.remind(-1, force = true)
                state.show("Отправил напоминалку — глянь в угол экрана")
            }
        }
    }
}

/** Время «09:00»: печатаешь — сохраняется, как только время понятно. */
@Composable
private fun TimeField(min: Int, onChange: (Int) -> Unit) {
    var text by remember(min) { mutableStateOf(Copy.fmtTime(min).padStart(5, '0')) }
    var focused by remember { mutableStateOf(false) }
    val bad = parseTime(text).getOrNull() == null
    Box(
        Modifier.width(92.dp)
            .brutal(Palette.Paper, radius = 11.dp, shadow = if (focused) 3.dp else 0.dp, border = 2.dp, borderColor = if (bad && !focused) Palette.Red else Palette.Ink)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            text,
            onValueChange = { v ->
                text = v.take(5)
                parseTime(text).getOrNull()?.let(onChange)
            },
            singleLine = true,
            textStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Palette.Ink),
            cursorBrush = SolidColor(Palette.Ink),
            modifier = Modifier.onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) text = Copy.fmtTime(min).padStart(5, '0')
            },
        )
    }
}

// ── Клавиши ────────────────────────────────────────────────────

@Composable
private fun KeysPane(state: AppState) {
    Text(
        "Нажми «Изменить», потом нужное сочетание. Esc — отмена. Если сочетание уже занято, поменяю местами.",
        fontSize = 14.5.sp, color = Palette.Mute, modifier = Modifier.padding(bottom = 4.dp),
    )
    Action.entries.forEach { a -> KeyRow(state, a) }
    Row(
        Modifier.fillMaxWidth().brutal(Palette.Card, radius = 14.dp, shadow = 0.dp, border = 2.dp).graphicsLayer { alpha = 0.7f }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Закрыть окно или диалог", fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
            Text("Не переназначается", fontSize = 13.5.sp, color = Palette.Mute)
        }
        Keycaps("Esc")
        Spacer(Modifier.width(110.dp))
    }
    Row(Modifier.padding(top = 6.dp)) {
        BrutalButton({ state.capturing = null; state.keys.reset(); state.show("Клавиши как с завода") }) { Text("Вернуть как было", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
    }
}

@Composable
private fun KeyRow(state: AppState, a: Action) {
    val cap = state.capturing == a
    val border by animateColorAsState(if (cap) Palette.Red else Palette.Ink, tween(150), label = "krBorder")
    Row(
        Modifier.fillMaxWidth()
            .brutal(Palette.Card, radius = 14.dp, shadow = if (cap) 3.dp else 0.dp, border = 2.dp, borderColor = border, shadowColor = Palette.Red)
            .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(a.label, fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
            if (a.hint.isNotEmpty()) Text(a.hint, fontSize = 13.5.sp, color = Palette.Mute)
        }
        if (cap) BlinkingKeycaps() else Keycaps(state.keys[a])
        BrutalButton(
            { state.capturing = if (cap) null else a },
            bg = Palette.Paper, radius = 10.dp, shadow = 2.dp, border = 2.dp,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 7.dp),
            modifier = Modifier.width(110.dp),
        ) { Text(if (cap) "Отмена" else "Изменить", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
}

@Composable
private fun BlinkingKeycaps() {
    val blink = rememberInfiniteTransition(label = "blink").animateFloat(1f, 0.35f, infiniteRepeatable(tween(500), androidx.compose.animation.core.RepeatMode.Reverse), label = "b")
    Keycaps("жми…", Modifier.graphicsLayer { alpha = blink.value })
}

// ── Синхронизация ──────────────────────────────────────────────

@Composable
private fun SyncPane(state: AppState, pickFolder: () -> File?) {
    Item("Файл задач", "Тот же ${DesktopStore.FILE_NAME}, что выбран в телефоне. Синхронизируй папку чем угодно — Syncthing, облаком — и правки с обеих сторон сольются по задачам.", stack = true) {
        state.folder?.let {
            Text(
                File(it, DesktopStore.FILE_NAME).absolutePath, fontFamily = Mono, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().brutal(Palette.Paper, radius = 10.dp, shadow = 0.dp, border = 2.dp).padding(12.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BrutalButton({ pickFolder()?.let { state.connect(it) } }) { Text("📁 Сменить папку", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            state.folder?.let { dir ->
                BrutalButton({ openFolder(dir) }) { Text("Открыть в проводнике", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            }
        }
    }
}

// ── Сообщить о проблеме ────────────────────────────────────────

@Composable
private fun ReportPane(state: AppState) {
    val dir = Log.dir
    Item(
        "Сообщить о проблеме",
        "Конь споткнулся — сразу, пока не забыл, открой папку ниже и скинь разрабу файлы ebis.log (и ebis.1.log, если есть). " +
            "Плюс пару слов: что жал и что пошло не так. В журнале — что делало приложение, ошибки и сведения о системе. Названий задач там нет.",
        stack = true,
    ) {
        Text(
            dir.absolutePath, fontFamily = Mono, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().brutal(Palette.Paper, radius = 10.dp, shadow = 0.dp, border = 2.dp).padding(12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BrutalButton({
                dir.mkdirs()
                Log.i("report", "Открыли папку с журналом из настроек")
                openFolder(dir)
            }) { Text(if (Launcher.isWindows) "📂 Открыть в проводнике" else "📂 Открыть папку", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            BrutalButton({
                copy(dir.absolutePath)
                state.show("Путь скопирован")
            }) { Text("Скопировать путь", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        }
    }
    Item("Скопировать журнал целиком", "Если файлы слать неудобно: последние 400 строк в буфер обмена, дальше вставляй прямо в сообщение") {
        BrutalButton({
            Log.i("report", "Скопировали журнал из настроек")
            copy(Log.tail(400))
            state.show("Журнал в буфере — вставляй")
        }) { Text("📋 Копировать", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
    }
    Text(
        "Версия ${Log.version} · ${System.getProperty("os.name")} ${System.getProperty("os.version")} · Java ${System.getProperty("java.version")}",
        fontFamily = Mono, fontSize = 12.5.sp, color = Palette.Mute, modifier = Modifier.padding(top = 4.dp, start = 4.dp),
    )
}

private fun copy(text: String) {
    runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(text), null) }
        .onFailure { Log.w("report", "Не вышло положить в буфер обмена", it) }
}

/** Открыть папку в файловом менеджере: Desktop API на Windows, xdg-open на Linux, где Desktop часто не поддерживается. */
private fun openFolder(dir: File) {
    val ok = runCatching {
        if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
            java.awt.Desktop.getDesktop().open(dir); true
        } else false
    }.getOrDefault(false)
    if (!ok) runCatching { ProcessBuilder("xdg-open", dir.absolutePath).start() }.onFailure { Log.w("ui", "Не открылась папка ни через Desktop, ни через xdg-open", it) }
}
