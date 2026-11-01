package com.raulh.randomreminders.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {

    @Query("SELECT * FROM rules ORDER BY active DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun getById(id: Long): RuleEntity?

    @Upsert
    suspend fun upsert(rule: RuleEntity): Long

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT id FROM rules WHERE active = 1")
    suspend fun activeIds(): List<Long>

    @Query("SELECT * FROM rules WHERE active = 1")
    suspend fun activeRules(): List<RuleEntity>
}
