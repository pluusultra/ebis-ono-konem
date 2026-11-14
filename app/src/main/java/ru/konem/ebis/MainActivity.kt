package ru.konem.ebis

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.konem.ebis.ui.AppRoot
import ru.konem.ebis.notify.Prefs
import ru.konem.ebis.ui.theme.EbisTheme
import ru.konem.ebis.ui.theme.ThemeMode

/** Что принесли снаружи: ярлык «Накинуть» или текст через «Поделиться». */
data class Prefill(val title: String = "", val desc: String = "")

object Incoming {
    var prefill by mutableStateOf<Prefill?>(null)

    /** Открыть вкладку «Сделано» (строка «✓ сделано сегодня» на виджете). */
    var openDone by mutableStateOf(false)

    const val ACTION_ADD = "ru.konem.ebis.ADD"

    /** Микрофон на виджете «Накинуть»: надиктовать задачу. */
    const val ACTION_VOICE = "ru.konem.ebis.VOICE"

    const val ACTION_DONE_TAB = "ru.konem.ebis.DONE_TAB"
}

class MainActivity : ComponentActivity() {
    // Сказанное попадает в форму как название: «завтра в 15» и прочее форма разберёт сама.
    private val voice = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (!text.isNullOrEmpty()) Incoming.prefill = Prefill(text.replaceFirstChar { it.uppercase() })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Цвет иконок статус-бара потом выставляет EbisTheme по выбранной теме.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        ThemeMode.mode = Prefs(this).themeMode
        // После поворота/пересоздания интент тот же — второй раз форму не открываем.
        if (savedInstanceState == null) handle(intent)
        setContent {
            EbisTheme {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            // С быстрого добавления по «⤢» приходит уже набранный текст.
            Incoming.ACTION_ADD -> Incoming.prefill = Prefill(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            Incoming.ACTION_DONE_TAB -> Incoming.openDone = true
            Incoming.ACTION_VOICE -> {
                val ask = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Какую херню накинуть?")
                // Нет распознавания речи на телефоне — просто открываем форму.
                try {
                    voice.launch(ask)
                } catch (e: ActivityNotFoundException) {
                    Incoming.prefill = Prefill()
                }
            }
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
                val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
                if (text.isEmpty() && subject.isEmpty()) return
                // Тема письма — в название, текст — в суть. Без темы: первая строка — название.
                Incoming.prefill = if (subject.isNotEmpty()) {
                    Prefill(subject.take(120), text)
                } else {
                    val first = text.lineSequence().first().take(120)
                    Prefill(first, text.removePrefix(first).trim())
                }
            }
        }
    }
}
