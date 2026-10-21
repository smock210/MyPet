package com.smock210.mypet.sqlClass

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthDao {

    // ---------- Прививки ----------
    @Query("SELECT * FROM Vaccination WHERE petId = :petId ORDER BY dateMillis ASC")
    suspend fun getVaccinationsForPet(petId: Long): List<Vaccination>

    @Query("SELECT * FROM Vaccination WHERE petId = :petId ORDER BY dateMillis ASC")
    fun getVaccinationsForPetFlow(petId: Long): Flow<List<Vaccination>>

    @Query("SELECT * FROM Vaccination")
    suspend fun getAllVaccinations(): List<Vaccination>

    @Insert
    suspend fun insertVaccination(v: Vaccination): Long

    @Update
    suspend fun updateVaccination(v: Vaccination)

    @Delete
    suspend fun deleteVaccination(v: Vaccination)

    // ---------- Записи к врачу ----------
    @Query("SELECT * FROM VetVisit WHERE petId = :petId ORDER BY dateMillis ASC")
    suspend fun getVisitsForPet(petId: Long): List<VetVisit>

    @Query("SELECT * FROM VetVisit WHERE petId = :petId ORDER BY dateMillis ASC")
    fun getVisitsForPetFlow(petId: Long): Flow<List<VetVisit>>

    @Query("SELECT * FROM VetVisit")
    suspend fun getAllVisits(): List<VetVisit>

    @Insert
    suspend fun insertVisit(v: VetVisit): Long

    @Update
    suspend fun updateVisit(v: VetVisit)

    @Delete
    suspend fun deleteVisit(v: VetVisit)
}