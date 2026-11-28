package ru.konem.ebis.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import ru.konem.ebis.R
import ru.konem.ebis.data.DaySnapshot
import ru.konem.ebis.data.Repeat
import ru.konem.ebis.data.Task
import ru.konem.ebis.data.TaskRepo
import ru.konem.ebis.notify.Prefs
import ru.konem.ebis.ui.Copy
import ru.konem.ebis.ui.Copy.plural
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Пять виджетов рабочего стола. Подложки с рамкой и жёсткой тенью — drawable widget_bg_*:
 * нижний слой тени сдвинут на 2dp, поэтому содержимое отступает справа и снизу чуть больше.
 */

private fun bold(size: Float, color: ColorProvider = WColor.Ink) =
    TextStyle(fontSize = size.sp, fontWeight = FontWeight.Bold, color = color)

private fun plain(size: Float, color: ColorProvider = WColor.Mute) =
    TextStyle(fontSize = size.sp, fontWeight = FontWeight.Medium, color = color)

private fun mono(size: Float, color: ColorProvider = WColor.Ink) =
    TextStyle(fontSize = size.sp, fontFamily = FontFamily.Monospace, color = color)

/** Бейдж как в карточке задачи: моноширинный текст в рамке. */
@Composable
private fun Tag(text: String, red: Boolean = false) {
    Text(
        text,
        style = mono(9.5f, if (red) WColor.White else WColor.Ink),
        maxLines = 1,
        modifier = GlanceModifier
            .background(ImageProvider(if (red) R.drawable.widget_tag_red else R.drawable.widget_tag))
            .padding(horizontal = 4.dp, vertical = 0.dp),
    )
}

private fun loadTasks(context: Context): List<Task> {
    TaskRepo.init(context)
    return TaskRepo.tasks
}

// ─── 1. Счётчик 2×2 ────────────────────────────────────────────────────────────

class CounterWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        loadTasks(context)
        provideContent { Counter() }
    }

    @Composable
    private fun Counter() {
        val context = LocalContext.current
        val day = DaySnapshot(TaskRepo.tasks)
        val n = day.remaining.size
        val rotten = day.overdue.size
        val label = when {
            n == 0 -> "ноль задач.\nПодозрительно."
            else -> plural(n, "задача осталась", "задачи осталось", "задач осталось") + "\nна сегодня"
        }
        Box(
            GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg_hay))
                .clickable(actionStartActivity(openAppIntent(context))),
        ) {
            Column(GlanceModifier.fillMaxSize().padding(start = 12.dp, top = 10.dp, end = 14.dp, bottom = 14.dp)) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Image(ImageProvider(R.drawable.logo), null, GlanceModifier.size(28.dp))
                    Spacer(GlanceModifier.defaultWeight())
                    if (rotten > 0) Tag("−$rotten ${plural(rotten, "протухла", "протухли", "протухли")}", red = true)
                }
                Spacer(GlanceModifier.defaultWeight())
                Image(ImageProvider(textBitmap(context, "$n", 38f, R.color.w_onhay)), "$n")
                Text(label, style = bold(11f, WColor.HeaderSub), maxLines = 2)
            }
        }
    }
}

class CounterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CounterWidget()
}

// ─── 2. Серия 2×2 ──────────────────────────────────────────────────────────────

class StreakWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        loadTasks(context)
        provideContent { Streak() }
    }

    @Composable
    private fun Streak() {
        val context = LocalContext.current
        val day = DaySnapshot(TaskRepo.tasks)
        val streak = Prefs(context).liveStreak(day.today.toEpochDay())
        val done = day.doneToday
        val total = done + day.remaining.size
        val hint = when {
            total == 0 -> "На сегодня пусто. Отдыхай, жеребец"
            day.remaining.isEmpty() -> "Всё закрыл. Жеребец 🏆"
            streak == 0 -> "Закрой всё — начнёшь серию"
            else -> "Ещё ${Copy.taskCount(day.remaining.size)} — и серия живёт"
        }
        Box(
            GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg_paper))
                .clickable(actionStartActivity(openAppIntent(context))),
        ) {
            Column(GlanceModifier.fillMaxSize().padding(start = 12.dp, top = 10.dp, end = 14.dp, bottom = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔥", style = TextStyle(fontSize = 18.sp))
                    Spacer(GlanceModifier.width(4.dp))
                    Image(ImageProvider(textBitmap(context, "$streak", 26f, R.color.w_ink)), "$streak")
                    Spacer(GlanceModifier.width(5.dp))
                    Text(plural(streak, "день\nподряд", "дня\nподряд", "дней\nподряд"), style = bold(10f, WColor.Mute), maxLines = 2)
                }
                Spacer(GlanceModifier.defaultWeight())
                Row(GlanceModifier.fillMaxWidth()) {
                    Text("Закрыл", style = bold(10.5f), modifier = GlanceModifier.defaultWeight())
                    Text("$done/$total", style = mono(10.5f))
                }
                Spacer(GlanceModifier.height(4.dp))
                Box(GlanceModifier.fillMaxWidth().background(ImageProvider(R.drawable.widget_progress)).padding(2.dp)) {
                    LinearProgressIndicator(
                        progress = if (total == 0) 0f else done.toFloat() / total,
                        modifier = GlanceModifier.fillMaxWidth().height(7.dp),
                        color = WColor.Green,
                        backgroundColor = WColor.Card,
                    )
                }
                Spacer(GlanceModifier.height(6.dp))
                Text(hint, style = plain(10f), maxLines = 2)
            }
        }
    }
}

