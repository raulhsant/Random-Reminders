package com.raulh.randomreminders.alarms

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.raulh.randomreminders.R
import com.raulh.randomreminders.ui.theme.RandomRemindersTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Tela cheia do alarme. É aberta pelo [AlarmReceiver] assim que o disparo
 * acontece (e também pelo full-screen intent da notificação, quando o sistema
 * permite) para que o usuário veja o alarme e possa pará-lo imediatamente,
 * inclusive com o aparelho bloqueado ou com a tela desligada.
 */
@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    @Inject lateinit var notifier: NotificationHelper

    private var ruleId = INVALID_RULE_ID
    private val title = mutableStateOf("")
    private val message = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!applyIntent(intent)) {
            finish()
            return
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            RandomRemindersTheme {
                AlarmScreen(
                    title = title.value,
                    message = message.value,
                    onStop = ::stopAlarm,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntent(intent)
    }

    /** Lê os extras do disparo; devolve false quando o intent não é válido. */
    private fun applyIntent(intent: Intent): Boolean {
        val id = intent.getLongExtra(EXTRA_RULE_ID, INVALID_RULE_ID)
        val started = intent.getLongExtra(EXTRA_STARTED_AT, 0L)
        if (id <= 0 || started <= 0L) return false

        ruleId = id
        title.value = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        message.value = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
        return true
    }

    /** Para o som/notificação e fecha a tela do alarme. */
    private fun stopAlarm() {
        notifier.dismissReminder(ruleId)
        finish()
    }

    companion object {
        const val EXTRA_RULE_ID = "alarm_rule_id"
        const val EXTRA_STARTED_AT = "alarm_started_at"
        const val EXTRA_TITLE = "alarm_title"
        const val EXTRA_MESSAGE = "alarm_message"
        private const val INVALID_RULE_ID = -1L
    }
}

@Composable
private fun AlarmScreen(
    title: String,
    message: String,
    onStop: () -> Unit,
) {
    BackHandler(onBack = onStop)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
            ) {
                Text(stringResource(R.string.action_stop_alarm))
            }
        }
    }
}
