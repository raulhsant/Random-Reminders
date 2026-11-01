package com.raulh.randomreminders.ui.rulelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulh.randomreminders.alarms.AlarmScheduler
import com.raulh.randomreminders.alarms.NotificationHelper
import com.raulh.randomreminders.data.ReminderRepository
import com.raulh.randomreminders.data.db.RuleEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Estado imutável da lista de lembretes. */
data class RuleListUiState(
    val rules: List<RuleItem> = emptyList(),
    val isLoading: Boolean = true,
)

/** Item pronto para exibição: regra + texto do próximo disparo. */
data class RuleItem(
    val rule: RuleEntity,
    val summary: String,
    val nextShotText: String?,
)

@HiltViewModel
class RuleListViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val scheduler: AlarmScheduler,
    private val notifier: NotificationHelper,
) : ViewModel() {

    /** Tick de atualização: o próximo disparo muda mesmo sem edição de dados. */
    private val ticker = flow<Unit> {
        while (true) {
            emit(Unit)
            delay(TICK_MILLIS)
        }
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<RuleListUiState> =
        combine(repository.observeRules(), ticker) { rules, _ -> rules }
            .map { rules ->
                val nextShots = repository.nextShotByRule(System.currentTimeMillis())
                RuleListUiState(
                    rules = rules.map { rule ->
                        RuleItem(
                            rule = rule,
                            summary = summarize(rule),
                            nextShotText = if (rule.showNextTime) {
                                nextShots[rule.id]?.let(::formatNextShot)
                            } else {
                                null
                            },
                        )
                    },
                    isLoading = false,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RuleListUiState())

    /** Liga ou desliga o ciclo da regra (sorteia/agenda ou cancela). */
    fun setEnabled(ruleId: Long, enabled: Boolean) {
        viewModelScope.launch {
            val rule = repository.getRule(ruleId) ?: return@launch
            if (enabled) scheduler.activateRule(rule) else scheduler.deactivateRule(ruleId)
        }
    }

    fun deleteRule(ruleId: Long) {
        viewModelScope.launch {
            scheduler.deactivateRule(ruleId)
            notifier.deleteChannels(ruleId)
            repository.deleteRule(ruleId)
        }
    }

    private fun summarize(rule: RuleEntity): String {
        val start = "%02d:%02d".format(rule.startMinutes / 60, rule.startMinutes % 60)
        val end = "%02d:%02d".format(rule.endMinutes / 60, rule.endMinutes % 60)
        val cycle = when {
            rule.intervalDays == 0 -> "uma vez"
            rule.intervalDays == 1 -> "todos os dias"
            else -> "a cada ${rule.intervalDays} dias"
        }
        return "$start–$end · ${rule.shotsPerRun}× · espaçamento mínimo ${rule.minSpacingMinutes} min · $cycle"
    }

    private companion object {
        const val TICK_MILLIS = 30_000L
        val NEXT_SHOT_FORMATTER = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm")

        fun formatNextShot(millis: Long): String =
            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(NEXT_SHOT_FORMATTER)
    }
}