class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()
}

// ─── 3. Сегодня — в TodayWidget.kt (обычные RemoteViews ради быстрой галочки) ────

// ─── 4. Горит 4×1 ──────────────────────────────────────────────────────────────

class HotWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        loadTasks(context)
        provideContent { Hot() }
    }

    @Composable
    private fun Hot() {
        val context = LocalContext.current
        val today = LocalDate.now()
        val t = hottest(TaskRepo.tasks, today)
        Row(
            GlanceModifier.fillMaxSize().background(ImageProvider(if (t != null) R.drawable.widget_bg_hot else R.drawable.widget_bg_paper))
                .padding(start = 12.dp, end = 10.dp, top = 2.dp, bottom = 4.dp)
                .clickable(actionStartActivity(openAppIntent(context))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (t == null) {
                Text("🐴", style = TextStyle(fontSize = 18.sp))
                Spacer(GlanceModifier.width(8.dp))
                Text("Ничего не горит. Подозрительно 👀", style = bold(12.5f), maxLines = 1)
                return@Row
            }
            Text("🔥", style = TextStyle(fontSize = 18.sp))
            Spacer(GlanceModifier.width(8.dp))
            Column(GlanceModifier.defaultWeight()) {
                val late = ChronoUnit.DAYS.between(t.due, today).toInt()
                val kicker = when {
                    t.hot -> "горит"
                    late > 0 -> "опоздание на $late ${plural(late, "день", "дня", "дней")}"
                    else -> t.time?.let { "сегодня в ${Copy.fmtTime(it)}" } ?: "на сегодня"
                }
                Text(kicker, style = mono(9.5f, WColor.Red), maxLines = 1)
                Text(t.title, style = bold(12.5f), maxLines = 1)
            }
            Spacer(GlanceModifier.width(6.dp))
            Box(
                GlanceModifier.size(34.dp).background(ImageProvider(R.drawable.widget_btn_green)).clickable(doneAction(t.id)),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", style = bold(15f, WColor.White), modifier = GlanceModifier.padding(end = 2.dp, bottom = 2.dp))
            }
            Spacer(GlanceModifier.width(6.dp))
            Box(
                GlanceModifier.size(34.dp).background(ImageProvider(R.drawable.widget_btn_hay)).clickable(postponeAction(t.id)),
                contentAlignment = Alignment.Center,
            ) {
                Text("⏭", style = bold(13f, WColor.OnHay), modifier = GlanceModifier.padding(end = 2.dp, bottom = 2.dp))
            }
        }
    }
}

class HotWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HotWidget()
}

// ─── 5. Накинуть 4×1 ───────────────────────────────────────────────────────────

class QuickWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Quick() }
    }

    @Composable
    private fun Quick() {
        val context = LocalContext.current
        Row(
            GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg_quick))
                .padding(start = 16.dp, end = 14.dp, top = 2.dp, bottom = 4.dp)
                .clickable(actionStartActivity(quickAddIntent(context))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("+", style = bold(20f, WColor.Red))
            Spacer(GlanceModifier.width(10.dp))
            Text("Накинуть задачу…", style = bold(13.5f, WColor.Hay), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            Image(
                ImageProvider(R.drawable.widget_mic), "Надиктовать",
                GlanceModifier.size(30.dp).padding(4.dp).clickable(actionStartActivity(voiceIntent(context))),
            )
        }
    }
}

class QuickWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickWidget()
}
