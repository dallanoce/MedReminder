package io.github.dallanoce.medreminder.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Bump the version for any schema change and add a migration (or @AutoMigration) plus a test in
// MigrationTest: a destructive migration would wipe users' medication plans.
@Database(entities = [Medication::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "med_reminder_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
