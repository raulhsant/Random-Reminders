package com.raulh.randomreminders.domain

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Utilitários de texto <-> hora (input da tela -> modelo de domínio).
 * Fonte única para parse e formatação "HH:mm" (aceita "7:05").
 */
object TimeText {

    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

    /** Converte "HH:mm" (ou "H:mm") em [LocalTime]; null quando inválido. */
    fun parseOrNull(value: String): LocalTime? =
        runCatching { LocalTime.parse(value.trim(), formatter) }.getOrNull()

    /** Formata uma hora para exibição estável no padrão de 24 horas. */
    fun format(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)
}
