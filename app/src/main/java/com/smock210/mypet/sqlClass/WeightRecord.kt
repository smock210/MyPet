package com.smock210.mypet.sqlClass

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Запись о взвешивании питомца (FIX_PLAN.md, 6.1).
 */
@Entity
data class WeightRecord(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var petId: Long = 0,
    var weightKg: Double = 0.0,
    var dateMillis: Long = System.currentTimeMillis()
)
