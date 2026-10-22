package io.github.dallanoce.medreminder.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val intervalHours: Int,
    val intervalMinutes: Int,
    val orderIndex: Int // To maintain the sequence
)
