package com.pingpaisa.app

import com.pingpaisa.app.data.SavingsGoal
import org.junit.Assert.*
import org.junit.Test

class GoalsUiTest {
    private fun goal(saved: Double, target: Double = 200000.0) = SavingsGoal(id = 3, name = "Trip", targetAmount = target, savedAmount = saved)

    @Test fun halfwayGoal() {
        val row = GoalsUi.from(listOf(goal(136000.0))).single()
        assertEquals(68, row.percent)
        assertEquals("₹1,36,000 saved of ₹2,00,000", row.savedText)
        assertEquals("₹64,000 to go", row.toGoText)
        assertEquals(3, row.id)
    }

    @Test fun newGoalIsZero() {
        val row = GoalsUi.from(listOf(goal(0.0))).single()
        assertEquals(0, row.percent)
        assertEquals("₹2,00,000 to go", row.toGoText)
    }

    @Test fun reachedAndOverSavedStopAtHundred() {
        assertEquals("Goal reached", GoalsUi.from(listOf(goal(200000.0))).single().toGoText)
        val over = GoalsUi.from(listOf(goal(250000.0))).single()
        assertEquals(100, over.percent)
        assertEquals(1f, over.fraction, 0f)
    }

    @Test fun zeroTargetDoesNotCrash() {
        assertEquals(0, GoalsUi.from(listOf(goal(10.0, target = 0.0))).single().percent)
    }

    @Test fun validation() {
        assertTrue(GoalValidation.validate("Trip", "5000", "").isValid)
        assertTrue(GoalValidation.validate("Trip", "5000", "1200.5").isValid)
        assertNotNull(GoalValidation.validate(" ", "5000", "").name)
        assertNotNull(GoalValidation.validate("x".repeat(41), "5000", "").name)
        assertNotNull(GoalValidation.validate("Trip", "0", "").target)
        assertNotNull(GoalValidation.validate("Trip", "5000", ".").saved)
    }
}
