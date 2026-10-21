package com.smock210.mypet.sqlClass

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PetData::class, Vaccination::class, VetVisit::class, PetPhoto::class, WeightRecord::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun employeeDao(): EmployeeDao
    abstract fun healthDao(): HealthDao
    abstract fun petPhotoDao(): PetPhotoDao
    abstract fun weightDao(): WeightDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // internal — для миграционного теста (FIX_PLAN.md, 5.1)
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS Vaccination (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        petId INTEGER NOT NULL,
                        name TEXT NOT NULL DEFAULT '',
                        dateMillis INTEGER NOT NULL,
                        reminderEnabled INTEGER NOT NULL DEFAULT 1,
                        notes TEXT NOT NULL DEFAULT ''
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS VetVisit (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        petId INTEGER NOT NULL,
                        clinicName TEXT NOT NULL DEFAULT '',
                        doctorName TEXT NOT NULL DEFAULT '',
                        dateMillis INTEGER NOT NULL,
                        notes TEXT NOT NULL DEFAULT '',
                        diagnosis TEXT NOT NULL DEFAULT '',
                        calendarEventId INTEGER NOT NULL DEFAULT 0
                    )"""
                )
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS PetPhoto (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        petId INTEGER NOT NULL,
                        photoUri TEXT NOT NULL,
                        isPrimary INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)
                    )"""
                )
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE PetData ADD COLUMN species TEXT NOT NULL DEFAULT 'dog'")
                db.execSQL("ALTER TABLE PetData ADD COLUMN breed TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE PetData ADD COLUMN ageYears INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE PetData ADD COLUMN weightKg REAL NOT NULL DEFAULT 0.0")
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE Vaccination ADD COLUMN done INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS WeightRecord (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        petId INTEGER NOT NULL,
                        weightKg REAL NOT NULL,
                        dateMillis INTEGER NOT NULL
                    )"""
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pet_database"
                ).addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6
                )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}