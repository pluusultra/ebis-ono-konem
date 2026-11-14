package ru.konem.ebis.desktop

import kotlinx.coroutines.delay
import ru.konem.ebis.data.DaySnapshot
import ru.konem.ebis.data.Task
import ru.konem.ebis.ui.Copy
import ru.konem.ebis.notify.NotifCopy
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.concurrent.Executors
import java.util.prefs.Preferences

/** Чем запущено приложение: exe из сборки (jpackage) или java из Gradle / переносного jar. */
object Launcher {
    val isWindows = System.getProperty("os.name").lowercase().contains("win")

    /** Трей есть: D-Bus (Linux) или обычный Java. В GNOME без расширения AppIndicator трея нет: прятать окно туда нельзя — его потом не достать. */
    val hasTray: Boolean get() = LinuxTray.active || awtTray
    private val awtTray: Boolean by lazy { runCatching { java.awt.SystemTray.isSupported() }.getOrDefault(false) }

    /**
     * exe собранного приложения; null при запуске через Gradle или `java -jar`.
     * В AppImage внутренний путь меняется при каждом запуске, поэтому для автозапуска нужен сам файл .AppImage.
     */
    val exe: String? = System.getenv("APPIMAGE")?.takeIf { File(it).exists() }
        ?: System.getProperty("jpackage.app-path")?.takeIf { File(it).exists() }

    /** Команда, которой Windows запустит нас по клику на кнопку уведомления. */
    fun command(arg: String): String {
        exe?.let { return "\"$it\" \"$arg\"" }
        val java = ProcessHandle.current().info().command().orElse("java")
        val javaw = File(File(java).parentFile, "javaw.exe").takeIf { it.exists() }?.absolutePath ?: java
        // Classpath из Gradle длиннее предела командной строки Windows — кладём его в @-файл для java.
        val args = File(DesktopStore.defaultFolder(), "launch.args")
        runCatching {
            args.parentFile.mkdirs()
            val cp = System.getProperty("java.class.path").replace('\\', '/')
            args.writeText("-cp \"$cp\"\nru.konem.ebis.desktop.MainKt\n")
        }
        return "\"$javaw\" \"@${args.absolutePath}\" \"$arg\""
    }
}

/** Linux: уведомления через notify-send (работает и без трея, например в GNOME) и автозапуск через ~/.config/autostart. */
object LinuxDesk {
    private val home = System.getProperty("user.home")
    private val config = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let(::File) ?: File(home, ".config")
    private val autostartFile = File(config, "autostart/ebis-ono-konem.desktop")
    private val icon by lazy {
        File(DesktopStore.defaultFolder(), "notif/logo.png").also { f ->
            if (!f.exists()) runCatching {
                f.parentFile.mkdirs()
                WinToast::class.java.getResourceAsStream("/notif/logo.png")?.use { f.writeBytes(it.readBytes()) }
            }
        }
    }

    /** true — notify-send есть и отработал. */
    fun notify(title: String, text: String, sound: Boolean): Boolean = runCatching {
        val cmd = mutableListOf("notify-send", "-a", "Ебись оно конём")
        if (icon.exists()) cmd += listOf("-i", icon.absolutePath)
        if (sound) cmd += listOf("-h", "string:sound-name:message-new-instant")
        cmd += listOf(title, text)
        val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val out = p.inputStream.readAllBytes().toString(Charsets.UTF_8).trim()
        val code = p.waitFor()
        if (code != 0) Log.w("notify", "notify-send вернул $code: $out")
        code == 0
    }.getOrElse {
        Log.w("notify", "notify-send не запустился: $it")
        false
    }

    /** Автозапуск только у установленной версии: у неё есть свой исполняемый файл. */
    fun autostart(on: Boolean) {
        runCatching {
            val exe = Launcher.exe
            if (!on || exe == null) {
                autostartFile.delete()
                return
            }
            autostartFile.parentFile.mkdirs()
            autostartFile.writeText(
                """
                [Desktop Entry]
                Type=Application
                Name=Ебись оно конём
                Exec="$exe" --hidden
                Icon=${icon.absolutePath}
                Terminal=false
                X-GNOME-Autostart-enabled=true
                """.trimIndent() + "\n",
            )
        }
    }
}

