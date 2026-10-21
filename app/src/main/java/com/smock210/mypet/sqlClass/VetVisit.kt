package com.smock210.mypet.sqlClass

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class VetVisit(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var petId: Long = 0,
    var clinicName: String = "",
    var doctorName: String = "",
    var dateMillis: Long = System.currentTimeMillis(),
    var notes: String = "",
    var diagnosis: String = "",
    var calendarEventId: Long = 0
)