package io.github.dallanoce.medreminder.ui

/** Validation for what the user types in the plan and settings dialogs. */
object InputValidation {
    const val MAX_INTERVAL_HOURS = 24
    const val MAX_REMINDER_OFFSET_MINUTES = 120
    const val MAX_NAME_LENGTH = 40
    private const val MAX_DIGITS = 3

    /** Keeps only what can be typed into a numeric field: up to 3 digits. */
    fun digitsOnly(text: String): String = text.filter { it.isDigit() }.take(MAX_DIGITS)

    /**
     * The interval from the previous medication, in (hours, minutes), or null if out of range.
     * Empty fields count as 0. The interval can be at most 24 hours (the plan is a daily one).
     */
    fun parseInterval(hoursText: String, minutesText: String): Pair<Int, Int>? {
        val hours = if (hoursText.isBlank()) 0 else hoursText.toIntOrNull() ?: return null
        val minutes = if (minutesText.isBlank()) 0 else minutesText.toIntOrNull() ?: return null
        if (hours !in 0..MAX_INTERVAL_HOURS || minutes !in 0..59) return null
        if (hours * 60 + minutes > MAX_INTERVAL_HOURS * 60) return null
        return hours to minutes
    }

    /** The early-reminder offset in minutes (0 = no reminder), or null if empty or out of range. */
    fun parseReminderOffset(text: String): Int? =
        text.toIntOrNull()?.takeIf { it in 0..MAX_REMINDER_OFFSET_MINUTES }

    fun isValidMedicationName(name: String): Boolean =
        name.isNotBlank() && name.trim().length <= MAX_NAME_LENGTH
}
