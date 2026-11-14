package ru.konem.ebis.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import ru.konem.ebis.notify.Alarm
import java.util.prefs.Preferences
import java.awt.event.KeyEvent as AwtKey

/** Настройки уведомлений, как на телефоне (app/.../notify/Prefs.kt), плюс то, что есть только на ПК: трей и автозапуск. */
class NotifSettings(private val p: Preferences) {
    private fun bool(key: String, def: Boolean) = mutableStateOf(p.getBoolean(key, def))
    private fun int(key: String, def: Int) = mutableStateOf(p.getInt(key, def))

    private val _on = bool("n.on", true)
    private val _tray = bool("n.tray", true)
    private val _autostart = bool("n.autostart", false)
    private val _morning = int("n.morningMin", 9 * 60)
    private val _nag = int("n.nagCount", 3)
    private val _shame = bool("n.shame", true)
    private val _evening = bool("n.evening", true)
    private val _eveningMin = int("n.eveningMin", 21 * 60)
    private val _remind = bool("n.remind", true)
    private val _sound = bool("n.sound", true)
    private val _alarmOn = bool("n.alarmOn", true)
    private val _alarmSound = mutableStateOf(p.get("n.alarmSound", Alarm.DEFAULT_SOUND))
    private val _alarmCustom = mutableStateOf<String?>(p.get("n.alarmCustom", null))
    private val _snoozeMin = int("n.snoozeMin", Alarm.DEFAULT_SNOOZE)
    private val _alarmRamp = bool("n.alarmRamp", true)

    private fun save(block: Preferences.() -> Unit) {
        p.block()
        runCatching { p.flush() }
    }

    var on: Boolean get() = _on.value; set(v) { _on.value = v; save { putBoolean("n.on", v) } }
    var tray: Boolean get() = _tray.value; set(v) { _tray.value = v; save { putBoolean("n.tray", v) } }
    var autostart: Boolean get() = _autostart.value; set(v) { _autostart.value = v; save { putBoolean("n.autostart", v) } }
    var morningMin: Int get() = _morning.value; set(v) { _morning.value = v; save { putInt("n.morningMin", v) } }
    var nagCount: Int get() = _nag.value; set(v) { _nag.value = v; save { putInt("n.nagCount", v) } }
    var shame: Boolean get() = _shame.value; set(v) { _shame.value = v; save { putBoolean("n.shame", v) } }
    var evening: Boolean get() = _evening.value; set(v) { _evening.value = v; save { putBoolean("n.evening", v) } }
    var eveningMin: Int get() = _eveningMin.value; set(v) { _eveningMin.value = v; save { putInt("n.eveningMin", v) } }
    var remind: Boolean get() = _remind.value; set(v) { _remind.value = v; save { putBoolean("n.remind", v) } }
    var sound: Boolean get() = _sound.value; set(v) { _sound.value = v; save { putBoolean("n.sound", v) } }

    // Будильник «Горит»
    var alarmOn: Boolean get() = _alarmOn.value; set(v) { _alarmOn.value = v; save { putBoolean("n.alarmOn", v) } }
    /** id из [Alarm.Sound] или [Alarm.CUSTOM]. */
    var alarmSound: String get() = _alarmSound.value; set(v) { _alarmSound.value = v; save { put("n.alarmSound", v) } }
    /** Как назывался свой файл; сама копия — в [AlarmPlayer.customDir]. */
    var alarmCustomName: String? get() = _alarmCustom.value; set(v) { _alarmCustom.value = v; save { if (v == null) remove("n.alarmCustom") else put("n.alarmCustom", v) } }
    var snoozeMin: Int get() = _snoozeMin.value; set(v) { _snoozeMin.value = v; save { putInt("n.snoozeMin", v) } }
    var alarmRamp: Boolean get() = _alarmRamp.value; set(v) { _alarmRamp.value = v; save { putBoolean("n.alarmRamp", v) } }

    /** День (epochDay), до конца которого конь молчит. */
    var mutedDay: Long
        get() = p.getLong("n.mutedDay", -1)
        set(v) = save { putLong("n.mutedDay", v) }

    /** Что уже отправили сегодня: «slot@epochDay» через запятую. Старые дни выкидываются. */
    fun fired(key: String, day: Long): Boolean = "$key@$day" in p.get("n.fired", "").split(',')

    fun markFired(key: String, day: Long) {
        val keep = p.get("n.fired", "").split(',').filter { it.endsWith("@$day") }
        save { put("n.fired", (keep + "$key@$day").distinct().joinToString(",")) }
    }

    companion object {
        const val SHAME_MIN = 11 * 60
        fun nagTimes(count: Int): List<Int> = when (count) {
            1 -> listOf(14 * 60)
            2 -> listOf(12 * 60, 17 * 60)
            3 -> listOf(12 * 60, 15 * 60, 18 * 60)
            else -> emptyList()
        }
    }
}

