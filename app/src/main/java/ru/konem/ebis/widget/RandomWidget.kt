package ru.konem.ebis.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.sp
import ru.konem.ebis.R
import ru.konem.ebis.data.Task
import ru.konem.ebis.data.TaskRepo
import ru.konem.ebis.notify.Notifier
import ru.konem.ebis.ui.Copy
import java.time.LocalDate

/**
 * «Рандом»: конь смотрит на тебя, качает головой и выбирает задачу на сегодня.
 * Не нравится — «давай другое», но конь это запомнит. Состояние (какая задача выпала,
 * сколько раз отказался) хранится у каждого экземпляра виджета отдельно.
 */
class RandomWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        TaskRepo.init(context)
        provideContent { Content() }
    }

    @Composable
    private fun Content() {
        val context = LocalContext.current
        val state = currentState<Preferences>()
        val left = todayLeft()
        val picked = state[TaskKey]?.let { id -> left.firstOrNull { it.id == id } }
        val mode = state[ModeKey]

        Row(
            GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg_paper))
                .padding(start = 6.dp, top = 4.dp, end = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(GlanceModifier.width(96.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                AndroidRemoteViews(RemoteViews(context.packageName, R.layout.widget_horse_flipper), GlanceModifier.fillMaxSize())
            }
            Spacer(GlanceModifier.width(6.dp))
            Column(GlanceModifier.defaultWeight().fillMaxHeight().padding(top = 6.dp)) {
                when {
                    picked != null -> {
                        Text(state[LineKey] ?: "Конь выбрал:", style = mono(10f, WColor.Red), maxLines = 2)
                        Spacer(GlanceModifier.height(3.dp))
                        Text(picked.title, style = bold(14.5f), maxLines = 2)
                        Spacer(GlanceModifier.defaultWeight())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                GlanceModifier.size(32.dp).background(ImageProvider(R.drawable.widget_btn_green))
                                    .clickable(actionRunCallback<RandomDoneAction>()),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("✓", style = bold(15f, WColor.White), modifier = GlanceModifier.padding(end = 2.dp, bottom = 2.dp))
                            }
                            Spacer(GlanceModifier.width(6.dp))
                            Button("Не, давай другое", R.drawable.widget_btn_paper, WColor.Ink, actionRunCallback<RerollAction>())
                        }
                    }
                    left.isEmpty() -> {
                        Text(if (mode == MODE_DONE) state[LineKey] ?: "Всё закрыл" else "На сегодня пусто", style = bold(14f), maxLines = 2)
                        Spacer(GlanceModifier.height(3.dp))
                        Text("Конь в шоке. Рандомить нечего — накинь что-нибудь.", style = plain(11f), maxLines = 2)
                        Spacer(GlanceModifier.defaultWeight())
                        Button("+ Накинуть задачу", R.drawable.widget_btn_hay, WColor.OnHay, actionStartActivity(quickAddIntent(context)))
                    }
                    else -> {
                        if (mode == MODE_DONE) {
                            Text(state[LineKey] ?: "Минус одна задача", style = bold(14f), maxLines = 2)
                            Spacer(GlanceModifier.height(3.dp))
                            Text("Ещё одну? ${Copy.left(left.size)}.", style = plain(11f), maxLines = 2)
                        } else {
                            Text("Не знаешь, за что взяться?", style = bold(14f), maxLines = 2)
                            Spacer(GlanceModifier.height(3.dp))
                            Text("Конь выберет за тебя. Он всё равно умнее.", style = plain(11f), maxLines = 2)
                        }
                        Spacer(GlanceModifier.defaultWeight())
                        Button(
                            if (mode == MODE_DONE) "🎲 Давай ещё" else "🎲 Бросить жребий",
                            R.drawable.widget_btn_hay, WColor.OnHay, actionRunCallback<RollAction>(),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun Button(text: String, bg: Int, color: ColorProvider, action: Action) {
        Box(
            GlanceModifier.background(ImageProvider(bg)).clickable(action)
                .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
        ) {
            Text(text, style = bold(11.5f, color), maxLines = 1)
        }
    }

    companion object {
        val TaskKey = longPreferencesKey("taskId")
        val RejectsKey = intPreferencesKey("rejects")
        val LineKey = stringPreferencesKey("line")
        val ModeKey = stringPreferencesKey("mode")
        const val MODE_DONE = "done"

        fun todayLeft(): List<Task> {
            val today = LocalDate.now()
            return TaskRepo.tasks.filter { !it.done && !it.due.isAfter(today) }
        }

        val rollLines = listOf(
            "Конь выбрал:",
            "Судьба говорит:",
            "Сегодня ты делаешь вот это:",
            "Жребий брошен:",
            "Копытом ткнул в:",
            "Звёзды сошлись на:",
        )

        /** Чем больше отказов, тем меньше у коня терпения. */
        fun rejectLine(n: Int): String = when (n) {
            1 -> "Ладно, вот другая. Конь запомнил."
            2 -> "А какой смысл, если и так откажешься?"
            3 -> "Ты листаешь задачи, как ленту в тиктоке."
            4 -> "Конь устал. Бери, что дают."
            else -> listOf(
                "Отказ №$n. Рекорд близко.",
                "Рандом не виноват, что ты бедолага.",
                "Ещё раз — и конь сам всё сделает. Шучу, не сделает.",
                "Может, просто поплачешь?",
                "Конь видел многое. Но такое — впервые.",
            ).random()
        }
    }
}

class RandomWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RandomWidget()
}

private fun bold(size: Float, color: ColorProvider = WColor.Ink) =
    TextStyle(fontSize = size.sp, fontWeight = FontWeight.Bold, color = color)

private fun plain(size: Float) = TextStyle(fontSize = size.sp, fontWeight = FontWeight.Medium, color = WColor.Mute)

private fun mono(size: Float, color: ColorProvider) = TextStyle(fontSize = size.sp, fontFamily = FontFamily.Monospace, color = color)

/** «Бросить жребий»: случайная задача из сегодняшних. */
class RollAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        TaskRepo.init(context)
        val pick = RandomWidget.todayLeft().randomOrNull()
        updateAppWidgetState(context, glanceId) {
            it.remove(RandomWidget.ModeKey)
            it[RandomWidget.RejectsKey] = 0
            if (pick == null) it.remove(RandomWidget.TaskKey) else it[RandomWidget.TaskKey] = pick.id
            it[RandomWidget.LineKey] = RandomWidget.rollLines.random()
        }
        RandomWidget().update(context, glanceId)
    }
}

