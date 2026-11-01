package com.raulh.randomreminders.alarms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.raulh.randomreminders.MainActivity
import com.raulh.randomreminders.R
import com.raulh.randomreminders.domain.NotificationMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publica a notificação estilo "despertador" disparada pelo [AlarmReceiver]:
 * canal de alta prioridade + full-screen intent para aparecer na tela de bloqueio.
 */
@Singleton
class NotificationHelper @Inject constructor(@ApplicationContext private val context: Context) {

    fun showReminder(ruleId: Long, ruleName: String?, description: String?, soundUri: String?) {
        val channelId = ensureChannel(ruleId, ruleName, soundUri)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        runCatching {
            NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(ruleName?.takeIf(String::isNotBlank) ?: context.getString(R.string.app_name))
                .setContentText(
                    NotificationMessage.resolve(
                        description,
                        context.getString(R.string.reminder_notification_text),
                    ),
                )
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setFullScreenIntent(contentIntent(), true)
                .build()
                .also { manager.notify(ruleId.hashCode(), it) }
        }
    }

    fun syncSoundChannel(ruleId: Long, ruleName: String?, soundUri: String?) {
        ensureChannel(ruleId, ruleName, soundUri)
    }

    fun deleteChannels(ruleId: Long) {
        val prefix = "random_reminders_${ruleId}_"
        context.getSystemService(NotificationManager::class.java)
            .notificationChannels
            .filter { it.id.startsWith(prefix) }
            .forEach { context.getSystemService(NotificationManager::class.java).deleteNotificationChannel(it.id) }
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        CONTENT_REQUEST_CODE,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ensureChannel(ruleId: Long, ruleName: String?, soundUri: String?): String {
        val channelId = channelId(ruleId, soundUri)
        val channel = NotificationChannel(
            channelId,
            ruleName?.takeIf(String::isNotBlank) ?: context.getString(R.string.channel_reminders_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_reminders_description)
            enableVibration(true)
            setSound(
                soundUri.toSoundUri(),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        val systemManager = context.getSystemService(NotificationManager::class.java)
        systemManager.notificationChannels
            .filter { it.id.startsWith("random_reminders_${ruleId}_") && it.id != channelId }
            .forEach { systemManager.deleteNotificationChannel(it.id) }
        systemManager.createNotificationChannel(channel)
        return channelId
    }

    private fun channelId(ruleId: Long, soundUri: String?): String =
        "random_reminders_${ruleId}_${Integer.toHexString(soundUri?.hashCode() ?: 0)}"

    private fun String?.toSoundUri(): Uri? = when (this) {
        "silent" -> null
        null -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        else -> Uri.parse(this)
    }

    private companion object {
        const val CONTENT_REQUEST_CODE = 11
    }
}