// ── Оформление ─────────────────────────────────────────────────

enum class Pattern(val label: String) { Dots("Точки"), Grid("Клетка"), Plus("Плюсы"), Hatch("Штрих"), None("Пусто") }

/** Цвет узора; null — цвет текста темы. */
enum class PatternInk(val label: String, val color: Color?) {
    Ink("Как текст", null), Red("Красный", Color(0xFFE5383B)), Hay("Сено", Color(0xFFE0A800)),
    Green("Зелёный", Color(0xFF2FA866)), Blue("Синий", Color(0xFF3D8BFF)),
}

/** Насколько заметен узор: процент непрозрачности. */
enum class PatternStrength(val label: String, val alpha: Float) { Faint("Едва", 0.06f), Soft("Слабо", 0.11f), Normal("Норм", 0.18f), Strong("Сильно", 0.28f) }

enum class BodyFont(val label: String, val hint: String) {
    Onest("Onest", "Как было"), Roboto("Roboto", "Строгий, как в Android"),
    Montserrat("Montserrat", "Широкий, жирный"), Nunito("Nunito", "Мягкий, округлый"),
}

data class Look(
    /**
     * Свой фон отдельно для дня и для ночи; null — фон темы. Раньше фон был один и перебивал тему целиком:
     * со светлым своим фоном «Ночь» ничего не меняла. Теперь светлый цвет живёт в дне, тёмный — в ночи.
     */
    val bgLight: Color? = null,
    val bgDark: Color? = null,
    val pattern: Pattern = Pattern.Dots,
    val ink: PatternInk = PatternInk.Ink,
    val strength: PatternStrength = PatternStrength.Soft,
    val head: Color = HeadPresets.first().second,
    val font: BodyFont = BodyFont.Onest,
    val scale: Float = 1f,
) {
    fun bg(dark: Boolean): Color? = if (dark) bgDark else bgLight

    /** Ставит фон в «свою» тему: тёмный цвет — ночи, светлый — дню. null сбрасывает фон темы [dark]. */
    fun withBg(c: Color?, dark: Boolean): Look = when {
        c == null -> if (dark) copy(bgDark = null) else copy(bgLight = null)
        isDarkBg(c) -> copy(bgDark = c)
        else -> copy(bgLight = c)
    }

    companion object {
        /** Порог тот же, что в [Scheme.with]: темнее — белый текст. */
        fun isDarkBg(c: Color) = c.luminance() < 0.18f
    }
}

val HeadPresets = listOf(
    "Сено" to Color(0xFFFFD447), "Мята" to Color(0xFF8BE3BE), "Жвачка" to Color(0xFFFFA8CB),
    "Небо" to Color(0xFF9AD8FF), "Апельсин" to Color(0xFFFFB062),
)

val BgPresets = listOf(
    "Мята" to Color(0xFFE6F4EC), "Небо" to Color(0xFFEAF1FF), "Персик" to Color(0xFFFFE9DC), "Сирень" to Color(0xFFF1ECFF),
    "Графит" to Color(0xFF1E2228), "Полночь" to Color(0xFF0E1630), "Шоколад" to Color(0xFF1E1410),
)

val Scales = listOf(1f to "100%", 1.15f to "115%", 1.3f to "130%")

class LookStore(private val p: Preferences) {
    fun load(): Look {
        val d = Look()
        fun <E : Enum<E>> e(key: String, def: E, all: Array<E>) = all.firstOrNull { it.name == p.get(key, def.name) } ?: def
        fun color(key: String) = p.get(key, null)?.toLongOrNull()?.let { Color(it.toInt()) }
        // Старый единый фон раскладываем по теме, к которой он подходит.
        val legacy = color("look.bg")
        return Look(
            bgLight = color("look.bgLight") ?: legacy?.takeIf { !Look.isDarkBg(it) },
            bgDark = color("look.bgDark") ?: legacy?.takeIf { Look.isDarkBg(it) },
            pattern = e("look.pattern", d.pattern, Pattern.entries.toTypedArray()),
            ink = e("look.ink", d.ink, PatternInk.entries.toTypedArray()),
            strength = e("look.strength", d.strength, PatternStrength.entries.toTypedArray()),
            head = p.get("look.head", null)?.toLongOrNull()?.let { Color(it.toInt()) } ?: d.head,
            font = e("look.font", d.font, BodyFont.entries.toTypedArray()),
            scale = p.getFloat("look.scale", 1f),
        )
    }

