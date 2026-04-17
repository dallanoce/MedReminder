package com.example.medreminder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.medreminder.alarm.AlarmScheduler
import com.example.medreminder.data.local.datastore.DataStoreManager
import com.example.medreminder.data.local.room.AppDatabase
import com.example.medreminder.data.local.room.Medication
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class MedViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val medicationDao = database.medicationDao()
    private val dataStoreManager = DataStoreManager(application)
    private val alarmScheduler = AlarmScheduler(application)

    val medications: StateFlow<List<Medication>> = medicationDao.getAllMedications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val firstDoseTime: StateFlow<Long?> = dataStoreManager.firstDoseTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun addMedication(name: String, hours: Int, minutes: Int) {
        viewModelScope.launch {
            val currentMeds = medications.value
            val newMed = Medication(
                name = name,
                intervalHours = hours,
                intervalMinutes = minutes,
                orderIndex = currentMeds.size
            )
            medicationDao.insertMedication(newMed)
        }
    }

    fun deleteMedication(medication: Medication) {
        viewModelScope.launch {
            medicationDao.deleteMedication(medication)
        }
    }

    fun setFirstDoseTime(timestamp: Long) {
        viewModelScope.launch {
            dataStoreManager.saveFirstDoseTime(timestamp)
            scheduleAllAlarms(timestamp)
        }
    }

    fun clearDay() {
        viewModelScope.launch {
            dataStoreManager.clearFirstDoseTime()
            medications.value.forEach {
                alarmScheduler.cancelAlarm(it.id)
            }
        }
    }

    private fun scheduleAllAlarms(firstDoseTimestamp: Long) {
        val meds = medications.value
        var currentTime = firstDoseTimestamp
        
        meds.forEach { med ->
            // In a real app, the interval is FROM THE PREVIOUS medication.
            // But for the first one, it might be 0 from the "start".
            // The requirements say: "scheduling distance in hours/minutes from the previous medication".
            // So if it's the first medication, the interval is relative to the "start" time input?
            // Actually, "When the user takes their first medication of the day, they input the time... 
            // calculate... subsequent medications... based on the saved intervals".
            
            // Let's assume the first medication in the list is the one taken at the input time.
            // Or maybe the input time IS the first dose, and we schedule starting from there.
            
            // Re-reading: "provide a Name and the Time Interval ... from the previous medication".
            // So Med 1: Name, Interval (from start? or is Med 1 the start?)
            // Usually, Med 1 is at T=0. Med 2 is at T=T1+Interval2.
            
            // If the user inputs the time of the FIRST dose, then med[0] is at that time.
            // med[1] is at firstDoseTime + med[1].interval.
            
            if (med == meds.firstOrNull()) {
                // First medication is exactly at the input time
                alarmScheduler.scheduleAlarm(med.id, med.name, currentTime)
            } else {
                // Subsequent medications are at previousTime + their interval
                currentTime += (med.intervalHours * 3600 + med.intervalMinutes * 60) * 1000
                alarmScheduler.scheduleAlarm(med.id, med.name, currentTime)
            }
        }
    }
}
