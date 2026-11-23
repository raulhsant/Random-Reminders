package com.raulh.randomreminders.alarms

internal interface AlarmTone {
    var isLooping: Boolean
    val isPlaying: Boolean
    fun play()
    fun stop()
}

internal fun interface AlarmSoundCancellation {
    fun cancel()
}

internal fun interface AlarmSoundScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): AlarmSoundCancellation
}

internal class AlarmSoundController(
    private val supportsNativeLooping: Boolean,
    private val createTone: (String?) -> AlarmTone?,
    private val scheduler: AlarmSoundScheduler,
) {
    private val tones = mutableMapOf<Long, AlarmTone>()
    private val stopCancellations = mutableMapOf<Long, AlarmSoundCancellation>()
    private val restartCancellations = mutableMapOf<Long, AlarmSoundCancellation>()

    @Synchronized
    fun start(ruleId: Long, soundUri: String?) {
        stop(ruleId)
        val tone = createTone(soundUri) ?: return
        tone.isLooping = supportsNativeLooping
        tones[ruleId] = tone
        tone.play()
        stopCancellations[ruleId] = scheduler.schedule(ALARM_SOUND_DURATION_MILLIS) {
            stopIfCurrent(ruleId, tone)
        }
        if (!supportsNativeLooping) {
            scheduleLegacyRestart(ruleId, tone)
        }
    }

    @Synchronized
    fun stop(ruleId: Long) {
        stopCancellations.remove(ruleId)?.cancel()
        restartCancellations.remove(ruleId)?.cancel()
        tones.remove(ruleId)?.stop()
    }

    @Synchronized
    private fun stopIfCurrent(ruleId: Long, tone: AlarmTone) {
        if (tones[ruleId] === tone) {
            stop(ruleId)
        }
    }

    private fun scheduleLegacyRestart(ruleId: Long, tone: AlarmTone) {
        restartCancellations[ruleId] = scheduler.schedule(RINGTONE_RESTART_INTERVAL_MILLIS) {
            synchronized(this) {
                if (tones[ruleId] !== tone) return@schedule
                if (!tone.isPlaying) tone.play()
                scheduleLegacyRestart(ruleId, tone)
            }
        }
    }

    private companion object {
        const val ALARM_SOUND_DURATION_MILLIS = 15_000L
        const val RINGTONE_RESTART_INTERVAL_MILLIS = 1_000L
    }
}
