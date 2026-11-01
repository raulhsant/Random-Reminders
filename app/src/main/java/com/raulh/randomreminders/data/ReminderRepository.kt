package com.raulh.randomreminders.data

import androidx.room.withTransaction
import com.raulh.randomreminders.data.db.HistoryDao
import com.raulh.randomreminders.data.db.ReminderDatabase
import com.raulh.randomreminders.data.db.RuleDao
import com.raulh.randomreminders.data.db.RuleEntity
import com.raulh.randomreminders.data.db.ShotEntity
import com.raulh.randomreminders.data.db.ShotDao
import com.raulh.randomreminders.data.db.ShotHistoryEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Fachada de persistência (regras, pendências e histórico) sobre o Room.
 * O [com.raulh.randomreminders.alarms.AlarmScheduler] orquestra o sorteio;
 * aqui ficam apenas operações de dados.
 */
@Singleton
class ReminderRepository @Inject constructor(
    private val db: ReminderDatabase,
    private val ruleDao: RuleDao,
    private val shotDao: ShotDao,
    private val historyDao: HistoryDao,
) {

    // ---- Regras -------------------------------------------------------------

    fun observeRules(): Flow<List<RuleEntity>> = ruleDao.observeAll()

    suspend fun getRule(id: Long): RuleEntity? = ruleDao.getById(id)

    suspend fun upsertRule(rule: RuleEntity): Long = ruleDao.upsert(rule)

    suspend fun deleteRule(id: Long) {
        db.withTransaction {
            shotDao.deleteForRule(id)
            historyDao.deleteForRule(id) // regra apagada: histórico dela sai junto
            ruleDao.deleteById(id)
        }
    }

    suspend fun setActive(id: Long, active: Boolean) {
        ruleDao.getById(id)?.let { ruleDao.upsert(it.copy(active = active)) }
    }

    suspend fun setActivationBase(id: Long, epochDay: Long) {
        ruleDao.getById(id)?.let { ruleDao.upsert(it.copy(activationEpochDay = epochDay)) }
    }

    suspend fun activeRuleIds(): List<Long> = ruleDao.activeIds()

    suspend fun activeRules(): List<RuleEntity> = ruleDao.activeRules()

    // ---- Disparos pendentes ---------------------------------------------------

    suspend fun replacePending(ruleId: Long, triggerMillis: List<Long>) {
        db.withTransaction {
            shotDao.deleteForRule(ruleId)
            shotDao.insertAll(triggerMillis.map { ShotEntity(ruleId = ruleId, triggerAtMillis = it) })
        }
    }

    suspend fun dropPastPending(ruleId: Long, nowMillis: Long) {
        shotDao.deletePast(ruleId, nowMillis)
    }

    /** Remove todas as pendências da regra (ao desativar). */
    suspend fun clearPending(ruleId: Long) {
        shotDao.deleteForRule(ruleId)
    }

    suspend fun nextPendingMillis(ruleId: Long, nowMillis: Long): Long? =
        shotDao.nextPendingMillis(ruleId, nowMillis)

    suspend fun lastPendingMillis(ruleId: Long): Long? = shotDao.lastPendingMillis(ruleId)

    /**
     * Consome o disparo pendente mais cedo da regra (o que acabou de tocar),
     * registra no histórico e devolve o instante disparado.
     */
    suspend fun consumeOldestPending(ruleId: Long): Long? = db.withTransaction {
        val shot = shotDao.oldestPending(ruleId) ?: return@withTransaction null
        shotDao.deleteById(shot.id)
        historyDao.insert(
            ShotHistoryEntity(ruleId = ruleId, firedAtMillis = shot.triggerAtMillis),
        )
        shot.triggerAtMillis
    }

    /** Próximo disparo futuro por regra, para exibição na lista. */
    suspend fun nextShotByRule(nowMillis: Long): Map<Long, Long?> =
        shotDao.nextByRule(nowMillis).associate { it.ruleId to it.nextMillis }
}
