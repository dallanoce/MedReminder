package com.example.medreminder.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "med_reminder_prefs")

class DataStoreManager(private val context: Context) {

    companion object {
        val FIRST_DOSE_TIME_KEY = longPreferencesKey("first_dose_time")
    }

    val firstDoseTime: Flow<Long?> = context.dataStore.data
        .map { preferences ->
            preferences[FIRST_DOSE_TIME_KEY]
        }

    suspend fun saveFirstDoseTime(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[FIRST_DOSE_TIME_KEY] = timestamp
        }
    }

    suspend fun clearFirstDoseTime() {
        context.dataStore.edit { preferences ->
            preferences.remove(FIRST_DOSE_TIME_KEY)
        }
    }
}
