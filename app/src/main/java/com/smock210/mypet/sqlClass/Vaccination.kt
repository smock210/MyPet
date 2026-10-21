package com.smock210.mypet.sqlClass

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Vaccination(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var petId: Long = 0,
    var name: String = "",
    var dateMillis: Long = System.currentTimeMillis(),
    var reminderEnabled: Boolean = true,
    var notes: String = "",
    var done: Boolean = false
)