package ru.konem.ebis.notify

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ru.konem.ebis.data.Task
import ru.konem.ebis.data.TaskRepo
import ru.konem.ebis.ui.Copy

/**
 * Будильник «Горит»: мелодия по кругу, вибрация и уведомление на весь экран, пока не ответишь.
 * В беззвучном режиме молчит, в режиме вибрации только вибрирует.
 */
class AlarmService : Service() {
    /** Что сейчас орёт. [id] < 0 — проверка из настроек, задачи за ней нет. */
    data class Ringing(val id: Long, val title: String, val at: String, val snoozes: Int, val snoozeMin: Int)

    enum class Answer { Done, Snooze, Tomorrow }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        TaskRepo.init(this)
        val prefs = Prefs(this)
        val id = intent?.getLongExtra(EXTRA_TASK, -1L) ?: -1L
        val r = if (intent?.getBooleanExtra(EXTRA_TEST, false) == true) {
            Ringing(-1, "Позвонить в налоговую", "15:00", 0, prefs.snoozeMin)
        } else {
            val t = TaskRepo.tasks.firstOrNull { it.id == id && !it.done }
            if (t == null) {
                if (_ringing.value == null) stopSelf()
                return START_NOT_STICKY
            }
            ringingFor(t, prefs)
        }
        // Пока орала другая задача, пришла новая: про старую остаётся обычная напоминалка.
        _ringing.value?.takeIf { it.id >= 0 && it.id != r.id }?.let { Notifier.remind(this, it.id) }
        _ringing.value = r

        val n = Notifier.alarm(this, r)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(Notifier.ID_ALARM, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(Notifier.ID_ALARM, n)
        }
        ring(prefs)
        return START_NOT_STICKY
    }

    private fun ringingFor(t: Task, prefs: Prefs) =
        Ringing(t.id, t.title, t.time?.let { Copy.fmtTime(it) } ?: "сейчас", prefs.snoozes(t.id), prefs.snoozeMin)

    private fun ring(prefs: Prefs) {
        silence()
        val mode = getSystemService(AudioManager::class.java)?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        if (mode == AudioManager.RINGER_MODE_NORMAL) {
            player = AlarmSounds.player(this, prefs.alarmSound)?.also { p ->
                if (prefs.alarmRamp) {
                    // Громкость нарастает за 20 секунд, чтобы не подскочить с кровати.
                    val start = System.currentTimeMillis()
                    p.setVolume(0.15f, 0.15f)
                    handler.post(object : Runnable {
                        override fun run() {
                            if (player !== p) return
                            val k = ((System.currentTimeMillis() - start).toFloat() / Alarm.RAMP_MS).coerceIn(0f, 1f)
                            val v = 0.15f + 0.85f * k
                            runCatching { p.setVolume(v, v) }
                            if (k < 1f) handler.postDelayed(this, 400)
                        }
                    })
                }
                p.start()
            }
        }
        if (prefs.alarmVibrate && mode != AudioManager.RINGER_MODE_SILENT) {
            vibrator = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
            })?.also { v ->
                val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                @Suppress("DEPRECATION")
                v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0), attrs)
            }
        }
    }

    private fun silence() {
        player?.runCatching { stop(); release() }
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun finishRinging() {
        silence()
        _ringing.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        silence()
        _ringing.value = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_TASK = "taskId"
        private const val EXTRA_TEST = "test"

        private var instance: AlarmService? = null
        private val _ringing = MutableStateFlow<Ringing?>(null)

        /** Экран тревоги следит за этим: стало null — закрывается. */
        val ringing: StateFlow<Ringing?> get() = _ringing

        /** Из будильника AlarmManager. false — запустить не дали, пусть придёт обычная напоминалка. */
        fun start(context: Context, taskId: Long): Boolean = launch(context, Intent(context, AlarmService::class.java).putExtra(EXTRA_TASK, taskId))

        /** Кнопка «Заорать сейчас» в настройках. */
        fun test(context: Context) = launch(context, Intent(context, AlarmService::class.java).putExtra(EXTRA_TEST, true))

        private fun launch(context: Context, intent: Intent): Boolean = try {
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (e: Exception) {
            // Android 12+: из фона службу не запустить (бывает, если будильник не точный).
            false
        }

        /** Задачи поменялись (в приложении, виджетом, с ПК): если ту, что орёт, закрыли — замолкаем. */
        fun onTasksChanged(tasks: List<Task>) {
            val r = _ringing.value ?: return
            if (r.id < 0) return
            if (tasks.none { it.id == r.id && !it.done }) Handler(Looper.getMainLooper()).post { instance?.finishRinging() }
        }

        /** Ответ с экрана тревоги или кнопки уведомления. Возвращает текст тоста. */
        fun answer(context: Context, taskId: Long, a: Answer): String? {
            instance?.finishRinging() ?: run { _ringing.value = null }
            Notifier.cancel(context, Notifier.ID_ALARM)
            if (taskId < 0) return "Проверка окончена. Конь доволен 🐴"
            TaskRepo.init(context)
            val prefs = Prefs(context)
            return when (a) {
                Answer.Done -> {
                    TaskRepo.setDone(taskId, sync = true)
                    prefs.resetSnoozes(taskId)
                    Notifier.cancelForTask(context, taskId)
                    if (Notifier.checkAllDone(context) != null) {
                        Notifier.celebrate(context)
                        null
                    } else NotifCopy.toastDone
                }
                Answer.Snooze -> {
                    prefs.addSnooze(taskId)
                    Scheduler.snooze(context, taskId, prefs.snoozeMin.toLong(), alarm = true)
                    NotifCopy.toastAlarmSnooze(prefs.snoozeMin)
                }
                Answer.Tomorrow -> {
                    TaskRepo.postpone(taskId, sync = true)
                    prefs.resetSnoozes(taskId)
                    Notifier.cancelForTask(context, taskId)
                    NotifCopy.toastTomorrow
                }
            }
        }
    }
}
