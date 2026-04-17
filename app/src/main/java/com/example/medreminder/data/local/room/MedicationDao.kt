package com.example.medreminder.data.local.room

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications ORDER BY orderIndex ASC")
    fun getAllMedications(): Flow<List<Medication>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedication(medication: Medication)

    @Delete
    suspend fun deleteMedication(medication: Medication)

    @Query("DELETE FROM medications")
    suspend fun deleteAllMedications()

    @Update
    suspend fun updateMedication(medication: Medication)

    @Transaction
    suspend fun updatePlan(medications: List<Medication>) {
        deleteAllMedications()
        medications.forEachIndexed { index, med ->
            insertMedication(med.copy(orderIndex = index))
        }
    }
}
