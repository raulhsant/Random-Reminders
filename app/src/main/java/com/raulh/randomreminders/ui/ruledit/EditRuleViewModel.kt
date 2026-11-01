package com.raulh.randomreminders.ui.ruledit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulh.randomreminders.alarms.AlarmScheduler
import com.raulh.randomreminders.alarms.NotificationHelper
import com.raulh.randomreminders.data.ReminderRepository
import com.raulh.randomreminders.data.db.RuleEntity
import com.raulh.randomreminders.domain.TimeText
import com.raulh.randomreminders.domain.model.ReminderRule
import com.raulh.randomreminders.ui.activate.ActivationField
import com.raulh.randomreminders.ui.activate.ActivationForm
import com.raulh.randomreminders.ui.activate.updated
import com.raulh.randomreminders.ui.activate.parseActivationForm
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado imutável do editor (novo ou existente). */
data class EditRuleUiState(
    val ruleId: Long? = null,
    val name: String = "",
    val form: ActivationForm = ActivationForm(),
    val fieldErrors: Set<ActivationField> = emptySet(),
    val ruleErrors: List<String> = emptyList(),
    val isValid: Boolean = false,
    val wasActive: Boolean = false,
    val isActive: Boolean = true,
    val repeats: Boolean = true,
    val soundUri: String? = null,
    val showNextTime: Boolean = false,
    val description: String = "",
)

private data class EditorOptions(
    val wasActive: Boolean,
    val isActive: Boolean,
    val repeats: Boolean,
    val soundUri: String?,
    val showNextTime: Boolean,
)

@HiltViewModel
class EditRuleViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: ReminderRepository,
    private val scheduler: AlarmScheduler,
    private val notifier: NotificationHelper,
) : ViewModel() {

    private val ruleId: Long = savedState.get<Long>("ruleId") ?: 0L

    private val name = MutableStateFlow("")
    private val form = MutableStateFlow(ActivationForm())
    private val wasActive = MutableStateFlow(false)
    private val isActive = MutableStateFlow(true)
    private val repeats = MutableStateFlow(true)
    private val soundUri = MutableStateFlow<String?>(null)
    private val showNextTime = MutableStateFlow(false)
    private val description = MutableStateFlow("")

    private val options = combine(wasActive, isActive, repeats, soundUri, showNextTime) {
            previouslyActive, active, repeatsValue, sound, showTime ->
        EditorOptions(previouslyActive, active, repeatsValue, sound, showTime)
    }

    val uiState: StateFlow<EditRuleUiState> =
        combine(name, form, options, description) { nameValue, formValue, editorOptions, descriptionValue ->
            val invalidName = nameValue.isBlank()
            val parsed = parseActivationForm(
                formValue.copy(intervalDays = if (editorOptions.repeats) formValue.intervalDays else "0"),
            )
            val ruleErrors = parsed.rule?.validationErrors().orEmpty()
            EditRuleUiState(
                ruleId = if (ruleId > 0) ruleId else null,
                name = nameValue,
                form = formValue,
                fieldErrors = if (invalidName) parsed.fieldErrors + ActivationField.NAME
                else parsed.fieldErrors,
                ruleErrors = ruleErrors,
                isValid = !invalidName && parsed.rule != null && ruleErrors.isEmpty(),
                wasActive = editorOptions.wasActive,
                isActive = editorOptions.isActive,
                repeats = editorOptions.repeats,
                soundUri = editorOptions.soundUri,
                showNextTime = editorOptions.showNextTime,
                description = descriptionValue,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditRuleUiState())

    init {
        viewModelScope.launch {
            repository.getRule(ruleId)?.let { entity ->
                name.update { entity.name }
                form.update {
                    ActivationForm(
                        start = minutesToText(entity.startMinutes),
                        end = minutesToText(entity.endMinutes),
                        shots = entity.shotsPerRun.toString(),
                        spacing = entity.minSpacingMinutes.toString(),
                        intervalDays = entity.intervalDays.coerceAtLeast(1).toString(),
                    )
                }
                wasActive.update { entity.active }
                isActive.update { entity.active }
                repeats.update { entity.intervalDays > 0 }
                soundUri.update { entity.soundUri }
                showNextTime.update { entity.showNextTime }
                description.update { entity.description }
            }
        }
    }

    fun onNameChange(value: String) = name.update { value }

    fun onFieldChange(field: ActivationField, value: String) = form.update { it.updated(field, value) }

    fun onActiveChange(value: Boolean) = isActive.update { value }

    fun onRepeatsChange(value: Boolean) = repeats.update { value }

    fun onSoundChange(value: String?) = soundUri.update { value }

    fun onShowNextTimeChange(value: Boolean) = showNextTime.update { value }

    fun onDescriptionChange(value: String) = description.update { value }

    /**
     * Salva a regra. Se ela estava ativa, o ciclo é refeito (re-sorteio do dia)
     * para os novos parâmetros entrar em vigor de imediato.
     */
    fun save(onDone: () -> Unit) {
        val state = uiState.value
        if (!state.isValid) return
        val effectiveForm = state.form.copy(
            intervalDays = if (state.repeats) state.form.intervalDays else "0",
        )
        val rule = parseActivationForm(effectiveForm).rule ?: return
        viewModelScope.launch {
            val entityIdBefore = state.ruleId ?: 0L
            val previous = state.ruleId?.let { repository.getRule(it) }
            val entity = toEntity(state, rule)
            val entityId = repository.upsertRule(entity)
            val effectiveId = if (entityIdBefore > 0) entityIdBefore else entityId
            notifier.syncSoundChannel(effectiveId, state.name.trim(), state.soundUri)
            if (state.isActive && (previous == null || scheduleChanged(previous, entity))) {
                scheduler.cancelAlarm(effectiveId)
                repository.clearPending(effectiveId)
                scheduler.activateRule(entity.copy(id = effectiveId))
            } else if (!state.isActive && previous?.active == true) {
                scheduler.deactivateRule(effectiveId)
            }
            onDone()
        }
    }

    /** Exclui a regra (somente modo edição). */
    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            scheduler.deactivateRule(ruleId)
            notifier.deleteChannels(ruleId)
            repository.deleteRule(ruleId)
            onDone()
        }
    }

    private fun toEntity(state: EditRuleUiState, rule: ReminderRule): RuleEntity {
        // Ao reativar uma regra editada o ciclo é refeito de qualquer forma;
        // activationEpochDay é sobrescrito por scheduler.activateRule.
        return RuleEntity(
            id = state.ruleId ?: 0L,
            name = state.name.trim(),
            startMinutes = rule.start.hour * 60 + rule.start.minute,
            endMinutes = rule.end.hour * 60 + rule.end.minute,
            shotsPerRun = rule.shotsPerRun,
            minSpacingMinutes = rule.minSpacingMinutes,
            intervalDays = rule.intervalDays,
            active = state.isActive,
            activationEpochDay = 0,
            soundUri = state.soundUri,
            showNextTime = state.showNextTime,
            description = state.description.trim(),
        )
    }

    private fun scheduleChanged(previous: RuleEntity, updated: RuleEntity): Boolean =
        !previous.active ||
            previous.startMinutes != updated.startMinutes ||
            previous.endMinutes != updated.endMinutes ||
            previous.shotsPerRun != updated.shotsPerRun ||
            previous.minSpacingMinutes != updated.minSpacingMinutes ||
            previous.intervalDays != updated.intervalDays

    private fun minutesToText(minutes: Int): String =
        "%02d:%02d".format(minutes / 60, minutes % 60)
}
