package com.raulh.randomreminders.ui.activate

import com.raulh.randomreminders.domain.TimeText
import com.raulh.randomreminders.domain.model.ReminderRule

/** Campos do formulário de acionamento (strings cruas, editáveis pelo usuário). */
data class ActivationForm(
    val start: String = "07:00",
    val end: String = "22:00",
    val shots: String = "4",
    val spacing: String = "60",
    val intervalDays: String = "1",
)

enum class ActivationField { NAME, START, END, SHOTS, SPACING, INTERVAL }

/** Resultado do parse do formulário: regra (quando parseável) + erros por campo. */
data class ParsedActivation(val rule: ReminderRule?, val fieldErrors: Set<ActivationField>)

/**
 * Parse + validação de entrada (fonte única, usada pela UI e pelo ViewModel).
 * Erros de regra de negócio vêm de [ReminderRule.validationErrors] — sem duplicação.
 */
fun parseActivationForm(form: ActivationForm): ParsedActivation {
    val fieldErrors = mutableSetOf<ActivationField>()

    val start = TimeText.parseOrNull(form.start)
    val end = TimeText.parseOrNull(form.end)
    val shots = form.shots.trim().toIntOrNull()
    val spacing = form.spacing.trim().toIntOrNull()
    val interval = form.intervalDays.trim().toIntOrNull()

    if (start == null) fieldErrors += ActivationField.START
    if (end == null) fieldErrors += ActivationField.END
    if (shots == null || shots < 1) fieldErrors += ActivationField.SHOTS
    if (spacing == null || spacing < 1) fieldErrors += ActivationField.SPACING
    if (interval == null || interval < 0) fieldErrors += ActivationField.INTERVAL

    val rule = if (fieldErrors.isEmpty()) {
        ReminderRule(
            start = start!!,
            end = end!!,
            shotsPerRun = shots!!,
            minSpacingMinutes = spacing!!,
            intervalDays = interval!!,
        )
    } else {
        null
    }
    return ParsedActivation(rule, fieldErrors)
}

/** Aplica a alteração de um campo, imutavelmente. */
fun ActivationForm.updated(field: ActivationField, value: String): ActivationForm = when (field) {
    ActivationField.NAME -> this // nome não passa por aqui (ver onNameChange)
    ActivationField.START -> copy(start = value)
    ActivationField.END -> copy(end = value)
    ActivationField.SHOTS -> copy(shots = value)
    ActivationField.SPACING -> copy(spacing = value)
    ActivationField.INTERVAL -> copy(intervalDays = value)
}
