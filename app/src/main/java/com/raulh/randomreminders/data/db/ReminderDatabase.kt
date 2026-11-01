package com.raulh.randomreminders.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RuleEntity::class, ShotEntity::class, ShotHistoryEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class ReminderDatabase : RoomDatabase() {

    abstract fun ruleDao(): RuleDao
    abstract fun shotDao(): ShotDao
    abstract fun historyDao(): HistoryDao

    companion object {
        const val NAME = "random_reminders.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rules ADD COLUMN soundUri TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE rules ADD COLUMN showNextTime INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rules ADD COLUMN description TEXT NOT NULL DEFAULT ''")
            }
        }

        fun build(context: Context): ReminderDatabase =
            Room.databaseBuilder(context, ReminderDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .build()
    }
}
