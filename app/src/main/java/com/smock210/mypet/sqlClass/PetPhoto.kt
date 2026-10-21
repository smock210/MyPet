package com.smock210.mypet.sqlClass

import androidx.room.*

@Entity
data class PetPhoto(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var petId: Long = 0,
    var photoUri: String = "",
    var isPrimary: Boolean = false,
    var createdAt: Long = System.currentTimeMillis()
)