/**
 * Уведомления Windows с кнопками. Windows показывает имя и значок приложения только тем, у кого
 * есть ярлык в меню «Пуск» с тем же AppUserModelID, поэтому при запуске кладём туда свой ярлык.
 * Кнопки — ссылки ebis-konem://…: Windows запускает нас с ней, а [SingleInstance] передаёт её уже открытому окну.
 * Сами уведомления показывает Windows PowerShell 5.1 через WinRT — так не нужны нативные библиотеки.
 */
object WinToast {
    const val AUMID = "Konem.EbisOnoKonem"
    const val SCHEME = "ebis-konem"
    private const val APP_NAME = "Ебись оно конём"

    /** Для Linux и на случай, если PowerShell недоступен: простое уведомление из трея. */
    var fallback: ((title: String, text: String) -> Unit)? = null

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "ebis-toast").apply { isDaemon = true } }
    private val dir get() = File(DesktopStore.defaultFolder(), "notif")
    private val logo get() = File(dir, "logo.png")

    class Btn(val label: String, val cmd: String)

    /** Ярлык, ссылка ebis-konem:// и автозапуск. Тяжёлое (компиляция C# в PowerShell) — только если что-то поменялось. */
    fun setup(prefs: Preferences, autostart: Boolean) {
        if (!Launcher.isWindows) {
            worker.execute { LinuxDesk.autostart(autostart) }
            return
        }
        worker.execute {
            runCatching {
                dir.mkdirs()
                listOf("logo.png", "icon.ico").forEach { name ->
                    val f = File(dir, name)
                    // Перезаписываем, если значок в сборке поменялся.
                    val bytes = WinToast::class.java.getResourceAsStream("/notif/$name")?.use { it.readBytes() }
                    if (bytes != null && !(f.exists() && f.readBytes().contentEquals(bytes))) f.writeBytes(bytes)
                }
                val target = Launcher.exe ?: Launcher.command("").substringAfter('"').substringBefore('"')
                val icon = Launcher.exe ?: File(dir, "icon.ico").absolutePath
                val open = Launcher.command("%1")
                val sign = "4|$target|$open"
                if (prefs.get("win.setup", "") != sign) {
                    if (ps(setupScript(target, icon, open)) == 0) {
                        prefs.put("win.setup", sign)
                        runCatching { prefs.flush() }
                    }
                }
            }
            applyAutostart(autostart)
        }
    }

    /** Запуск вместе с Windows: только у собранного exe, иначе запускать нечего. */
    fun setAutostart(on: Boolean) {
        worker.execute { if (Launcher.isWindows) applyAutostart(on) else LinuxDesk.autostart(on) }
    }

    private fun applyAutostart(on: Boolean) {
        val exe = Launcher.exe ?: return
        val key = "HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
        ps(
            if (on) "Set-ItemProperty -Path '$key' -Name 'EbisOnoKonem' -Value (${b64("\"$exe\" --hidden")})"
            else "Remove-ItemProperty -Path '$key' -Name 'EbisOnoKonem' -ErrorAction SilentlyContinue",
        )
    }

    fun show(title: String, text: String, attribution: String? = null, buttons: List<Btn> = emptyList(), tag: String, sound: Boolean, reminder: Boolean = false) {
        if (!Launcher.isWindows) {
            worker.execute { if (!LinuxDesk.notify(title, text, sound)) fallback?.invoke(title, text) }
            return
        }
        val xml = buildString {
            append("<toast activationType=\"protocol\" launch=\"$SCHEME://open\"")
            if (reminder && buttons.isNotEmpty()) append(" scenario=\"reminder\"")
            append("><visual><binding template=\"ToastGeneric\">")
            if (logo.exists()) append("<image placement=\"appLogoOverride\" hint-crop=\"circle\" src=\"${esc(logo.toURI().toString())}\"/>")
            append("<text>${esc(title)}</text><text>${esc(text)}</text>")
            attribution?.let { append("<text placement=\"attribution\">${esc(it)}</text>") }
            append("</binding></visual>")
            if (buttons.isNotEmpty()) {
                append("<actions>")
                buttons.forEach { append("<action content=\"${esc(it.label)}\" activationType=\"protocol\" arguments=\"${esc(it.cmd)}\"/>") }
                append("</actions>")
            }
            append(if (sound) "<audio src=\"ms-winsoundevent:Notification.Default\"/>" else "<audio silent=\"true\"/>")
            append("</toast>")
        }
        worker.execute {
            val code = ps(
                """
                [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
                [Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType = WindowsRuntime] | Out-Null
                ${'$'}doc = New-Object Windows.Data.Xml.Dom.XmlDocument
                ${'$'}doc.LoadXml(${b64(xml)})
                ${'$'}t = New-Object Windows.UI.Notifications.ToastNotification ${'$'}doc
                ${'$'}t.Tag = '${tag.take(60).filter { it.isLetterOrDigit() || it == '-' }}'
                ${'$'}t.Group = 'ebis'
                [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('$AUMID').Show(${'$'}t)
                """.trimIndent(),
            )
            if (code != 0) fallback?.invoke(title, text)
        }
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** Строка для PowerShell без проблем с кавычками и кодировкой: декодируется из base64 UTF-8. */
    private fun b64(s: String) = "[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('${Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))}'))"

    /** Запускает скрипт в Windows PowerShell 5.1. Скрипт передаётся как -EncodedCommand (UTF-16), чтобы кириллица дошла целой. */
    private fun ps(script: String): Int = runCatching {
        val enc = Base64.getEncoder().encodeToString(("\$ErrorActionPreference = 'Stop'\n" + script).toByteArray(Charsets.UTF_16LE))
        val p = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-EncodedCommand", enc)
            .redirectErrorStream(true)
            .start()
        val out = p.inputStream.readAllBytes().toString(Charsets.UTF_8).trim()
        p.waitFor().also { code -> if (code != 0) Log.w("toast", "PowerShell вернул $code: ${out.take(800)}") }
    }.getOrElse {
        Log.w("toast", "PowerShell не запустился: $it")
        -1
    }

    private fun setupScript(target: String, icon: String, open: String): String = """
        Add-Type -TypeDefinition @'
        using System;
        using System.Runtime.InteropServices;
        public static class EbisLink {
          [ComImport, Guid("000214F9-0000-0000-C000-000000000046"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
          interface IShellLinkW {
            void GetPath(IntPtr a, int b, IntPtr c, int d); void GetIDList(out IntPtr p); void SetIDList(IntPtr p);
            void GetDescription(IntPtr a, int b); void SetDescription([MarshalAs(UnmanagedType.LPWStr)] string s);
            void GetWorkingDirectory(IntPtr a, int b); void SetWorkingDirectory([MarshalAs(UnmanagedType.LPWStr)] string s);
            void GetArguments(IntPtr a, int b); void SetArguments([MarshalAs(UnmanagedType.LPWStr)] string s);
            void GetHotkey(out short h); void SetHotkey(short h); void GetShowCmd(out int c); void SetShowCmd(int c);
            void GetIconLocation(IntPtr a, int b, out int c); void SetIconLocation([MarshalAs(UnmanagedType.LPWStr)] string s, int i);
            void SetRelativePath([MarshalAs(UnmanagedType.LPWStr)] string s, int r); void Resolve(IntPtr h, int f);
            void SetPath([MarshalAs(UnmanagedType.LPWStr)] string s);
          }
          [ComImport, Guid("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
          interface IPropertyStore {
            void GetCount(out uint c); void GetAt(uint i, out PropertyKey k); void GetValue(ref PropertyKey k, out PropVariant v);
            void SetValue(ref PropertyKey k, ref PropVariant v); void Commit();
          }
          [ComImport, Guid("0000010b-0000-0000-C000-000000000046"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
          interface IPersistFile {
            void GetClassID(out Guid g); [PreserveSig] int IsDirty();
            void Load([MarshalAs(UnmanagedType.LPWStr)] string f, int m); void Save([MarshalAs(UnmanagedType.LPWStr)] string f, bool r);
            void SaveCompleted([MarshalAs(UnmanagedType.LPWStr)] string f); void GetCurFile(out IntPtr f);
          }
          [StructLayout(LayoutKind.Sequential, Pack = 4)] public struct PropertyKey { public Guid fmtid; public uint pid; }
          [StructLayout(LayoutKind.Explicit)] public struct PropVariant { [FieldOffset(0)] public ushort vt; [FieldOffset(8)] public IntPtr p; }
          [ComImport, Guid("00021401-0000-0000-C000-000000000046")] class ShellLink {}
          public static void Create(string lnk, string target, string icon, string aumid) {
            var link = (IShellLinkW)new ShellLink();
            link.SetPath(target); link.SetIconLocation(icon, 0);
            var store = (IPropertyStore)link;
            var key = new PropertyKey { fmtid = new Guid("9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3"), pid = 5 };
            var v = new PropVariant { vt = 31, p = Marshal.StringToCoTaskMemUni(aumid) };
            store.SetValue(ref key, ref v); store.Commit();
            ((IPersistFile)link).Save(lnk, true);
            Marshal.FreeCoTaskMem(v.p);
          }
        }
        '@
        ${'$'}lnk = Join-Path ([Environment]::GetFolderPath('Programs')) (${b64("$APP_NAME.lnk")})
        [EbisLink]::Create(${'$'}lnk, ${b64(target)}, ${b64(icon)}, '$AUMID')
        ${'$'}k = 'HKCU:\Software\Classes\$SCHEME'
        New-Item -Path "${'$'}k\shell\open\command" -Force | Out-Null
        Set-ItemProperty -Path ${'$'}k -Name '(default)' -Value (${b64("URL:$APP_NAME")})
        Set-ItemProperty -Path ${'$'}k -Name 'URL Protocol' -Value ''
        Set-ItemProperty -Path "${'$'}k\shell\open\command" -Name '(default)' -Value (${b64(open)})
    """.trimIndent()
}

