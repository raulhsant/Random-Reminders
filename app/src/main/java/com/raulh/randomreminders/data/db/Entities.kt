package com.raulh.randomreminders.data.db

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

/**
 * Lembrete configurado pelo usuário ("mesa", "beber água", …).
 * `startMinutes`/`endMinutes` = minuto do dia da janela; `active` liga o ciclo
 * ao [com.raulh.randomreminders.alarms.AlarmScheduler].
 */
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val shotsPerRun: Int,
    val minSpacingMinutes: Int,
    val intervalDays: Int,
    val active: Boolean,
    /** Dia-base da recorrência "a cada N dias" (dia da ativação). */
    val activationEpochDay: Long = 0,
    /** null = som padrão; "silent" = silencioso; demais valores são URIs de toque. */
    val soundUri: String? = null,
    @ColumnInfo(defaultValue = "0") val showNextTime: Boolean = false,
    @ColumnInfo(defaultValue = "''") val description: String = "",
)

/** Disparo sorteado que ainda vai tocar (ou já passou e será descartado). */
@Entity(tableName = "pending_shots")
data class ShotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    val triggerAtMillis: Long,
)

/** Registro de disparo efetivado (alimentará a tela de histórico). */
@Entity(tableName = "shot_history")
data class ShotHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    val firedAtMillis: Long,
)
