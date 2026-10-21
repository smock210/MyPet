package com.smock210.mypet.sqlClass

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PetPhotoDao {
    @Query("SELECT * FROM PetPhoto WHERE petId = :petId ORDER BY isPrimary DESC, createdAt ASC")
    suspend fun getPhotosForPet(petId: Long): List<PetPhoto>

    @Query("SELECT * FROM PetPhoto WHERE petId = :petId ORDER BY isPrimary DESC, createdAt ASC")
    fun getPhotosForPetFlow(petId: Long): Flow<List<PetPhoto>>

    @Query("SELECT * FROM PetPhoto")
    suspend fun getAllPhotos(): List<PetPhoto>

    @Query("SELECT * FROM PetPhoto WHERE id = :id")
    suspend fun getById(id: Long): PetPhoto?

    @Insert
    suspend fun insert(photo: PetPhoto)

    @Update
    suspend fun update(photo: PetPhoto)

    @Delete
    suspend fun delete(photo: PetPhoto)

    @Query("DELETE FROM PetPhoto WHERE petId = :petId")
    suspend fun deleteAllForPet(petId: Long)
}