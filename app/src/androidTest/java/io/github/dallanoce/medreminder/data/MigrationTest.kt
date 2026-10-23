package io.github.dallanoce.medreminder.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.dallanoce.medreminder.data.local.room.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks the exported schemas in app/schemas. When the database version is bumped, add a test here
 * that creates the previous version, inserts a plan, runs the migration and checks the plan survived.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun version1SchemaHoldsAPlan() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("INSERT INTO medications (name, intervalHours, intervalMinutes, orderIndex) VALUES ('A', 0, 0, 0)")
            db.query("SELECT COUNT(*) FROM medications").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
