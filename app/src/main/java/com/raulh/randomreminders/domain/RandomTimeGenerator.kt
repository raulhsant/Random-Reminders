package com.raulh.randomreminders.domain

import com.raulh.randomreminders.domain.model.ReminderRule
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/**
 * Geração de horários aleatórios de disparo.
 *
 * Estratégia: dentro da janela diária, cada disparo tem um slot-base
 * (`i * espaçamentoMínimo`) somado a uma folga aleatória. As folgas são
 * ordenadas antes da distribuição, o que garante matematicamente:
 *
 * - todos os disparos dentro de [start, end];
 * - espaçamento entre disparos vizinhos sempre >= [ReminderRule.minSpacingMinutes];
 * - boa pseudo-aleatoriedade nos instantes.
 *
 * Classe pura (sem Android), facilmente testável — nos testes passa-se um
 * `Random` com semente fixa; no app, o Hilt fornece via [com.raulh.randomreminders.di.AppModule].
 */
class RandomTimeGenerator(private val random: Random) {

    /**
     * Gera os disparos do dia [forDate], em ordem crescente.
     * Exige uma regra válida (ver [ReminderRule.validationErrors]).
     */
    fun generateShots(rule: ReminderRule, forDate: LocalDate): List<LocalDateTime> {
        val errors = rule.validationErrors()
        require(errors.isEmpty()) { "Regra inválida: $errors" }

        val windowStart = rule.start.atDate(forDate)
        val windowEnd = rule.end.atDate(forDate)
        val windowMinutes = Duration.between(windowStart, windowEnd).toMinutes()
        val minSpacing = rule.minSpacingMinutes.toLong()
        val freeMinutes = windowMinutes - (rule.shotsPerRun - 1L) * minSpacing

        val randomFreeOffsets = List(rule.shotsPerRun) { random.nextLong(freeMinutes + 1) }.sorted()
        return randomFreeOffsets.mapIndexed { index, free ->
            windowStart.plusMinutes(index * minSpacing + free)
        }
    }

    /** Primeiro dia do ciclo permitido na série "a cada N dias" a partir da ativação. */
    fun nextAllowedDate(rule: ReminderRule, activationDate: LocalDate, from: LocalDate): LocalDate {
        require(rule.intervalDays > 0) { "Uma regra sem repetição não tem próximo ciclo." }
        val delta = ChronoUnit.DAYS.between(activationDate, from)
        val remainder = Math.floorMod(delta, rule.intervalDays.toLong())
        return if (remainder == 0L) from else from.plusDays(rule.intervalDays - remainder)
    }

    /**
     * Próximo disparo depois de [from], respeitando a janela diária e a
     * recorrência "a cada N dias". Retorna null somente se 400 dias forem
     * varridos sem resultado (guarda de segurança; não deve ocorrer).
     */
    fun nextOccurrence(
        rule: ReminderRule,
        activationDate: LocalDate,
        from: ZonedDateTime,
    ): ZonedDateTime? {
        if (rule.intervalDays == 0) return null
        var candidateDate = nextAllowedDate(rule, activationDate, from.toLocalDate())
        repeat(MAX_DAYS_SCAN) {
            generateShots(rule, candidateDate)
                .asSequence()
                .map { it.atZone(from.zone) }
                .firstOrNull { it.isAfter(from) }
                ?.let { return it }
            candidateDate = nextAllowedDate(rule, activationDate, candidateDate.plusDays(1))
        }
        return null
    }

    private companion object {
        const val MAX_DAYS_SCAN = 400
    }
}