enum class Slot(val key: String, val label: String) { Morning("morning", "Утро"), Nag("nag", "Нытьё"), Shame("shame", "Позор"), Evening("evening", "Итог") }

/** Расписание уведомлений, как на телефоне: утро, нытьё, позорный столб, вечер и напоминалки по времени задач. */
class Reminders(private val state: AppState) {
    private val n get() = state.notif
    private val snoozed = mutableMapOf<Long, LocalDateTime>()

    suspend fun run() {
        var last = LocalDateTime.now()
        // Запустились утром с Windows после времени пинка — пинаем один раз, но не после полудня.
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        if (n.on && now.toLocalTime().toSecondOfDay() / 60 in n.morningMin until 12 * 60 && !n.fired(Slot.Morning.key, today.toEpochDay()) && n.mutedDay != today.toEpochDay()) {
            delay(4000)
            n.markFired(Slot.Morning.key, today.toEpochDay())
            fire(Slot.Morning)
        }
        while (true) {
            delay(20_000)
            val t = LocalDateTime.now()
            runCatching { tick(last, t) }.onFailure { Log.e("remind", "Проверка напоминалок упала", it) }
            last = t
        }
    }

    private fun tick(from: LocalDateTime, to: LocalDateTime) {
        if (!n.on) return
        val today = to.toLocalDate()
        val day = today.toEpochDay()
        // Ровно в своё время, а если компьютер спал — не позже чем через 15 минут.
        fun hit(min: Int): Boolean {
            val at = today.atTime(min / 60, min % 60)
            return at.isAfter(from) && !at.isAfter(to) && Duration.between(at, to).toMinutes() < 15
        }
        if (n.mutedDay != day) {
            val slots = buildList {
                add(Slot.Morning to n.morningMin)
                NotifSettings.nagTimes(n.nagCount).forEach { add(Slot.Nag to it) }
                if (n.shame) add(Slot.Shame to NotifSettings.SHAME_MIN)
                if (n.evening) add(Slot.Evening to n.eveningMin)
            }
            slots.forEach { (slot, min) ->
                val key = "${slot.key}$min"
                if (hit(min) && !n.fired(key, day)) {
                    n.markFired(key, day)
                    fire(slot)
                }
            }
        }
        if (n.remind || n.alarmOn) {
            state.tasks.filter { !it.done && it.time != null && it.due == today }.forEach { t ->
                val key = "r${t.id}"
                if (hit(t.time!!) && !n.fired(key, day)) {
                    n.markFired(key, day)
                    remind(t.id)
                }
            }
        }
        snoozed.entries.filter { !it.value.isAfter(to) }.map { it.key }.forEach { id ->
            snoozed.remove(id)
            remind(id)
        }
    }

