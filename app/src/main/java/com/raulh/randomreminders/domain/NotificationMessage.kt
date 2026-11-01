package com.raulh.randomreminders.domain

object NotificationMessage {

    fun resolve(description: String?, fallback: String): String =
        description?.trim()?.takeIf { it.isNotEmpty() } ?: fallback
}