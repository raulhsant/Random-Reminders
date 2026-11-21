package com.raulh.randomreminders.alarms

import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
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

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    @Inject lateinit var notifier: NotificationHelper

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null
    private var ruleId = INVALID_RULE_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ruleId = intent.getLongExtra(EXTRA_RULE_ID, INVALID_RULE_ID)
        val startedAt = intent.getLongExtra(EXTRA_STARTED_AT, 0L)
        if (ruleId <= 0 || startedAt <= 0L) {
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
                    title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                    message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty(),
                    onDismiss = ::dismissAlarm,
                )
            }
        }

        val remainingMillis = (ALARM_DURATION_MILLIS - (System.currentTimeMillis() - startedAt))
            .coerceAtLeast(0L)
        timeoutRunnable = Runnable { dismissAlarm() }.also {
            timeoutHandler.postDelayed(it, remainingMillis)
        }
    }

    override fun onDestroy() {
        timeoutRunnable?.let(timeoutHandler::removeCallbacks)
        super.onDestroy()
    }

    private fun dismissAlarm() {
        notifier.dismissReminder(ruleId)
        finish()
    }

    companion object {
        const val EXTRA_RULE_ID = "alarm_rule_id"
        const val EXTRA_STARTED_AT = "alarm_started_at"
        const val EXTRA_TITLE = "alarm_title"
        const val EXTRA_MESSAGE = "alarm_message"

        private const val INVALID_RULE_ID = -1L
        private const val ALARM_DURATION_MILLIS = 15_000L
    }
}

@Composable
private fun AlarmScreen(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
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
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
            ) {
                Text(stringResource(R.string.action_dismiss))
            }
        }
    }
}
