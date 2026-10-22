package io.github.dallanoce.medreminder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.dallanoce.medreminder.alarm.AlarmScheduler
import io.github.dallanoce.medreminder.data.local.datastore.DataStoreManager
import io.github.dallanoce.medreminder.data.local.room.AppDatabase
import io.github.dallanoce.medreminder.data.local.room.Medication
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    // Editing the plan while a day is active reschedules the doses that follow the change.
    fun addMedication(name: String, hours: Int, minutes: Int) {
        viewModelScope.launch {
            val newMed = Medication(
                name = name,
                intervalHours = hours,
                intervalMinutes = minutes,
                orderIndex = medicationDao.nextOrderIndex()
            )
            medicationDao.insertMedication(newMed)
            alarmScheduler.rescheduleAll()
        }
    }

    fun deleteMedication(medication: Medication) {
        viewModelScope.launch {
            alarmScheduler.cancelAlarm(medication.id)
            medicationDao.deleteAndRenumber(medication)
            alarmScheduler.rescheduleAll()
        }
    }

    fun setFirstDoseTime(timestamp: Long) {
        viewModelScope.launch {
            dataStoreManager.saveFirstDoseTime(timestamp)
            alarmScheduler.rescheduleAll()
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
            // Moves today's reminders to the new offset (does nothing if no schedule is active)
            alarmScheduler.rescheduleAll()
        }
    }

    // Each screen has its own MedViewModel, so the StateFlows above may not be collected here:
    // read the plan from the database instead of medications.value.
    fun clearDay() {
        viewModelScope.launch {
            dataStoreManager.clearFirstDoseTime()
            medicationDao.getAllMedications().first().forEach {
                alarmScheduler.cancelAlarm(it.id)
            }
        }
    }
}
