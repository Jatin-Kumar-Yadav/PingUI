package com.pingpaisa.app

import com.pingpaisa.app.data.Transaction
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class HeatmapUiTest {
    private val today = LocalDate.of(2025, 11, 20)   // November 2025 starts on a Saturday
    private var n = 0
    private fun tx(amount: Double, date: String, category: String = "Food & Dining", type: String = "expense") =
        Transaction(amount = amount, recipient = "X", date = date, refNumber = "r${n++}", rawText = "", category = category, type = type)
    private fun ui(vararg t: Transaction) = HeatmapUi.from(t.toList(), today, Locale.US)

    @Test fun threeMonthsOldestFirst() {
        assertEquals(listOf("September", "October", "November"), ui().months.map { it.tab })
    }

    @Test fun gridIsWholeWeeksStartingMonday() {
        val nov = ui().months.last()
        assertEquals(0, nov.cells.size % 7)
        assertEquals(5, nov.cells.takeWhile { it.day == null }.size)   // Saturday is the 6th column
        assertEquals(1, nov.cells[5].day)
        assertEquals(30, nov.cells.mapNotNull { it.day }.max())
    }

    @Test fun totalsLevelsAndActiveDays() {
        val nov = ui(tx(100.0, "03/11/25"), tx(300.0, "03/11/25"), tx(1000.0, "10/11/25"), tx(200.0, "11/11/25")).months.last()
        assertEquals("₹1,600", nov.total)
        assertEquals("3 active days", nov.activeDays)
        fun level(day: Int) = nov.cells.first { it.day == day }.level
        assertEquals(4, level(10))
        assertEquals(2, level(3))     // 400 of 1000 = 40%
        assertEquals(1, level(11))    // 20%
        assertEquals(0, level(5))
    }

    @Test fun highestDayIsSelectedAndLabelled() {
        val nov = ui(tx(100.0, "03/11/25"), tx(1000.0, "10/11/25")).months.last()
        val cell = nov.cells[nov.defaultSelected]
        assertEquals(10, cell.day)
        assertEquals("Highest spend day", cell.title)
        assertEquals("MON, 10 NOVEMBER", cell.label)
        assertEquals("₹1,000", cell.amount)
    }

    @Test fun emptyMonthSelectsTodayOrFirst() {
        val m = ui()
        assertEquals(20, m.months.last().cells[m.months.last().defaultSelected].day)
        assertEquals(1, m.months.first().cells[m.months.first().defaultSelected].day)
        assertEquals("No spending recorded in November yet.", m.months.last().insight)
        assertEquals("0 active days", m.months.last().activeDays)
    }

    @Test fun futureDaysAreLabelled() {
        val nov = ui().months.last()
        assertEquals("Not here yet", nov.cells.first { it.day == 25 }.title)
        assertEquals("No spending", nov.cells.first { it.day == 5 }.title)
    }

    @Test fun incomeAndInvalidDatesIgnored() {
        val nov = ui(tx(5000.0, "03/11/25", type = "income"), tx(700.0, "31/02/25")).months.last()
        assertEquals("₹0", nov.total)
    }

    @Test fun otherMonthsAreSeparate() {
        val m = ui(tx(500.0, "15/10/25"))
        assertEquals("₹500", m.months[1].total)
        assertEquals("₹0", m.months.last().total)
    }

    private fun insight(vararg t: Transaction) = HeatmapUi.from(t.toList(), today, Locale.US).months.last().insight

    // 1-2 Nov are Sat/Sun. Up to the 20th there are 6 weekend days and 14 weekdays.
    @Test fun weekendInsightMore() {
        assertEquals("You spend 900% more per day on weekends, mostly on Travel.",
            insight(tx(600.0, "01/11/25", "Travel"), tx(140.0, "03/11/25")))   // 100 a day against 10
    }

    @Test fun weekendInsightLess() {
        assertEquals("You spend 50% less per day on weekends than on weekdays.",
            insight(tx(60.0, "01/11/25"), tx(280.0, "03/11/25")))              // 10 a day against 20
    }

    @Test fun weekendInsightSame() {
        assertEquals("Your spending is about the same on weekends and weekdays.",
            insight(tx(60.0, "01/11/25"), tx(140.0, "03/11/25")))              // 10 a day against 10
    }

    @Test fun weekendInsightNeedsBothKinds() {
        assertEquals("Not enough days yet to compare weekends with weekdays.", insight(tx(60.0, "03/11/25")))
    }
}
