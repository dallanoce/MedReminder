package io.github.dallanoce.medreminder.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidationTest {

    @Test
    fun `numeric fields keep at most three digits`() {
        assertEquals("123", InputValidation.digitsOnly("1-2a3456"))
        assertEquals("", InputValidation.digitsOnly("-"))
    }

    @Test
    fun `empty interval fields count as zero`() {
        assertEquals(0 to 0, InputValidation.parseInterval("", ""))
        assertEquals(2 to 0, InputValidation.parseInterval("2", ""))
        assertEquals(0 to 45, InputValidation.parseInterval("", "45"))
    }

    @Test
    fun `interval must be within a day`() {
        assertEquals(24 to 0, InputValidation.parseInterval("24", "0"))
        assertEquals(23 to 59, InputValidation.parseInterval("23", "59"))
        assertNull(InputValidation.parseInterval("24", "1"))
        assertNull(InputValidation.parseInterval("25", "0"))
        assertNull(InputValidation.parseInterval("0", "60"))
        assertNull(InputValidation.parseInterval("999", "0"))
    }

    @Test
    fun `reminder offset accepts 0 to 120 minutes`() {
        assertEquals(0, InputValidation.parseReminderOffset("0"))
        assertEquals(120, InputValidation.parseReminderOffset("120"))
        assertNull(InputValidation.parseReminderOffset("121"))
        assertNull(InputValidation.parseReminderOffset("-5"))
        assertNull(InputValidation.parseReminderOffset(""))
    }

    @Test
    fun `medication name is required and limited in length`() {
        assertTrue(InputValidation.isValidMedicationName("Aspirin"))
        assertFalse(InputValidation.isValidMedicationName("   "))
        assertFalse(InputValidation.isValidMedicationName("x".repeat(41)))
        assertTrue(InputValidation.isValidMedicationName("  " + "x".repeat(40) + "  "))
    }
}