    fun snooze(id: Long, minutes: Long = 60) {
        snoozed[id] = LocalDateTime.now().plusMinutes(minutes)
    }

    /** Сколько раз будильник по задаче откладывали: от этого зависит, насколько зол конь. */
    private val alarmSnoozes = mutableMapOf<Long, Int>()

    /** Будильник «Горит»: окно поверх всех и мелодия по кругу. null — проверка из настроек. */
    fun ring(t: Task?) {
        val r = if (t == null) AlarmRing(-1, "Позвонить в налоговую", "15:00", 0, n.snoozeMin)
        else AlarmRing(t.id, t.title, t.time?.let { Copy.fmtTime(it) } ?: "сейчас", alarmSnoozes[t.id] ?: 0, n.snoozeMin)
        // Пока орала другая задача, пришла новая: про старую остаётся обычная напоминалка.
        state.alarm?.takeIf { it.id >= 0 && it.id != r.id }?.let { prev -> remind(prev.id, plain = true) }
        Log.i("alarm", "Будильник: ${if (r.id < 0) "проверка из настроек" else "задача ${r.id}"}, звук=${n.alarmSound}, нарастание=${n.alarmRamp}")
        state.alarm = r
        AlarmPlayer.stopPreview()
        AlarmPlayer.start(n.alarmSound, n.alarmRamp)
        if (r.id >= 0) {
            // Дублируем в центр уведомлений: окно могло уехать на другой монитор. Без звука — орёт мелодия.
            val m = NotifCopy.alarm(r.at, r.snoozes)
            WinToast.show(
                "🔥 ${m.title}: ${r.title}", m.text, null,
                listOf(WinToast.Btn(NotifCopy.actionDone, cmd("done", r.id)), WinToast.Btn(NotifCopy.alarmSnooze(r.snoozeMin, r.snoozes), cmd("asnooze", r.id)), WinToast.Btn(NotifCopy.actionTomorrow, cmd("later", r.id))),
                "r${r.id}", sound = false, reminder = true,
            )
        }
    }

