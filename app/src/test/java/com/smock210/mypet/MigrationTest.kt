package com.smock210.mypet

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.smock210.mypet.sqlClass.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Миграционный тест БД (FIX_PLAN.md, 5.1).
 *
 * MigrationTestHelper требует exportSchema = true и JSON-схем в репозитории,
 * поэтому здесь используется самостоятельный подход: создаём БД схемы v4
 * через SupportSQLiteOpenHelper, прогоняем миграцию 4→5 и проверяем,
 * что данные сохранились, а колонка done появилась с дефолтом 0.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private lateinit var context: Context
    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB_NAME)

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DB_NAME)
            .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """CREATE TABLE PetData (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT,
                            species TEXT NOT NULL DEFAULT 'dog',
                            breed TEXT NOT NULL DEFAULT '',
                            ageYears INTEGER NOT NULL DEFAULT 0,
                            weightKg REAL NOT NULL DEFAULT 0.0,
                            smallDetail TEXT,
                            photoUri TEXT,
                            createdAt INTEGER NOT NULL
                        )"""
                    )
                    db.execSQL(
                        """CREATE TABLE Vaccination (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            petId INTEGER NOT NULL,
                            name TEXT NOT NULL DEFAULT '',
                            dateMillis INTEGER NOT NULL,
                            reminderEnabled INTEGER NOT NULL DEFAULT 1,
                            notes TEXT NOT NULL DEFAULT ''
                        )"""
                    )
                    db.execSQL(
                        """INSERT INTO PetData (name, species, breed, ageYears, weightKg, smallDetail, photoUri, createdAt)
                           VALUES ('Бобик', 'dog', 'Дворняга', 3, 12.5, 'Любит гулять', NULL, 1700000000000)"""
                    )
                    db.execSQL(
                        """INSERT INTO Vaccination (petId, name, dateMillis, reminderEnabled, notes)
                           VALUES (1, 'Бешенство', 1700000000000, 1, 'Первая прививка')"""
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        helper = FrameworkSQLiteOpenHelperFactory().create(config)
    }

    @After
    fun tearDown() {
        helper.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `migration 4 to 5 adds done column and preserves data`() {
        val db = helper.writableDatabase
        assertEquals(4, db.version)

        AppDatabase.MIGRATION_4_5.migrate(db)

        // Колонка появилась
        val columns = mutableListOf<String>()
        db.query("PRAGMA table_info(Vaccination)").use { c ->
            val idx = c.getColumnIndex("name")
            while (c.moveToNext()) columns.add(c.getString(idx))
        }
        assertTrue("Колонка done должна появиться после миграции", columns.contains("done"))

        // Старые данные на месте
        db.query("SELECT petId, name FROM Vaccination").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1L, c.getLong(0))
            assertEquals("Бешенство", c.getString(1))
            assertFalse("Должна остаться ровно одна запись", c.moveToNext())
        }

        // Новое поле читается как false (DEFAULT 0)
        db.query("SELECT done FROM Vaccination").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }

        // Данные питомца не пострадали
        db.query("SELECT name, ageYears, weightKg FROM PetData").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Бобик", c.getString(0))
            assertEquals(3, c.getInt(1))
            assertEquals(12.5, c.getDouble(2), 0.001)
        }
    }

    @Test
    fun `migration works on empty vaccination table`() {
        val db = helper.writableDatabase
        db.execSQL("DELETE FROM Vaccination")

        AppDatabase.MIGRATION_4_5.migrate(db)

        db.query("SELECT COUNT(*) FROM Vaccination").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0L, c.getLong(0))
        }
    }

    @Test
    fun `migration 5 to 6 creates WeightRecord table and keeps existing data`() {
        val db = helper.writableDatabase

        // 4→5 (добавляем done), затем 5→6 (таблица WeightRecord)
        AppDatabase.MIGRATION_4_5.migrate(db)
        AppDatabase.MIGRATION_5_6.migrate(db)

        val tables = mutableListOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type='table'").use { c ->
            val idx = c.getColumnIndex("name")
            while (c.moveToNext()) tables.add(c.getString(idx))
        }
        assertTrue("Таблица WeightRecord должна появиться", tables.contains("WeightRecord"))

        // Старые данные не пострадали
        db.query("SELECT name FROM PetData").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Бобик", c.getString(0))
        }

        // В таблицу можно писать и читать
        db.execSQL(
            """INSERT INTO WeightRecord (petId, weightKg, dateMillis)
               VALUES (1, 12.5, 1700000000000)"""
        )
        db.query("SELECT petId, weightKg FROM WeightRecord").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1L, c.getLong(0))
            assertEquals(12.5, c.getDouble(1), 0.001)
        }
    }

    companion object {
        private const val DB_NAME = "migration_test_database"
    }
}
