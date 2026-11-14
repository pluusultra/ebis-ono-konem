package ru.konem.ebis.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import ru.konem.ebis.R
import ru.konem.ebis.data.DaySnapshot
import ru.konem.ebis.data.Task
import ru.konem.ebis.data.TaskRepo
import ru.konem.ebis.notify.Notifier
import ru.konem.ebis.notify.Prefs
import ru.konem.ebis.ui.Copy
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors

/** Сколько отмеченная задача ещё висит в списке зачёркнутой: видно, что нажатие сработало, и можно передумать. */
const val TICK_GRACE_MS = 2000L

/**
 * Как в обычных трекерах: в списке только висящие задачи на сегодня, в постоянном порядке
 * («горит» сверху, дальше как во вкладке «Сегодня»). Только что отмеченная остаётся на своём месте
 * зачёркнутой [TICK_GRACE_MS], потом уходит — её место занимают нижние строки.
 */
fun todayRows(tasks: List<Task>, today: LocalDate, newFirst: Boolean, now: Long = System.currentTimeMillis()): List<Task> =
    tasks.filter { t ->
        if (t.done) t.doneAt > 0 && now - t.doneAt < TICK_GRACE_MS else !t.due.isAfter(today)
    }.sortedWith(
        compareByDescending<Task> { it.hot }.then(if (newFirst) compareByDescending { it.order } else compareBy { it.order })
    )

/**
 * Виджет «Сегодня» на обычных RemoteViews. На Glance каждая строка списка открывала невидимую
 * активити-трамплин, а перерисовка шла через сессии на WorkManager — галочка срабатывала с задержкой.
 * Здесь нажатие на строку — прямой broadcast в [TodayWidgetReceiver], и виджет перерисовывается тут же.
 */
object TodayWidgetUi {
    private val main = Handler(Looper.getMainLooper())
    private var sweep: Runnable? = null