    /** Ответ на будильник из его окна или кнопки уведомления. */
    fun answer(a: AlarmAnswer) {
        val r = state.alarm ?: return
        Log.i("alarm", "Ответ на будильник: $a")
        stopAlarm()
        if (r.id < 0) {
            state.show("Проверка окончена. Конь доволен 🐴")
            return
        }
        val t = state.tasks.firstOrNull { it.id == r.id }
        when (a) {
            AlarmAnswer.Done -> {
                alarmSnoozes.remove(r.id)
                if (t != null && !t.done) state.toggle(r.id)
            }
            AlarmAnswer.Snooze -> {
                alarmSnoozes[r.id] = r.snoozes + 1
                snooze(r.id, n.snoozeMin.toLong())
                state.show(NotifCopy.toastAlarmSnooze(n.snoozeMin))
            }
            AlarmAnswer.Tomorrow -> {
                alarmSnoozes.remove(r.id)
                if (t != null) state.postpone(r.id)
            }
        }
    }

    fun stopAlarm() {
        state.alarm = null
        AlarmPlayer.stop()
    }

    private fun cmd(action: String, id: Long? = null) = "${WinToast.SCHEME}://$action" + (id?.let { "/$it" } ?: "")

    /** [force] — кнопка «проверить» в настройках: без пустых задач придумает пример. */
    fun fire(slot: Slot, force: Boolean = false) {
        Log.i("remind", "Уведомление ${slot.key}${if (force) " (вручную)" else ""}")
        val s = DaySnapshot(state.tasks)
        val progress = "${s.doneToday} из ${s.doneToday + s.remaining.size} на сегодня"
        val done = NotifCopy.actionDone
        val later = NotifCopy.actionTomorrow
        when (slot) {
            Slot.Morning -> {
                val m = NotifCopy.morning(s.remaining.size, s.overdue.size)
                post(m, null, listOf(WinToast.Btn("Открыть список", cmd("open")), WinToast.Btn(NotifCopy.actionMute, cmd("mute"))), "morning")
            }
            Slot.Nag -> {
                val t = s.remaining.firstOrNull()
                if (t == null) {
                    if (force) post(NotifCopy.nag("Пример задачи", 1, 3), "1 из 4 на сегодня", emptyList(), "nag")
                    return
                }
                post(
                    NotifCopy.nag(t.title, s.doneToday, s.remaining.size), progress,
                    listOf(WinToast.Btn(done, cmd("done", t.id)), WinToast.Btn(later, cmd("later", t.id)), WinToast.Btn(NotifCopy.actionMute, cmd("mute"))), "nag",
                )
            }
            Slot.Shame -> {
                val t = s.overdue.firstOrNull()
                if (t == null) {
                    if (force) post(NotifCopy.shame("Дописать диплом", 214, 2), null, emptyList(), "shame")
                    return
                }
                val days = ChronoUnit.DAYS.between(t.due, s.today).toInt()
                post(
                    NotifCopy.shame(t.title, days, s.overdue.size - 1), progress,
                    listOf(WinToast.Btn(done, cmd("done", t.id)), WinToast.Btn(later, cmd("later", t.id)), WinToast.Btn(NotifCopy.actionDelete, cmd("del", t.id))), "shame",
                )
            }
            Slot.Evening -> post(NotifCopy.evening(s.doneToday, s.remaining.size, s.tomorrow), progress, listOf(WinToast.Btn("Открыть список", cmd("open"))), "evening")
        }
    }

