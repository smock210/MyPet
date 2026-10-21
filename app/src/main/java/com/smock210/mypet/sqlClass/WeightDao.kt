package com.smock210.mypet.sqlClass

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {

    @Query("SELECT * FROM WeightRecord WHERE petId = :petId ORDER BY dateMillis ASC")
    fun getWeightsForPetFlow(petId: Long): Flow<List<WeightRecord>>

    @Query("SELECT * FROM WeightRecord WHERE petId = :petId ORDER BY dateMillis ASC")
    suspend fun getWeightsForPet(petId: Long): List<WeightRecord>

    @Insert
    suspend fun insert(record: WeightRecord): Long

    @Query("DELETE FROM WeightRecord WHERE petId = :petId")
    suspend fun deleteAllForPet(petId: Long)
}