/** «Не, давай другое»: другая задача и всё более ехидная подпись. */
class RerollAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        TaskRepo.init(context)
        updateAppWidgetState(context, glanceId) {
            val left = RandomWidget.todayLeft()
            val current = it[RandomWidget.TaskKey]
            val others = left.filter { t -> t.id != current }
            val rejects = (it[RandomWidget.RejectsKey] ?: 0) + 1
            it[RandomWidget.RejectsKey] = rejects
            if (others.isEmpty()) {
                it[RandomWidget.LineKey] = "Других нет. Только эта. Смирись."
            } else {
                it[RandomWidget.TaskKey] = others.random().id
                it[RandomWidget.LineKey] = RandomWidget.rejectLine(rejects)
            }
        }
        RandomWidget().update(context, glanceId)
    }
}

/** «Сделал» на выпавшей задаче: закрываем её и хвалим. */
class RandomDoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        TaskRepo.init(context)
        var id: Long? = null
        updateAppWidgetState(context, glanceId) {
            id = it[RandomWidget.TaskKey]
            it.remove(RandomWidget.TaskKey)
            it[RandomWidget.RejectsKey] = 0
            it[RandomWidget.ModeKey] = RandomWidget.MODE_DONE
            it[RandomWidget.LineKey] = Copy.praise.random()
        }
        id?.let { taskId ->
            TaskRepo.setDone(taskId, sync = true)
            Notifier.cancelForTask(context, taskId)
            if (Notifier.checkAllDone(context) != null) Notifier.celebrate(context)
        }
        RandomWidget().update(context, glanceId)
    }
}
