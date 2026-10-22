package io.github.dallanoce.medreminder.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.dallanoce.medreminder.data.local.room.AppDatabase
import io.github.dallanoce.medreminder.data.local.room.Medication
import io.github.dallanoce.medreminder.data.local.room.MedicationDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MedicationDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: MedicationDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        dao = db.medicationDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun add(name: String) {
        dao.insertMedication(Medication(name = name, intervalHours = 1, intervalMinutes = 0, orderIndex = dao.nextOrderIndex()))
    }

    @Test
    fun newMedicationsAreAppendedInOrder() = runBlocking {
        assertEquals(0, dao.nextOrderIndex())
        add("A"); add("B"); add("C")
        assertEquals(listOf("A" to 0, "B" to 1, "C" to 2), dao.getMedicationsOnce().map { it.name to it.orderIndex })
    }

    @Test
    fun deletingCloseTheGapAndLaterAddsDontDuplicatePositions() = runBlocking {
        add("A"); add("B"); add("C")
        dao.deleteAndRenumber(dao.getMedicationsOnce().first { it.name == "B" })
        assertEquals(listOf("A" to 0, "C" to 1), dao.getMedicationsOnce().map { it.name to it.orderIndex })

        add("D")
        assertEquals(listOf("A" to 0, "C" to 1, "D" to 2), dao.getMedicationsOnce().map { it.name to it.orderIndex })
    }

    @Test
    fun deletingTheFirstMakesTheNextOneFirst() = runBlocking {
        add("A"); add("B")
        dao.deleteAndRenumber(dao.getMedicationsOnce().first())
        assertEquals(listOf("B" to 0), dao.getMedicationsOnce().map { it.name to it.orderIndex })
    }
}
