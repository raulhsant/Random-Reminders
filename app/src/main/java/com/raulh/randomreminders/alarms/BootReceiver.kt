package com.raulh.randomreminders.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.raulh.randomreminders.di.AppScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Alarmes do AlarmManager não sobrevivem ao reboot nem a mudanças de
 * relógio/fuso: este receiver reagenda TODAS as regras ativas a partir do
 * estado persistido (ver [AlarmScheduler.scheduleAll]).
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: AlarmScheduler

    @AppScope
    @Inject
    lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED
        ) return

        val result = goAsync()
        appScope.launch {
            try {
                scheduler.scheduleAll()
            } finally {
                result.finish()
            }
        }
    }
}
