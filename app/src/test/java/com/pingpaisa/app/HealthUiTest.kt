package com.pingpaisa.app

import com.pingpaisa.app.healthscore.HealthScoreHistory
import com.pingpaisa.app.healthscore.InsightEngine
import com.pingpaisa.app.healthscore.ScoreCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class HealthUiTest {

    private val today = LocalDate.of(2026, 11, 6)
    private val zone: ZoneId = ZoneOffset.ofHoursMinutes(5, 30)   // IST
    private val en = Locale.ENGLISH

    private fun at(date: LocalDate, hour: Int = 12) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun snap(score: Int, date: LocalDate) = HealthScoreHistory(score = score, riskLevel = "LOW", timestamp = at(date))

    private fun ui(raw: ScoreCalculator.RawFactors, history: List<HealthScoreHistory> = emptyList()): HealthUi {
        val result = ScoreCalculator.calculate(raw)
        return HealthUi.from(result, InsightEngine.fallback(raw, result), raw.income, raw.expense, history, today, zone, en)
    }

    private val healthy = ScoreCalculator.RawFactors(
        income = 100000.0, expense = 40000.0, totalEmiAmount = 0.0, monthlyTotals = emptyList(), recurringReliability = null)

    @Test fun scoreRiskAndFactorsComeFromTheEngine() {
        val u = ui(healthy)
        assertEquals(100, u.score)                  // saves 60%, no EMIs, rest neutral
        assertEquals("LOW RISK", u.riskLabel)
        assertEquals(listOf("Savings rate", "EMI burden", "Spending consistency", "Recurring reliability"), u.factors.map { it.label })
        assertEquals(listOf(100, 100, 100, 100), u.factors.map { it.value })
        assertEquals("You save 60% of income (30%+ = full marks)", u.factors[0].detail)
        assertEquals("Needs 2+ months of data, counted as neutral for now", u.factors[2].detail)
        assertEquals("NOVEMBER 2026", u.eyebrow)
    }

    @Test fun heavyEmisAndOverspendingShowUp() {
        val u = ui(healthy.copy(expense = 120000.0, totalEmiAmount = 60000.0))
        assertEquals("HIGH RISK", u.riskLabel)
        assertEquals(0, u.factors[0].value)
        assertEquals("You're spending more than you earn", u.factors[0].detail)
        assertEquals("EMIs use 60% of income (50%+ = zero marks)", u.factors[1].detail)
    }

    @Test fun ruleBasedInsightIsLabelledHonestly() {
        val u = ui(healthy)
        assertEquals("INSIGHT", u.insightLabel)
        assertTrue(u.insight.startsWith("Your score is 100/100"))
        assertEquals("Based on ₹1,00,000 income and ₹40,000 going out this month, " +
            "including EMIs and other recurring payments not yet paid.", u.basis)
    }

    @Test fun changeComparesWithTheLastScoreBeforeThisMonth() {
        val history = listOf(snap(60, LocalDate.of(2026, 9, 30)), snap(70, LocalDate.of(2026, 10, 28)), snap(90, LocalDate.of(2026, 11, 2)))
        assertEquals("Up 5 points since October", HealthUi.changeText(75, history, today, zone, en))
        assertEquals("Down 1 point since October", HealthUi.changeText(69, history, today, zone, en))
        assertEquals("Same as in October", HealthUi.changeText(70, history, today, zone, en))
    }

    @Test fun firstMonthHasNoComparison() {
        assertTrue(HealthUi.changeText(80, listOf(snap(78, LocalDate.of(2026, 11, 1))), today, zone, en).startsWith("Your first score"))
        assertTrue(HealthUi.changeText(80, emptyList(), today, zone, en).startsWith("Your first score"))
    }

    @Test fun snapshotIsSavedOncePerDayUnlessTheScoreChanges() {
        assertTrue(HealthUi.shouldRecord(80, null, today, zone))
        assertFalse(HealthUi.shouldRecord(80, snap(80, today), today, zone))
        assertTrue(HealthUi.shouldRecord(81, snap(80, today), today, zone))
        assertTrue(HealthUi.shouldRecord(80, snap(80, today.minusDays(1)), today, zone))
    }
}
