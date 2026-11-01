package com.raulh.randomreminders.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ShotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(shots: List<ShotEntity>)

    /** Instante mais cedo ainda pendente da regra (null se não houver). */
    @Query("SELECT MIN(triggerAtMillis) FROM pending_shots WHERE ruleId = :ruleId AND triggerAtMillis > :nowMillis")
    suspend fun nextPendingMillis(ruleId: Long, nowMillis: Long): Long?

    /** Descarta disparos que ficaram para trás (reboot, aparelho desligado). */
    @Query("DELETE FROM pending_shots WHERE ruleId = :ruleId AND triggerAtMillis <= :nowMillis")
    suspend fun deletePast(ruleId: Long, nowMillis: Long)

    /** Disparo pendente mais cedo da regra (independente do instante). */
    @Query("SELECT * FROM pending_shots WHERE ruleId = :ruleId ORDER BY triggerAtMillis LIMIT 1")
    suspend fun oldestPending(ruleId: Long): ShotEntity?

    @Query("DELETE FROM pending_shots WHERE id = :shotId")
    suspend fun deleteById(shotId: Long)

    @Query("DELETE FROM pending_shots WHERE ruleId = :ruleId")
    suspend fun deleteForRule(ruleId: Long)

    /** Instante mais tardio entre pendências da regra (base p/ próximo ciclo). */
    @Query("SELECT MAX(triggerAtMillis) FROM pending_shots WHERE ruleId = :ruleId")
    suspend fun lastPendingMillis(ruleId: Long): Long?

    /** Próximo disparo futuro por regra (para exibir na lista). */
    @Query("SELECT ruleId, MIN(triggerAtMillis) AS nextMillis FROM pending_shots WHERE triggerAtMillis > :nowMillis GROUP BY ruleId")
    suspend fun nextByRule(nowMillis: Long): List<NextShotRow>
}

data class NextShotRow(val ruleId: Long, val nextMillis: Long?)