    fun save(l: Look) {
        p.remove("look.bg")
        if (l.bgLight == null) p.remove("look.bgLight") else p.put("look.bgLight", l.bgLight.toArgb().toLong().toString())
        if (l.bgDark == null) p.remove("look.bgDark") else p.put("look.bgDark", l.bgDark.toArgb().toLong().toString())
        p.put("look.pattern", l.pattern.name)
        p.put("look.ink", l.ink.name)
        p.put("look.strength", l.strength.name)
        p.put("look.head", l.head.toArgb().toLong().toString())
        p.put("look.font", l.font.name)
        p.putFloat("look.scale", l.scale)
        runCatching { p.flush() }
    }
}

// ── Клавиши ────────────────────────────────────────────────────

enum class Action(val label: String, val hint: String, val default: String) {
    Quick("Накинуть херню", "Полная форма новой задачи", "Ctrl+N"),
    Today("Вкладка «Сегодня»", "", "Ctrl+1"),
    All("Вкладка «Вся херня»", "", "Ctrl+2"),
    Done("Вкладка «Сделано»", "", "Ctrl+3"),
    Settings("Открыть настройки", "", "Ctrl+,"),
    Theme("День / ночь", "", "Ctrl+D"),
    Save("Сохранить правку", "В окне редактирования", "Ctrl+Enter"),
    Hide("Спрятать окно", "В трей, если он включён", "Ctrl+W"),
}

/** Сочетания клавиш. Хранятся строками вида «Ctrl+Shift+K». */
class Keymap(private val p: Preferences) {
    private val map = mutableStateMapOf<Action, String>().apply {
        Action.entries.forEach { put(it, p.get("key.${it.name}", it.default)) }
    }

    operator fun get(a: Action): String = map.getValue(a)

    fun find(combo: String): Action? = Action.entries.firstOrNull { map[it] == combo }

    /** Ставит сочетание. Если оно было у другого действия — меняет их местами и возвращает это действие. */
    fun set(a: Action, combo: String): Action? {
        val clash = Action.entries.firstOrNull { it != a && map[it] == combo }
        if (clash != null) put(clash, map.getValue(a))
        put(a, combo)
        return clash
    }

    fun reset() = Action.entries.forEach { put(it, it.default) }

    private fun put(a: Action, combo: String) {
        map[a] = combo
        p.put("key.${a.name}", combo)
        runCatching { p.flush() }
    }

    companion object {
        /** Сочетание из нажатия; null — нажат только модификатор. */
        fun combo(e: KeyEvent): String? {
            val code = e.key.nativeKeyCode
            val name = when (code) {
                in AwtKey.VK_A..AwtKey.VK_Z -> ('A' + (code - AwtKey.VK_A)).toString()
                in AwtKey.VK_0..AwtKey.VK_9 -> ('0' + (code - AwtKey.VK_0)).toString()
                in AwtKey.VK_NUMPAD0..AwtKey.VK_NUMPAD9 -> ('0' + (code - AwtKey.VK_NUMPAD0)).toString()
                in AwtKey.VK_F1..AwtKey.VK_F12 -> "F${code - AwtKey.VK_F1 + 1}"
                AwtKey.VK_ENTER -> "Enter"
                AwtKey.VK_SPACE -> "Space"
                AwtKey.VK_COMMA -> ","
                AwtKey.VK_PERIOD -> "."
                AwtKey.VK_SLASH -> "/"
                AwtKey.VK_MINUS -> "-"
                AwtKey.VK_EQUALS -> "="
                AwtKey.VK_TAB -> "Tab"
                AwtKey.VK_BACK_SPACE -> "Backspace"
                AwtKey.VK_DELETE -> "Delete"
                AwtKey.VK_UP -> "↑"
                AwtKey.VK_DOWN -> "↓"
                AwtKey.VK_LEFT -> "←"
                AwtKey.VK_RIGHT -> "→"
                AwtKey.VK_ESCAPE -> "Esc"
                AwtKey.VK_CONTROL, AwtKey.VK_SHIFT, AwtKey.VK_ALT,
                AwtKey.VK_META, AwtKey.VK_WINDOWS, AwtKey.VK_ALT_GRAPH -> return null
                else -> AwtKey.getKeyText(code).takeIf { it.isNotBlank() } ?: return null
            }
            val parts = buildList {
                if (e.isCtrlPressed || e.isMetaPressed) add("Ctrl")
                if (e.isAltPressed) add("Alt")
                if (e.isShiftPressed) add("Shift")
                add(name)
            }
            return parts.joinToString("+")
        }

        /** Годится ли для глобального действия: без Ctrl/Alt сработает посреди набора текста. */
        fun usable(combo: String) = combo.startsWith("Ctrl+") || combo.startsWith("Alt+") || Regex("""(Shift\+)?F\d+""").matches(combo)
    }
}
