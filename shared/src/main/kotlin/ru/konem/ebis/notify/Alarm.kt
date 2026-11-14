package ru.konem.ebis.notify

/** Будильник «Горит»: задача со временем и отметкой «Горит» орёт мелодией по кругу, пока не ответишь. */
object Alarm {
    /** Встроенные мелодии. Файлы лежат в app/src/main/res/raw, ПК берёт их оттуда же как ресурсы. */
    enum class Sound(val id: String, val label: String, val hint: String, val file: String) {
        Gallop("gallop", "Галоп", "Россини. Конь же", "alarm_gallop.wav"),
        King("king", "Горный король", "Григ. С каждым кругом быстрее", "alarm_king.wav"),
        Village("village", "Сельская дискотека", "Арпеджио, как на денди", "alarm_village.wav"),
        Balalaika("balalaika", "Балалайка-тревога", "Тремоло на завалинке", "alarm_balalaika.wav"),
        Bell("bell", "Старый будильник", "Звонок с молоточком", "alarm_bell.mp3");

        /** Имя ресурса без расширения (R.raw.*). */
        val res: String get() = file.substringBeforeLast('.')

        companion object {
            fun of(id: String): Sound? = entries.firstOrNull { it.id == id }
        }
    }

    /** id своего звука: файл, который выбрал человек. */
    const val CUSTOM = "own"
    const val DEFAULT_SOUND = "gallop"

    /** «Потом» — через сколько минут заорать снова. */
    val snoozeOptions = listOf(10, 30, 60)
    const val DEFAULT_SNOOZE = 30

    /** За сколько громкость доходит до полной, если включено нарастание. */
    const val RAMP_MS = 20_000L
}