    /**
     * Напоминалка про задачу со временем. Тишина «на сегодня» её не глушит: время ставил сам.
     * Горящая задача вместо уведомления орёт будильником, если он включён ([plain] — всё равно уведомлением).
     */
    fun remind(id: Long, force: Boolean = false, plain: Boolean = false) {
        if (!force && !plain && n.alarmOn) {
            state.tasks.firstOrNull { it.id == id && !it.done && it.hot }?.let { ring(it); return }
        }
        if (!force && !plain && !n.remind) return
        val t = state.tasks.firstOrNull { it.id == id && !it.done }
            ?: if (force) state.tasks.firstOrNull { !it.done && it.time != null } ?: state.tasks.firstOrNull { !it.done } else null
        if (t == null) {
            if (force) post(NotifCopy.remind("Позвонить маме"), null, emptyList(), "remind", reminder = true)
            return
        }
        post(
            NotifCopy.remind(t.title), t.time?.let { "на ${ru.konem.ebis.ui.Copy.fmtTime(it)}" },
            listOf(WinToast.Btn(NotifCopy.actionDone, cmd("done", t.id)), WinToast.Btn(NotifCopy.actionSnooze, cmd("snooze", t.id)), WinToast.Btn(NotifCopy.actionTomorrow, cmd("later", t.id))),
            "r${t.id}", reminder = true,
        )
    }

    private fun post(m: NotifCopy.Msg, attribution: String?, buttons: List<WinToast.Btn>, tag: String, reminder: Boolean = false) =
        WinToast.show(m.title, m.text, attribution, buttons, tag, n.sound, reminder)
}

/** Команда из уведомления или от второго запуска. true — надо показать окно. */
fun AppState.handleCommand(cmd: String, reminders: Reminders): Boolean {
    val body = cmd.trim().removePrefix("${WinToast.SCHEME}:").trimStart('/').trimEnd('/')
    val action = body.substringBefore('/')
    val id = body.substringAfter('/', "").toLongOrNull()
    val task = id?.let { i -> tasks.firstOrNull { it.id == i } }
    // Кнопки уведомления будильника: если он ещё орёт по этой задаче — отвечаем ему.
    val ringing = id != null && alarm?.id == id
    when (action) {
        "done" -> if (ringing) reminders.answer(AlarmAnswer.Done) else if (task != null && !task.done) { toggle(task.id); show(NotifCopy.toastDone) }
        "later" -> if (ringing) reminders.answer(AlarmAnswer.Tomorrow) else if (task != null) postpone(task.id)
        "asnooze" -> if (ringing) reminders.answer(AlarmAnswer.Snooze) else if (task != null) { reminders.snooze(task.id, notif.snoozeMin.toLong()); show(NotifCopy.toastAlarmSnooze(notif.snoozeMin)) }
        "del" -> if (task != null) delete(task.id)
        "snooze" -> if (task != null) { reminders.snooze(task.id); show(NotifCopy.toastSnooze) }
        "mute" -> { notif.mutedDay = LocalDate.now().toEpochDay(); show(NotifCopy.toastMute) }
        "hidden" -> return false
        else -> return true
    }
    return false
}
