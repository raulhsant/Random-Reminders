package com.raulh.randomreminders.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.raulh.randomreminders.data.ReminderRepository
import com.raulh.randomreminders.data.db.RuleEntity
import com.raulh.randomreminders.domain.RandomTimeGenerator
import com.raulh.randomreminders.domain.model.ReminderRule
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordena o ciclo de vida dos alarmes aleatórios para **múltiplas regras**.
 *
 * Um [PendingIntent] por regra (`requestCode = ruleId`); os disparos pendentes
 * do ciclo ficam no Room ([com.raulh.randomreminders.data.ReminderRepository]).
 *
 * Fluxo por regra: agenda APENAS o próximo instante; ao disparar, o
 * [AlarmReceiver] consome a pendência (registrando no histórico) e chama
 * [scheduleRule], que sorteia o seguinte dentro da janela (ou no próximo dia
 * do ciclo permitido, "a cada N dias").
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ReminderRepository,
    private val generator: RandomTimeGenerator,
    private val clock: Clock,
) {

    /**
     * Ativa (ou reativa) uma regra: fixa o dia-base da recorrência, sorteia
     * todos os disparos do ciclo do dia, substitui as pendências e agenda.
     */
    suspend fun activateRule(rule: RuleEntity) {
        val activationDate = LocalDate.now(clock)
        val zone = clock.zone
        val shots = generator.generateShots(rule.toDomainRule(), activationDate)
        repository.setActivationBase(rule.id, activationDate.toEpochDay())
        repository.replacePending(
            rule.id,
            shots.map { it.atZone(zone).toInstant().toEpochMilli() },
        )
        repository.setActive(rule.id, true)
        scheduleRule(rule.id)
    }

    /** Cancela o alarme corrente da regra, limpa pendências e desativa. */
    suspend fun deactivateRule(ruleId: Long) {
        cancelAlarm(ruleId)
        repository.clearPending(ruleId)
        repository.setActive(ruleId, false)
    }

    /** Chamado pelo [AlarmReceiver] depois de cada disparo efetivado. */
    suspend fun onAlarmFired(ruleId: Long) {
        val rule = repository.getRule(ruleId) ?: return
        if (!rule.active) return
        scheduleRule(ruleId)
    }

    /** Reagenda TODAS as regras ativas (boot, mudança de relógio/fuso). */
    suspend fun scheduleAll() {
        repository.activeRuleIds().forEach { scheduleRule(it) }
    }

    /**
     * Agenda o próximo disparo futuro da regra:
     * 1. descarta disparos que ficaram no passado (ex.: reboot);
     * 2. sem pendências futuras, sorteia o dia seguinte permitido ("a cada N dias");
     * 3. agenda o instante mais cedo restante.
     */
    suspend fun scheduleRule(ruleId: Long) {
        val entity = repository.getRule(ruleId) ?: return
        if (!entity.active) return
        val zone = clock.zone
        val nowMillis = ZonedDateTime.now(clock).toInstant().toEpochMilli()
        val domain = entity.toDomainRule()

        repository.dropPastPending(ruleId, nowMillis)
        var due = repository.nextPendingMillis(ruleId, nowMillis)

        if (due == null) {
            if (domain.intervalDays == 0) {
                deactivateRule(ruleId)
                return
            }
            // Base do próximo ciclo: último disparo do ciclo anterior +1 dia.
            val lastPending = repository.lastPendingMillis(ruleId)
            val baseDate = lastPending
                ?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
                ?.plusDays(1)
                ?: LocalDate.now(clock).plusDays(1)
            val nextCycleDate = generator.nextAllowedDate(
                rule = domain,
                activationDate = LocalDate.ofEpochDay(entity.activationEpochDay),
                from = baseDate,
            )
            val nextCycleShots = generator.generateShots(domain, nextCycleDate)
            repository.replacePending(
                ruleId,
                nextCycleShots.map { it.atZone(zone).toInstant().toEpochMilli() },
            )
            due = repository.nextPendingMillis(ruleId, nowMillis) ?: return
        }

        schedule(ruleId, due)
    }

    /** Cancela o alarme pendente atualmente registrado da regra (se houver). */
    fun cancelAlarm(ruleId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_ALARM_FIRED)
        PendingIntent.getBroadcast(
            context,
            ruleId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.cancel()
    }

    private fun schedule(ruleId: Long, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ruleId.toInt(),
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_ALARM_FIRED)
                .putExtra(AlarmReceiver.EXTRA_RULE_ID, ruleId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Alarme exato quando permitido; fallback inexato (aprox. dentro da janela)
        // mantém o produto funcional sem a permissão especial.
        val canScheduleExact =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun RuleEntity.toDomainRule(): ReminderRule = ReminderRule(
        start = LocalTime.of(startMinutes / 60, startMinutes % 60),
        end = LocalTime.of(endMinutes / 60, endMinutes % 60),
        shotsPerRun = shotsPerRun,
        minSpacingMinutes = minSpacingMinutes,
        intervalDays = intervalDays,
    )

}
