package com.raulh.randomreminders.alarms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.media.RingtoneManager
import android.os.Build
import android.net.Uri
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.media.Ringtone
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
        val notificationId = ruleId.hashCode()

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
                .setAutoCancel(false)
                .setFullScreenIntent(contentIntent(), true)
                .addAction(
                    0,
                    context.getString(R.string.action_snooze),
                    actionIntent(ruleId, AlarmReceiver.ACTION_SNOOZE_NOTIFICATION, SNOOZE_REQUEST_CODE),
                )
                .addAction(
                    0,
                    context.getString(R.string.action_dismiss),
                    actionIntent(ruleId, AlarmReceiver.ACTION_DISMISS_NOTIFICATION, DISMISS_REQUEST_CODE),
                )
                .build()
                .also { manager.notify(notificationId, it) }
        }
        startAlarmSound(ruleId, soundUri)
        timeoutRunnables[ruleId]?.let { timeoutHandler.removeCallbacks(it) }
        val timeoutRunnable = Runnable { dismissReminder(ruleId) }
        timeoutRunnables[ruleId] = timeoutRunnable
        timeoutHandler.postDelayed(timeoutRunnable, ALARM_DURATION_MILLIS)
    }

    fun dismissReminder(ruleId: Long) {
        timeoutRunnables.remove(ruleId)?.let { timeoutHandler.removeCallbacks(it) }
        stopAlarmSound(ruleId)
        NotificationManagerCompat.from(context).cancel(ruleId.hashCode())
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

    private fun actionIntent(ruleId: Long, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode * 31 + ruleId.toInt(),
            Intent(context, AlarmReceiver::class.java)
                .setAction(action)
                .putExtra(AlarmReceiver.EXTRA_RULE_ID, ruleId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun startAlarmSound(ruleId: Long, soundUri: String?) {
        stopAlarmSound(ruleId)
        val uri = soundUri.toSoundUri() ?: return
        val ringTone = runCatching { RingtoneManager.getRingtone(context, uri) }.getOrNull()
        ringTones[ruleId] = ringTone ?: return
        ringTone.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                isLooping = true
            }
        }.play()
    }

    private fun stopAlarmSound(ruleId: Long) {
        ringTones.remove(ruleId)?.stop()
    }

    private fun ensureChannel(ruleId: Long, ruleName: String?, soundUri: String?): String {
        val channelId = channelId(ruleId, soundUri)
        val channel = NotificationChannel(
            channelId,
            ruleName?.takeIf(String::isNotBlank) ?: context.getString(R.string.channel_reminders_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_reminders_description)
            enableVibration(true)
            setSound(null, null)
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
        const val SNOOZE_REQUEST_CODE = 12
        const val DISMISS_REQUEST_CODE = 13
        const val ALARM_DURATION_MILLIS = 15_000L
    }

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnables = mutableMapOf<Long, Runnable>()
    private val ringTones = mutableMapOf<Long, Ringtone>()
}
