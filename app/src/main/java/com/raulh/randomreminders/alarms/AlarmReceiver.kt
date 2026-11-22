package com.raulh.randomreminders.alarms

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.raulh.randomreminders.data.ReminderRepository
import com.raulh.randomreminders.di.AppScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Recebe o disparo de uma regra: registra o histórico, publica a notificação
 * de alarme, abre a tela cheia para o usuário parar o som e agenda o próximo
 * horário aleatório.
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
        val ruleId = intent.getLongExtra(EXTRA_RULE_ID, -1L)
        if (ruleId <= 0) return

        when (intent.action) {
            ACTION_DISMISS_NOTIFICATION -> {
                notifier.dismissReminder(ruleId)
                return
            }
            ACTION_ALARM_FIRED -> Unit
            else -> return
        }

        val result = goAsync()
        appScope.launch {
            try {
                repository.consumeOldestPending(ruleId) ?: return@launch
                val rule = repository.getRule(ruleId)
                val screenIntent = notifier.showReminder(
                    ruleId,
                    rule?.name,
                    rule?.description,
                    rule?.soundUri,
                )
                // A notificação já tem full-screen intent (usado pelo sistema
                // quando a tela está bloqueada/desligada). O launch direto
                // garante a tela do alarme também com o aparelho desbloqueado,
                // onde o Android costuma mostrar apenas o heads-up.
                launchAlarmScreen(context, ruleId, screenIntent)
                scheduler.onAlarmFired(ruleId)
            } finally {
                result.finish()
            }
        }
    }

    /**
     * Abre a [AlarmActivity] a partir do receiver. Em Android 14+ o launch de
     * atividade em segundo plano é restrito; nesse caso usamos um PendingIntent
     * com opt-in explícito. Se o sistema ainda bloquear, a notificação com
     * full-screen intent continua sendo o caminho de fallback.
     */
    private fun launchAlarmScreen(context: Context, ruleId: Long, screenIntent: Intent) {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
        val launchIntent = Intent(screenIntent).addFlags(flags)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val creatorOptions = ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                creatorOptions.setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                )
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                SCREEN_REQUEST_CODE + ruleId.toInt(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                creatorOptions.toBundle(),
            )
            val senderOptions = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                )
            try {
                pendingIntent.send(context, 0, null, null, null, null, senderOptions.toBundle())
            } catch (exception: PendingIntent.CanceledException) {
                Log.e(TAG, "Não foi possível abrir a tela do alarme; a notificação permanece disponível.", exception)
            } catch (exception: SecurityException) {
                Log.w(TAG, "O Android bloqueou a abertura direta da tela; a notificação permanece disponível.", exception)
            }
        } else {
            try {
                context.startActivity(launchIntent)
            } catch (exception: SecurityException) {
                Log.w(TAG, "O Android bloqueou a abertura direta da tela; a notificação permanece disponível.", exception)
            }
        }
    }

    companion object {
        private const val TAG = "AlarmReceiver"
        const val ACTION_ALARM_FIRED = "com.raulh.randomreminders.ACTION_ALARM_FIRED"
        const val ACTION_DISMISS_NOTIFICATION = "com.raulh.randomreminders.ACTION_DISMISS_NOTIFICATION"
        const val EXTRA_RULE_ID = "rule_id"

        private const val SCREEN_REQUEST_CODE = 10_000
    }
}
