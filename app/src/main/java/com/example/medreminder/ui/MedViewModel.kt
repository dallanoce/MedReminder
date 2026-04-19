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

    val ringtoneUri: StateFlow<String?> = dataStoreManager.ringtoneUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val language: StateFlow<String?> = dataStoreManager.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val userName: StateFlow<String?> = dataStoreManager.userName
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

    fun setRingtoneUri(uri: String) {
        viewModelScope.launch {
            dataStoreManager.saveRingtoneUri(uri)
        }
    }

    fun setLanguage(languageCode: String) {
        viewModelScope.launch {
            dataStoreManager.saveLanguage(languageCode)
        }
    }

    fun setUserName(name: String) {
        viewModelScope.launch {
            dataStoreManager.saveUserName(name)
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
            if (med == meds.firstOrNull()) {
                // First medication is at the input time. 
                // Requirement: Don't receive notification for the first medication.
                // So we do NOT schedule an alarm for the first one.
            } else {
                // Subsequent medications are at previousTime + their interval
                currentTime += (med.intervalHours * 3600 + med.intervalMinutes * 60) * 1000
                alarmScheduler.scheduleAlarm(med.id, med.name, currentTime)
            }
        }
    }
}
