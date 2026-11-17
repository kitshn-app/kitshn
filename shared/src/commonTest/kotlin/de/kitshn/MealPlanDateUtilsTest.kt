package de.kitshn

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Regression tests for the meal-plan day-shift bugs (#398, #423). */
class MealPlanDateUtilsTest {

    @Test
    fun parseTandoorDate_fromUtcMidnight_returnsSameCalendarDay() {
        assertEquals(
            LocalDate(2026, 5, 6),
            "2026-05-06T00:00:00Z".parseTandoorDate()
        )
    }

    @Test
    fun parseTandoorDate_fromLegacyDateString_returnsSameDay() {
        assertEquals(
            LocalDate(2026, 5, 6),
            "2026-05-06".parseTandoorDate()
        )
    }

    @Test
    fun toStartOfDayString_sendsUtcMidnightForPickedDay() {
        assertEquals(
            "2026-05-13T00:00:00Z",
            LocalDate(2026, 5, 13).toStartOfDayString()
        )
    }

    @Test
    fun mealPlanDate_roundTrip_preservesPickedDay() {
        val picked = LocalDate(2026, 5, 13)
        val sent = picked.toStartOfDayString()
        assertEquals(picked, sent.parseTandoorDate())
    }

    @Test
    fun mealPlanDayFilter_multiDayPlan_coversEveryDayInRange() {
        val from = "2026-05-06T00:00:00Z".parseTandoorDate()
        val to = "2026-05-08T00:00:00Z".parseTandoorDate()

        fun isShownOn(day: LocalDate) = day in from..to

        assertTrue(isShownOn(LocalDate(2026, 5, 6)))
        assertTrue(isShownOn(LocalDate(2026, 5, 7)))
        assertTrue(isShownOn(LocalDate(2026, 5, 8)))
    }
}
