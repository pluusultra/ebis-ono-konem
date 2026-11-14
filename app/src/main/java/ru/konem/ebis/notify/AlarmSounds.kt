package ru.konem.ebis.notify

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import java.io.File

/** Откуда брать мелодию будильника: встроенная из res/raw или свой файл, скопированный в приложение. */
object AlarmSounds {
    private fun customFile(context: Context) = File(context.filesDir, "alarm_custom")

    /** id ресурса или 0, если файла в сборке нет (например, звонок ещё не положили в res/raw). */
    private fun resId(context: Context, s: Alarm.Sound): Int =
        context.resources.getIdentifier(s.res, "raw", context.packageName)

    /** Встроенные мелодии, которые реально есть в сборке. */
    fun available(context: Context): List<Alarm.Sound> = Alarm.Sound.entries.filter { resId(context, it) != 0 }

    fun hasCustom(context: Context) = customFile(context).exists()

    /** Копирует выбранный файл к себе: потом его можно удалить из загрузок. */
    fun saveCustom(context: Context, uri: Uri): Boolean = runCatching {
        val tmp = File(context.filesDir, "alarm_custom.tmp")
        context.contentResolver.openInputStream(uri)!!.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        // Проверяем, что это вообще звук, прежде чем менять рабочий файл.
        MediaPlayer().apply { setDataSource(tmp.path); prepare(); release() }
        tmp.renameTo(customFile(context).also { it.delete() })
    }.getOrDefault(false)

    /** Готовый к запуску плеер на потоке будильника. Не вышло со своим файлом — играет «Галоп». */
    fun player(context: Context, id: String): MediaPlayer? {
        fun make(setSource: MediaPlayer.() -> Unit) = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setSource()
                isLooping = true
                prepare()
            }
        }.getOrNull()

        if (id == Alarm.CUSTOM && hasCustom(context)) make { setDataSource(customFile(context).path) }?.let { return it }
        val s = Alarm.Sound.of(id)?.takeIf { resId(context, it) != 0 } ?: Alarm.Sound.Gallop
        val res = resId(context, s).takeIf { it != 0 } ?: return null
        return make { setDataSource(context, Uri.parse("android.resource://${context.packageName}/$res")) }
    }

    /** Прослушать в настройках. Повторный вызов с тем же id — стоп. Возвращает id, который сейчас играет. */
    private var preview: MediaPlayer? = null
    private var previewId: String? = null

    fun togglePreview(context: Context, id: String): String? {
        val same = previewId == id
        stopPreview()
        if (same) return null
        preview = player(context, id)?.apply { start() }
        previewId = if (preview != null) id else null
        return previewId
    }

    fun stopPreview() {
        preview?.runCatching { stop(); release() }
        preview = null
        previewId = null
    }
}
