package com.raulh.randomreminders.alarms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSoundControllerTest {

    @Test
    fun `modern Android starts a single looping tone and stops it after 15 seconds`() {
        val scheduler = TestAlarmSoundScheduler()
        val tone = FakeAlarmTone()
        val controller = AlarmSoundController(
            supportsNativeLooping = true,
            createTone = { tone },
            scheduler = scheduler,
        )

        controller.start(ruleId = 1L, soundUri = null)

        assertTrue(tone.isLooping)
        assertEquals(1, tone.playCount)

        scheduler.advanceBy(3_000L)

        assertEquals(1, tone.playCount)
        assertEquals(0, tone.stopCount)

        scheduler.advanceBy(12_000L)

        assertEquals(1, tone.playCount)
        assertEquals(1, tone.stopCount)
        assertFalse(tone.isPlaying)
    }

    private class FakeAlarmTone : AlarmTone {
        override var isLooping = false
        override var isPlaying = false
            private set
        var playCount = 0
            private set
        var stopCount = 0
            private set

        override fun play() {
            playCount++
            isPlaying = true
        }

        override fun stop() {
            stopCount++
            isPlaying = false
        }
    }

    private class TestAlarmSoundScheduler : AlarmSoundScheduler {
        private data class ScheduledAction(
            val dueAtMillis: Long,
            val action: () -> Unit,
            var cancelled: Boolean = false,
        )

        private val actions = mutableListOf<ScheduledAction>()
        private var currentTimeMillis = 0L

        override fun schedule(delayMillis: Long, action: () -> Unit): AlarmSoundCancellation {
            val scheduled = ScheduledAction(currentTimeMillis + delayMillis, action)
            actions += scheduled
            return AlarmSoundCancellation { scheduled.cancelled = true }
        }

        fun advanceBy(durationMillis: Long) {
            val targetTimeMillis = currentTimeMillis + durationMillis
            while (true) {
                val next = actions
                    .filter { !it.cancelled && it.dueAtMillis <= targetTimeMillis }
                    .minByOrNull { it.dueAtMillis }
                    ?: break
                actions.remove(next)
                currentTimeMillis = next.dueAtMillis
                next.action()
            }
            currentTimeMillis = targetTimeMillis
        }
    }
}
