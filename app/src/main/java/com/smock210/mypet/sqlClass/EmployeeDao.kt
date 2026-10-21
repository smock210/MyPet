package com.smock210.mypet.sqlClass

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EmployeeDao {
    @Query("SELECT * FROM PetData")
    suspend fun getAll(): List<PetData>

    @Query("SELECT * FROM PetData")
    fun getAllFlow(): Flow<List<PetData>>

    @Query("SELECT * FROM PetData WHERE id = :id")
    suspend fun getById(id: Long): PetData?

    @Insert
    suspend fun insert(employee: PetData): Long

    @Update
    suspend fun update(employee: PetData)

    @Delete
    suspend fun delete(employee: PetData)
}