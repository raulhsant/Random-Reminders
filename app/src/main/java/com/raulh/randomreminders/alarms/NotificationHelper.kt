package com.raulh.randomreminders.alarms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.raulh.randomreminders.R
import com.raulh.randomreminders.domain.NotificationMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publica a notificação de alarme disparada pelo [AlarmReceiver] e toca o som
 * no stream de alarme do Android por até 15 segundos.
 */
@Singleton
class NotificationHelper @Inject constructor(@ApplicationContext private val context: Context) {

    /**
     * Mostra o alarme e devolve o intent da tela para o receiver abrir.
     * O retorno independe da permissão de notificações: mesmo sem notificação,
     * o som e a tela do alarme devem funcionar.
     */
    fun showReminder(
        ruleId: Long,
        ruleName: String?,
        description: String?,
        soundUri: String?,
    ): Intent {
        val channelId = ensureChannel(ruleId, ruleName, soundUri)
        val title = ruleName?.takeIf(String::isNotBlank) ?: context.getString(R.string.app_name)
        val message = NotificationMessage.resolve(
            description,
            context.getString(R.string.reminder_notification_text),
        )
        val startedAt = System.currentTimeMillis()
        val screenIntent = alarmScreenIntent(ruleId, title, message, startedAt)

        val manager = NotificationManagerCompat.from(context)
        if (manager.areNotificationsEnabled()) {
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setWhen(startedAt)
                .setShowWhen(true)
                .setAutoCancel(false)
                .setOngoing(true)
                .setContentIntent(activityPendingIntent(screenIntent, ruleId))
                .setFullScreenIntent(activityPendingIntent(screenIntent, ruleId), true)
                .addAction(
                    0,
                    context.getString(R.string.action_dismiss),
                    actionIntent(ruleId, AlarmReceiver.ACTION_DISMISS_NOTIFICATION, DISMISS_REQUEST_CODE),
                )
                .build()
            runCatching { manager.notify(ruleId.hashCode(), notification) }
        }

        alarmSoundController.start(ruleId, soundUri)
        return screenIntent
    }

    fun dismissReminder(ruleId: Long) {
        alarmSoundController.stop(ruleId)
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

    private fun alarmScreenIntent(
        ruleId: Long,
        title: String,
        message: String,
        startedAt: Long,
    ): Intent = Intent(context, AlarmActivity::class.java)
        .putExtra(AlarmActivity.EXTRA_RULE_ID, ruleId)
        .putExtra(AlarmActivity.EXTRA_STARTED_AT, startedAt)
        .putExtra(AlarmActivity.EXTRA_TITLE, title)
        .putExtra(AlarmActivity.EXTRA_MESSAGE, message)

    private fun activityPendingIntent(intent: Intent, ruleId: Long): PendingIntent =
        PendingIntent.getActivity(
            context,
            ruleId.toInt(),
            intent,
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

    private fun createAlarmTone(soundUri: String?): AlarmTone? {
        val uri = soundUri.toSoundUri() ?: return null
        val ringTone = runCatching { RingtoneManager.getRingtone(context, uri) }.getOrNull() ?: return null
        ringTone.apply {
            // USAGE_ALARM roteia o som pelo volume de alarme e faz o Android
            // tratá-lo como alarme, não como toque de chamada.
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        }
        return object : AlarmTone {
            override var isLooping: Boolean
                get() = ringTone.isLooping
                set(value) {
                    ringTone.isLooping = value
                }

            override val isPlaying: Boolean
                get() = ringTone.isPlaying

            override fun play() {
                ringTone.play()
            }

            override fun stop() {
                ringTone.stop()
            }
        }
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
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
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
        const val DISMISS_REQUEST_CODE = 13
    }

    private val alarmSoundController = AlarmSoundController(
        supportsNativeLooping = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
        createTone = ::createAlarmTone,
        scheduler = AlarmSoundScheduler { delayMillis, action ->
            val runnable = Runnable(action)
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed(runnable, delayMillis)
            AlarmSoundCancellation { handler.removeCallbacks(runnable) }
        },
    )
}
