package com.smock210.mypet.sqlClass

import androidx.room.*

@Entity
data class PetData(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var name: String? = null,
    var species: String = "dog", // dog, cat, bird, fish
    var breed: String = "",
    var ageYears: Int = 0,
    var weightKg: Double = 0.0,
    var smallDetail: String? = null,
    var photoUri: String? = null,
    var createdAt: Long = System.currentTimeMillis()
)