package com.raulh.randomreminders.di

import android.content.Context
import com.raulh.randomreminders.data.db.HistoryDao
import com.raulh.randomreminders.data.db.ReminderDatabase
import com.raulh.randomreminders.data.db.RuleDao
import com.raulh.randomreminders.data.db.ShotDao
import com.raulh.randomreminders.domain.RandomTimeGenerator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Injeções globais do app: `Clock` (injetável, facilita testes com hora fixa),
 * escopo de corrotinas de aplicação usado pelos receivers e o banco Room.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    @AppScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ReminderDatabase =
        ReminderDatabase.build(context)

    @Provides
    fun provideRuleDao(db: ReminderDatabase): RuleDao = db.ruleDao()

    @Provides
    fun provideShotDao(db: ReminderDatabase): ShotDao = db.shotDao()

    @Provides
    fun provideHistoryDao(db: ReminderDatabase): HistoryDao = db.historyDao()

    @Provides
    @Singleton
    fun provideRandomTimeGenerator(): RandomTimeGenerator = RandomTimeGenerator(Random.Default)
}
