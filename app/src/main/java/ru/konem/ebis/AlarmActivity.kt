package ru.konem.ebis

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ru.konem.ebis.notify.AlarmService
import ru.konem.ebis.ui.AlarmScreen
import ru.konem.ebis.ui.theme.EbisTheme

/** Тревога «Горит» на весь экран, в том числе поверх экрана блокировки. Закрывается, когда будильник ответили. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            EbisTheme {
                val r by AlarmService.ringing.collectAsState()
                LaunchedEffect(r) { if (r == null) finish() }
                r?.let { ringing ->
                    AlarmScreen(ringing) { a ->
                        AlarmService.answer(this, ringing.id, a)?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
                        finish()
                    }
                }
            }
        }
    }
}
