package com.raulh.randomreminders.ui

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Solicita as permissões exigidas para o funcionamento dos alarmes:
 * - POST_NOTIFICATIONS (Android 13+): não é pedida em tempo de instalação;
 * - alarmes exatos (Android 12+): envia o usuário à tela de configuração
 *   do sistema; sem a permissão, o app continua com alarmes inexatos;
 * - notificações em tela cheia (Android 14+): necessárias para a tela do
 *   alarme aparecer com o aparelho bloqueado.
 */
object ReminderPermissions {

    private const val notificationPermission = Manifest.permission.POST_NOTIFICATIONS

    fun needsNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, notificationPermission) !=
            PackageManager.PERMISSION_GRANTED

    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(notificationPermission),
                // requestCode arbitrário, interno à app
                100,
            )
        }
    }

    /** Abre a tela de sistema "Alarmes e lembretes" quando não há permissão de alarme exato. */
    fun needsExactAlarmPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        return !alarmManager.canScheduleExactAlarms()
    }

    fun openExactAlarmSettings(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(Uri.parse("package:${context.packageName}"))
        runCatching { context.startActivity(intent) }
    }

    /** Android 14+ pode negar o full-screen intent mesmo com a permissão declarada. */
    fun needsFullScreenIntentPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return !manager.canUseFullScreenIntent()
    }

    fun openFullScreenIntentSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            .setData(Uri.parse("package:${context.packageName}"))
        runCatching { context.startActivity(intent) }
    }

    /** Tela de notificações do app (quando o usuário negou ou desativou o canal). */
    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        runCatching { context.startActivity(intent) }
    }
}
