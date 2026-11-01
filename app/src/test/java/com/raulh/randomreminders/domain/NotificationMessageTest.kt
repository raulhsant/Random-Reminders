package com.raulh.randomreminders.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationMessageTest {

    @Test
    fun `uses trimmed description as notification body`() {
        assertEquals("Drink water", NotificationMessage.resolve("  Drink water  ", FALLBACK))
    }

    @Test
    fun `uses fallback when description is blank`() {
        assertEquals(FALLBACK, NotificationMessage.resolve("  ", FALLBACK))
    }

    private companion object {
        const val FALLBACK = "Hora do seu lembrete"
    }
}