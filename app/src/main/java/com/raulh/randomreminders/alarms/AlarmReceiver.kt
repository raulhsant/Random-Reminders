package com.raulh.randomreminders.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.raulh.randomreminders.data.ReminderRepository
import com.raulh.randomreminders.di.AppScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Recebe o disparo de uma regra: registra o histórico, publica a notificação
 * (com o nome da regra) e agenda o próximo horário aleatório.
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: AlarmScheduler

    @Inject lateinit var repository: ReminderRepository

    @Inject lateinit var notifier: NotificationHelper

    @AppScope
    @Inject
    lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM_FIRED) return
        val ruleId = intent.getLongExtra(EXTRA_RULE_ID, -1L)
        if (ruleId <= 0) return

        val result = goAsync()
        appScope.launch {
            try {
                repository.consumeOldestPending(ruleId) ?: return@launch
                val rule = repository.getRule(ruleId)
                notifier.showReminder(ruleId, rule?.name, rule?.description, rule?.soundUri)
                scheduler.onAlarmFired(ruleId)
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val ACTION_ALARM_FIRED = "com.raulh.randomreminders.ACTION_ALARM_FIRED"
        const val EXTRA_RULE_ID = "rule_id"
    }
}
