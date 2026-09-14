package com.example.workhourstracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DailyEntry::class, WeekLog::class], version = 3, exportSchema = false)
abstract class WorkHoursDatabase : RoomDatabase() {

    abstract fun workHoursDao(): WorkHoursDao

    companion object {
        @Volatile
        private var INSTANCE: WorkHoursDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_entries ADD COLUMN clockInMinutes INTEGER")
                db.execSQL("ALTER TABLE daily_entries ADD COLUMN clockOutMinutes INTEGER")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_entries ADD COLUMN lunchOutMinutes INTEGER")
                db.execSQL("ALTER TABLE daily_entries ADD COLUMN lunchInMinutes INTEGER")
            }
        }

        fun getInstance(context: Context): WorkHoursDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WorkHoursDatabase::class.java,
                    "work_hours.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
    }
}
