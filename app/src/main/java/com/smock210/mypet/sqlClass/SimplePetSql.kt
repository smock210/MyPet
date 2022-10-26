package com.smock210.mypet.sqlClass

import androidx.room.*


class SimplePetSql {
    @Entity
    public class PetData{
        @PrimaryKey
        var id: Long = 0
        var name: String? = null
        var smallDetail: String? = null

    }


    @Dao
    interface EmployeeDao {
        @get:Query("SELECT * FROM PetData")
        val all: List<PetData>?

        @Query("SELECT * FROM PetData WHERE id = :id")
        fun getById(id: Long): PetData?

        @Insert
        fun insert(employee: PetData?)

        @Update
        fun update(employee: PetData?)

        @Delete
        fun delete(employee: PetData?)
    }


    @Database(entities = [PetData::class], version = 1)
    abstract class AppDatabase : RoomDatabase() {
        abstract fun employeeDao(): EmployeeDao?
    }
}