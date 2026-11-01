package com.raulh.randomreminders.domain.model

import java.time.Duration
import java.time.LocalTime

/**
 * Regra de criação de lembretes aleatórios.
 *
 * A validação de negócio centralizada vive aqui (fonte única): usada pelo
 * domínio, pelo agendador e pela UI — nada de duplicar mensagens de erro.
 *
 * @property start     início da janela diária de disparos (ex.: 07:00)
 * @property end       fim da janela diária (ex.: 22:00)
 * @property shotsPerRun       quantidade de disparos por ciclo (dia do ciclo)
 * @property minSpacingMinutes espaçamento mínimo entre disparos, configurável na tela
 * @property intervalDays      recorrência em dias; 0 = não repetir, 1 = diário
 */
data class ReminderRule(
    val start: LocalTime,
    val end: LocalTime,
    val shotsPerRun: Int,
    val minSpacingMinutes: Int,
    val intervalDays: Int = 1,
) {

    /**
     * Erros de regra de negócio (mensagem pronta para UI). Lista vazia = regra válida.
     */
    fun validationErrors(): List<String> = buildList {
        if (!start.isBefore(end)) add("O início deve ser antes do fim da janela.")
        if (shotsPerRun < 1) add("Deve haver ao menos 1 disparo por ciclo.")
        if (minSpacingMinutes < 1) add("O espaçamento mínimo deve ser de pelo menos 1 minuto.")
        if (intervalDays < 0) add("O intervalo não pode ser negativo.")

        if (start.isBefore(end) && shotsPerRun >= 1 && minSpacingMinutes >= 1) {
            val windowMinutes = Duration.between(start, end).toMinutes()
            val requiredMinutes = (shotsPerRun - 1).toLong() * minSpacingMinutes
            if (requiredMinutes > windowMinutes) {
                add(
                    "A janela de $windowMinutes min não comporta $shotsPerRun disparos " +
                        "com espaçamento mínimo de $minSpacingMinutes min (necessário: $requiredMinutes min).",
                )
            }
        }
    }
}
