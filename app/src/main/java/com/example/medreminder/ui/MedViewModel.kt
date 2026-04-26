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
import kotlinx.coroutines.flow.first
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

    val reminderOffset: StateFlow<Int> = dataStoreManager.reminderOffset
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 15)

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
            alarmScheduler.cancelAlarm(medication.id)
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

    fun setReminderOffset(offset: Int) {
        viewModelScope.launch {
            dataStoreManager.saveReminderOffset(offset)
            // Reschedule alarms with new offset if first dose is set
            val firstDose = firstDoseTime.value
            if (firstDose != null) {
                scheduleAllAlarms(firstDose)
            }
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
        val offsetMin = reminderOffset.value
        var currentTime = firstDoseTimestamp
        
        meds.forEach { med ->
            if (med == meds.firstOrNull()) {
                // First medication of the day: usually no alarm per original code logic
                // But we should probably keep consistency with AlarmReceiver rescheduling logic if any.
                // Original MedViewModel didn't schedule for first med.
                // Let's stick to that but add reminder if applicable? 
                // Actually, the user says "add a reminder 15 min before the actual time of medication".
                // If the first med has no alarm, it probably shouldn't have a reminder either.
            } else {
                currentTime += (med.intervalHours * 3600 + med.intervalMinutes * 60) * 1000
                
                // Schedule main alarm
                alarmScheduler.scheduleAlarm(med.id, med.name, currentTime)
                
                // Schedule reminder
                val reminderTime = currentTime - (offsetMin * 60 * 1000)
                if (reminderTime > System.currentTimeMillis()) {
                    alarmScheduler.scheduleAlarm(med.id, med.name, reminderTime, isReminder = true)
                }
            }
        }
    }
}
