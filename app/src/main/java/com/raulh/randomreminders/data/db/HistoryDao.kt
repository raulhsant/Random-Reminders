package com.raulh.randomreminders.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Insert
    suspend fun insert(entry: ShotHistoryEntity)

    @Query("DELETE FROM shot_history WHERE ruleId = :ruleId")
    suspend fun deleteForRule(ruleId: Long)

    /** Disparos recentes com o nome da regra, mais recentes primeiro. */
    @Query(
        """
        SELECT h.id AS id, h.ruleId AS ruleId, h.firedAtMillis AS firedAtMillis, r.name AS ruleName
        FROM shot_history h LEFT JOIN rules r ON r.id = h.ruleId
        ORDER BY h.firedAtMillis DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int = 100): Flow<List<HistoryEntry>>
}

data class HistoryEntry(
    val id: Long,
    val ruleId: Long,
    val firedAtMillis: Long,
    val ruleName: String?,
)