    fun ids(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, TodayWidgetReceiver::class.java))

    /** Перерисовать все «Сегодня» на рабочем столе. Дешёво: без сессий и очередей. */
    fun updateAll(context: Context) {
        val ids = ids(context)
        if (ids.isEmpty()) return
        TaskRepo.init(context)
        val mgr = AppWidgetManager.getInstance(context)
        ids.forEach { mgr.updateAppWidget(it, build(context, it)) }
        // До Android 12 строки отдаёт TodayListService — ему надо сказать, что данные поменялись.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) mgr.notifyAppWidgetViewDataChanged(ids, R.id.list)
        scheduleSweep(context)
    }

    /** Отмеченная задача висит зачёркнутой TICK_GRACE_MS — перерисовываем, когда ей пора уйти. */
    @Synchronized
    private fun scheduleSweep(context: Context) {
        val now = System.currentTimeMillis()
        val due = TaskRepo.tasks.filter { it.done && now - it.doneAt < TICK_GRACE_MS }.maxOfOrNull { it.doneAt + TICK_GRACE_MS } ?: return
        sweep?.let(main::removeCallbacks)
        val app = context.applicationContext
        sweep = Runnable { updateAll(app) }.also { main.postDelayed(it, due - now + 100) }
    }

    fun rows(context: Context): List<Task> =
        todayRows(TaskRepo.tasks, LocalDate.now(), Prefs(context).todayNewFirst)

    private fun build(context: Context, widgetId: Int): RemoteViews {
        val today = LocalDate.now()
        val day = DaySnapshot(TaskRepo.tasks, today)
        val rows = rows(context)
        val done = day.doneToday
        val total = done + day.remaining.size

        val v = RemoteViews(context.packageName, R.layout.widget_today)
        v.setImageViewBitmap(R.id.title, textBitmap(context, "Сегодня", 12f, R.color.w_onhay))
        v.setTextViewText(R.id.count, if (total == 0) "· пусто" else "· $done из $total")
        v.setViewVisibility(R.id.progress, if (total > 0) View.VISIBLE else View.INVISIBLE)
        v.setProgressBar(R.id.progress, 100, if (total == 0) 0 else done * 100 / total, false)

        v.setOnClickPendingIntent(R.id.header, activity(context, 1, openAppIntent(context)))
        v.setOnClickPendingIntent(R.id.add, activity(context, 2, quickAddIntent(context)))
        v.setOnClickPendingIntent(R.id.empty, activity(context, 2, quickAddIntent(context)))

        v.setTextViewText(
            R.id.empty,
            if (total == 0) "😶 На сегодня ничего. Подозрительно." else "🏆 Всё закрыл. Жеребец.",
        )
        v.setEmptyView(R.id.list, R.id.empty)

        v.setViewVisibility(R.id.done_row, if (done > 0) View.VISIBLE else View.GONE)
        v.setTextViewText(R.id.done_row, "✓ сделано сегодня: $done")
        v.setOnClickPendingIntent(R.id.done_row, activity(context, 3, doneTabIntent(context)))

        // Нажатие на строку — broadcast с id задачи (fill-in), без активити.
        val template = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, TodayWidgetReceiver::class.java).setAction(TodayWidgetReceiver.ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        v.setPendingIntentTemplate(R.id.list, template)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val items = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(true).setViewTypeCount(1)
            rows.forEach { items.addItem(it.id, row(context, it, today)) }
            v.setRemoteAdapter(R.id.list, items.build())
        } else {
            @Suppress("DEPRECATION")
            v.setRemoteAdapter(
                R.id.list,
                Intent(context, TodayListService::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    .setData(android.net.Uri.parse("ebis://today/$widgetId")),
            )
        }
        return v
    }

    fun row(context: Context, t: Task, today: LocalDate): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_today_row)
        v.setImageViewResource(R.id.check, if (t.done) R.drawable.widget_check_on else R.drawable.widget_check_off)
        if (t.done) {
            val s = SpannableString(t.title).apply { setSpan(StrikethroughSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            v.setTextViewText(R.id.title, s)
            v.setTextColor(R.id.title, ContextCompat.getColor(context, R.color.w_faint))
        } else {
            v.setTextViewText(R.id.title, t.title)
            v.setTextColor(R.id.title, ContextCompat.getColor(context, R.color.w_ink))
        }
        val late = ChronoUnit.DAYS.between(t.due, today)
        val red = when {
            t.done -> null
            t.hot -> "горит"
            late > 0 -> "−$late дн"
            else -> null
        }
        v.setViewVisibility(R.id.tag_red, if (red != null) View.VISIBLE else View.GONE)
        red?.let { v.setTextViewText(R.id.tag_red, it) }
        val plain = if (t.done) null else t.time?.let { Copy.fmtTime(it) }
        v.setViewVisibility(R.id.tag, if (plain != null) View.VISIBLE else View.GONE)
        plain?.let { v.setTextViewText(R.id.tag, it) }
        v.setOnClickFillInIntent(R.id.row, Intent().putExtra(TodayWidgetReceiver.EXTRA_TASK, t.id))
        return v
    }

    private fun activity(context: Context, code: Int, intent: Intent) =
        PendingIntent.getActivity(context, 7000 + code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

/**
 * Имя класса прежнее (раньше тут был приёмник Glance) — чтобы уже стоящие на столе виджеты
 * не пропали после обновления.
 */
class TodayWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        TodayWidgetUi.updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) {
            super.onReceive(context, intent)
            return
        }
        val id = intent.getLongExtra(EXTRA_TASK, -1)
        if (id < 0) return
        val pending = goAsync()
        TaskRepo.init(context)
        // Сначала в памяти и сразу на экран; файл, напоминания и остальные виджеты — следом.
        val done = TaskRepo.toggle(id)
        TodayWidgetUi.updateAll(context)
        background.execute {
            try {
                Notifier.cancelForTask(context, id)
                if (done && Notifier.checkAllDone(context) != null) Notifier.celebrate(context)
                TaskRepo.flush()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "ru.konem.ebis.widget.TODAY_TOGGLE"
        const val EXTRA_TASK = "taskId"
        private val background = Executors.newSingleThreadExecutor()
    }
}

/** Строки списка для Android 8–11: там нет RemoteCollectionItems. */
class TodayListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = object : RemoteViewsFactory {
        private var rows: List<Task> = emptyList()

        override fun onCreate() = Unit
        override fun onDataSetChanged() {
            TaskRepo.init(applicationContext)
            rows = TodayWidgetUi.rows(applicationContext)
        }
        override fun onDestroy() = Unit
        override fun getCount() = rows.size
        override fun getViewAt(position: Int): RemoteViews =
            TodayWidgetUi.row(applicationContext, rows[position], LocalDate.now())
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = rows[position].id
        override fun hasStableIds() = true
    }
}
