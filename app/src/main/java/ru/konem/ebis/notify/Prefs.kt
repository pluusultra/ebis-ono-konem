package ru.konem.ebis.notify

import android.content.Context

/** Настройки уведомлений. Время хранится в минутах от полуночи. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("ebis", Context.MODE_PRIVATE)

    var morningMin: Int
        get() = sp.getInt("morningMin", 9 * 60)
        set(v) = sp.edit().putInt("morningMin", v).apply()

    /** Сколько раз нудеть днём: 0..3. */
    var nagCount: Int
        get() = sp.getInt("nagCount", 3)
        set(v) = sp.edit().putInt("nagCount", v).apply()

    var shameOn: Boolean
        get() = sp.getBoolean("shameOn", true)
        set(v) = sp.edit().putBoolean("shameOn", v).apply()

    var eveningOn: Boolean
        get() = sp.getBoolean("eveningOn", true)
        set(v) = sp.edit().putBoolean("eveningOn", v).apply()

    var eveningMin: Int
        get() = sp.getInt("eveningMin", 21 * 60)
        set(v) = sp.edit().putInt("eveningMin", v).apply()

    /** День (epochDay), до конца которого конь молчит. */
    var mutedDay: Long
        get() = sp.getLong("mutedDay", -1)
        set(v) = sp.edit().putLong("mutedDay", v).apply()

    /** День, когда уже поздравили с закрытием всего. */
    var celebratedDay: Long
        get() = sp.getLong("celebratedDay", -1)
        set(v) = sp.edit().putLong("celebratedDay", v).apply()

    /** 0 — как в системе, 1 — день, 2 — ночь (см. ThemeMode). */
    var themeMode: Int
        get() = sp.getInt("themeMode", 0)
        set(v) = sp.edit().putInt("themeMode", v).apply()

    var askedPermission: Boolean
        get() = sp.getBoolean("askedPermission", false)
        set(v) = sp.edit().putBoolean("askedPermission", v).apply()

    /** «Сегодня»: новые задачи сверху, а не снизу. */
    var todayNewFirst: Boolean
        get() = sp.getBoolean("todayNewFirst", false)
        set(v) = sp.edit().putBoolean("todayNewFirst", v).apply()

    /** «Ну здарова, бедолага» при запуске. */
    var welcomeOn: Boolean
        get() = sp.getBoolean("welcomeOn", true)
        set(v) = sp.edit().putBoolean("welcomeOn", v).apply()

    /** Серия дней подряд, когда закрыто всё на сегодня. */
    var streak: Int
        get() = sp.getInt("streak", 0)
        set(v) = sp.edit().putInt("streak", v).apply()

    var streakDay: Long
        get() = sp.getLong("streakDay", -10)
        set(v) = sp.edit().putLong("streakDay", v).apply()

    var bestStreak: Int
        get() = sp.getInt("bestStreak", 0)
        set(v) = sp.edit().putInt("bestStreak", v).apply()

    /** Текущая серия: живёт, пока последний закрытый день — сегодня или вчера. */
    fun liveStreak(today: Long): Int = if (streakDay >= today - 1) streak else 0

    /** id задач, на которые сейчас стоят будильники-напоминалки. */
    var taskAlarms: Set<String>
        get() = sp.getStringSet("taskAlarms", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("taskAlarms", v).apply()

    // ── Будильник «Горит» ──────────────────────────────────────
    var alarmOn: Boolean
        get() = sp.getBoolean("alarmOn", true)
        set(v) = sp.edit().putBoolean("alarmOn", v).apply()

    /** id из [Alarm.Sound] или [Alarm.CUSTOM]. */
    var alarmSound: String
        get() = sp.getString("alarmSound", Alarm.DEFAULT_SOUND) ?: Alarm.DEFAULT_SOUND
        set(v) = sp.edit().putString("alarmSound", v).apply()

    /** Как назывался свой файл — показать в настройках. Сам файл лежит в filesDir (см. AlarmSounds). */
    var alarmCustomName: String?
        get() = sp.getString("alarmCustomName", null)
        set(v) = sp.edit().putString("alarmCustomName", v).apply()

    var snoozeMin: Int
        get() = sp.getInt("snoozeMin", Alarm.DEFAULT_SNOOZE)
        set(v) = sp.edit().putInt("snoozeMin", v).apply()

    var alarmRamp: Boolean
        get() = sp.getBoolean("alarmRamp", true)
        set(v) = sp.edit().putBoolean("alarmRamp", v).apply()

    var alarmVibrate: Boolean
        get() = sp.getBoolean("alarmVibrate", true)
        set(v) = sp.edit().putBoolean("alarmVibrate", v).apply()

    /** Сколько раз будильник по задаче уже откладывали: от этого зависит, насколько зол конь. */
    fun snoozes(taskId: Long): Int = sp.getInt("snz_$taskId", 0)

    fun addSnooze(taskId: Long) = sp.edit().putInt("snz_$taskId", snoozes(taskId) + 1).apply()

    fun resetSnoozes(taskId: Long) = sp.edit().remove("snz_$taskId").apply()

    companion object {
        const val SHAME_MIN = 11 * 60

        fun nagTimes(count: Int): List<Int> = when (count) {
            1 -> listOf(14 * 60)
            2 -> listOf(12 * 60, 17 * 60)
            3 -> listOf(12 * 60, 15 * 60, 18 * 60)
            else -> emptyList()
        }

        fun fmt(min: Int) = "%d:%02d".format(min / 60, min % 60)
    }
}
