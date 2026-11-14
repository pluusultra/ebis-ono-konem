package ru.konem.ebis.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.LruCache
import android.util.TypedValue
import androidx.annotation.ColorRes
import androidx.annotation.FontRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.updateAll
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.konem.ebis.Incoming
import ru.konem.ebis.MainActivity
import ru.konem.ebis.QuickAddActivity
import ru.konem.ebis.R
import ru.konem.ebis.data.Task
import ru.konem.ebis.data.TaskRepo
import ru.konem.ebis.notify.Notifier
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max

/** Цвета виджетов: ресурсы с дневным и ночным вариантом (values / values-night). */
object WColor {
    val Ink = ColorProvider(R.color.w_ink)
    val OnHay = ColorProvider(R.color.w_onhay)
    val HeaderSub = ColorProvider(R.color.w_header_sub)
    val Mute = ColorProvider(R.color.w_mute)
    val Faint = ColorProvider(R.color.w_faint)
    val Red = ColorProvider(R.color.w_red)
    val Hay = ColorProvider(R.color.w_hay)
    val Green = ColorProvider(R.color.w_green)
    val White = ColorProvider(R.color.w_white)
    val Card = ColorProvider(R.color.w_card)
}

/**
 * Виджеты не умеют в шрифты приложения, поэтому крупные цифры и заголовки шрифтом Unbounded
 * рисуем картинкой. Цвет берётся из ресурса с учётом дневной/ночной темы.
 */
fun textBitmap(
    context: Context,
    text: String,
    sp: Float,
    @ColorRes color: Int,
    weight: Int = 900,
    @FontRes font: Int = R.font.unbounded,
): Bitmap {
    // Одни и те же надписи рисуются при каждой перерисовке — держим готовые (цвет зависит от темы).
    val key = "$text|$sp|${ContextCompat.getColor(context, color)}|$weight|$font|${context.resources.displayMetrics.density}"
    bitmaps[key]?.let { return it }
    return drawText(context, text, sp, color, weight, font).also { bitmaps.put(key, it) }
}

private val bitmaps = LruCache<String, Bitmap>(24)

private fun drawText(context: Context, text: String, sp: Float, @ColorRes color: Int, weight: Int, @FontRes font: Int): Bitmap {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(context, font)
        fontVariationSettings = "'wght' $weight"
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics)
        this.color = ContextCompat.getColor(context, color)
    }
    val fm = paint.fontMetrics
    val w = max(1, ceil(paint.measureText(text)).toInt())
    val h = max(1, ceil(fm.descent - fm.ascent).toInt())
    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
        Canvas(it).drawText(text, 0f, -fm.ascent, paint)
    }
}

/** Самая горящая задача на сегодня: «горит» → самая протухшая → по порядку. */
fun hottest(tasks: List<Task>, today: LocalDate): Task? =
    tasks.filter { !it.done && !it.due.isAfter(today) }
        .sortedWith(compareByDescending<Task> { it.hot }.thenBy { it.due }.thenBy { it.order })
        .firstOrNull()

fun openAppIntent(context: Context) = Intent(context, MainActivity::class.java)

/** «+» на виджетах: окошко с клавиатурой поверх рабочего стола, приложение не открывается. */
fun quickAddIntent(context: Context) = Intent(context, QuickAddActivity::class.java)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

fun doneTabIntent(context: Context) = Intent(context, MainActivity::class.java).setAction(Incoming.ACTION_DONE_TAB)

fun voiceIntent(context: Context) = Intent(context, MainActivity::class.java).setAction(Incoming.ACTION_VOICE)

private val TaskIdKey = ActionParameters.Key<Long>("taskId")
private val KindKey = ActionParameters.Key<String>("kind")

fun toggleAction(id: Long) = actionRunCallback<TaskAction>(actionParametersOf(TaskIdKey to id, KindKey to "toggle"))
fun doneAction(id: Long) = actionRunCallback<TaskAction>(actionParametersOf(TaskIdKey to id, KindKey to "done"))
fun postponeAction(id: Long) = actionRunCallback<TaskAction>(actionParametersOf(TaskIdKey to id, KindKey to "postpone"))

/** Галочка и кнопки на виджетах. То же, что кнопки в уведомлениях (см. ActionReceiver). */
class TaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        TaskRepo.init(context)
        val id = parameters[TaskIdKey] ?: return
        val done = when (parameters[KindKey]) {
            "toggle" -> TaskRepo.toggle(id, sync = true)
            "done" -> { TaskRepo.setDone(id, sync = true); true }
            "postpone" -> { TaskRepo.postpone(id, sync = true); false }
            else -> return
        }
        // Нажали на виджет — человек ждёт отклика: перерисовываем сразу, без обычной паузы.
        // Это же отменяет отложенную перерисовку от TaskRepo.onChanged, так что второй раз не рисуем.
        Widgets.refresh(context, delayMs = 0)
        Notifier.cancelForTask(context, id)
        if (done && Notifier.checkAllDone(context) != null) Notifier.celebrate(context)
    }
}

/**
 * Перерисовка всех виджетов. Изменения сыплются пачками, поэтому ждём чуть-чуть и рисуем один раз,
 * а сами перерисовки идут строго по очереди — наложение двух updateAll ломало виджеты.
 */
object Widgets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private var job: Job? = null

    @Synchronized
    fun refresh(context: Context, delayMs: Long = 300) {
        val app = context.applicationContext
        job?.cancel()
        job = scope.launch {
            delay(delayMs)
            updateNow(app)
        }
    }

    suspend fun updateNow(context: Context) = lock.withLock {
        runCatching {
            // «Сегодня» — обычные RemoteViews: обновляется мгновенно, без сессий Glance.
            TodayWidgetUi.updateAll(context)
            HotWidget().updateAll(context)
            CounterWidget().updateAll(context)
            StreakWidget().updateAll(context)
            RandomWidget().updateAll(context)
            // «Накинуть» от задач не зависит — не трогаем.
        }
    }
}
