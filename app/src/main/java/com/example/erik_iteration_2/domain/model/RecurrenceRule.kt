package com.example.erik_iteration_2.domain.model

import kotlinx.datetime.DayOfWeek

enum class WeekParity { EVEN, ODD }

/**
 * How a recurring item repeats. Encoded to a single string column so the schema
 * stays flat; see [encode] and [decode] for the format.
 */
sealed class RecurrenceRule {

    /**
     * Every day. The only rule without a weekday — the setup questionnaire's
     * daily routines would otherwise need seven definitions each.
     */
    data object Daily : RecurrenceRule()

    data class Weekly(val weekday: DayOfWeek) : RecurrenceRule()

    data class Biweekly(
        val weekday: DayOfWeek,
        val parity: WeekParity,
    ) : RecurrenceRule()

    /** The [weekOfMonth]-th [weekday] of the month, 1-based. */
    data class Monthly(
        val weekday: DayOfWeek,
        val weekOfMonth: Int,
    ) : RecurrenceRule()

    fun encode(): String = when (this) {
        is Daily -> "D"
        is Weekly -> "W:$weekday"
        is Biweekly -> "B:$weekday:$parity"
        is Monthly -> "M:$weekday:$weekOfMonth"
    }

    companion object {
        fun decode(value: String): RecurrenceRule {
            val parts = value.split(':')
            if (parts[0] == "D") return Daily
            val weekday = DayOfWeek.valueOf(parts[1])
            return when (parts[0]) {
                "W" -> Weekly(weekday)
                "B" -> Biweekly(weekday, WeekParity.valueOf(parts[2]))
                "M" -> Monthly(weekday, parts[2].toInt())
                else -> error("Unknown recurrence rule: $value")
            }
        }
    }
}
