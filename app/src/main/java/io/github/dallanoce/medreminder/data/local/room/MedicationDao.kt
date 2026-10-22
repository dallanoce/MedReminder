package io.github.dallanoce.medreminder.data.local.room

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications ORDER BY orderIndex ASC, id ASC")
    fun getAllMedications(): Flow<List<Medication>>

    @Query("SELECT * FROM medications ORDER BY orderIndex ASC, id ASC")
    suspend fun getMedicationsOnce(): List<Medication>

    /** The position for a medication added at the end of the plan. */
    @Query("SELECT COALESCE(MAX(orderIndex) + 1, 0) FROM medications")
    suspend fun nextOrderIndex(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedication(medication: Medication)

    @Delete
    suspend fun deleteMedication(medication: Medication)

    @Query("DELETE FROM medications")
    suspend fun deleteAllMedications()

    @Update
    suspend fun updateMedication(medication: Medication)

    /** Deletes a medication and closes the gap, so positions stay 0, 1, 2, … */
    @Transaction
    suspend fun deleteAndRenumber(medication: Medication) {
        deleteMedication(medication)
        getMedicationsOnce().forEachIndexed { index, med ->
            if (med.orderIndex != index) updateMedication(med.copy(orderIndex = index))
        }
    }
}
