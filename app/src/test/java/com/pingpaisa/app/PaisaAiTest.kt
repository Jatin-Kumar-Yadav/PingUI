package com.pingpaisa.app

import com.pingpaisa.app.data.Budget
import com.pingpaisa.app.data.RecurringItem
import com.pingpaisa.app.data.SavingsGoal
import com.pingpaisa.app.data.Transaction
import com.pingpaisa.app.healthscore.VerdictEngine.Verdict
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class PaisaAiTest {
    private val today = LocalDate.of(2025, 11, 20)
    private var n = 0
    private fun tx(amount: Double, date: String = "10/11/25", category: String = "Food & Dining", recipient: String = "Cafe", type: String = "expense") =
        Transaction(amount = amount, recipient = recipient, date = date, refNumber = "r${n++}", rawText = "", category = category, type = type)

    private fun ctx(
        income: Double = 50000.0, projected: Double = 30000.0, outflow: Double = 20000.0,
        transactions: List<Transaction> = emptyList(), budgets: List<Budget> = emptyList(),
        goals: List<SavingsGoal> = emptyList(), recurring: List<RecurringItem> = emptyList()
    ) = AiContext("Janvi Yadav", today, income, outflow, projected, 72, "low risk", "Your score is 72/100 (low risk).",
        transactions, budgets, goals, recurring)

    private fun ask(q: String, c: AiContext = ctx()) = PaisaAi.answer(q, c, Locale.US)

    @Test fun parsesAmounts() {
        assertEquals(20000.0, PaisaAi.parseAmount("can i spend ₹20,000 on a phone")!!, 0.0)
        assertEquals(5000.0, PaisaAi.parseAmount("rs 5000")!!, 0.0)
        assertEquals(20000.0, PaisaAi.parseAmount("buy a 20k watch")!!, 0.0)
        assertEquals(150000.0, PaisaAi.parseAmount("1.5 lakh bike")!!, 0.0)
        assertEquals(20000000.0, PaisaAi.parseAmount("2 crore flat")!!, 0.0)
        assertNull(PaisaAi.parseAmount("can i buy 2 shirts"))
        assertNull(PaisaAi.parseAmount("explain my score"))
    }

    @Test fun affordSafe() {
        val r = ask("Can I afford ₹10,000?")   // 50,000 - 30,000 - 10,000 = 10,000 left (20%)
        assertEquals("Yes, you can afford it.", r.title)
        assertEquals(Verdict.SAFE, r.metric!!.verdict)
        assertEquals("₹10,000", r.metric!!.value)
    }

    @Test fun affordTight() {
        val r = ask("can i buy a phone for 18000")   // 2,000 left, under the 5,000 cushion
        assertEquals(Verdict.TIGHT, r.metric!!.verdict)
        assertTrue(r.text.contains("₹15,000"))         // 50,000 - 30,000 - 5,000
    }

    @Test fun affordRisky() {
        val r = ask("Can I spend ₹25,000 on a new phone this month?")
        assertEquals("Not this month.", r.title)
        assertEquals(Verdict.RISKY, r.metric!!.verdict)
        assertEquals("−₹5,000", r.metric!!.value)
    }

    @Test fun affordNeedsIncome() {
        assertEquals("I need your income first.", ask("can i afford 5000", ctx(income = 0.0)).title)
    }

    @Test fun savingAdvicePicksTopCategory() {
        val c = ctx(transactions = listOf(tx(6000.0, category = "Shopping"), tx(4000.0)))
        val r = ask("Where can I save?", c)
        assertEquals("Start with Shopping.", r.title)
        assertTrue(r.text.contains("₹900"))      // 15% of 6,000
        assertTrue(r.text.contains("60%"))
        assertEquals(AiLink.Budgets, r.link)
    }

    @Test fun scoreAndForecast() {
        assertEquals("Your score is 72/100 (low risk).", ask("Explain my score").title)
        assertEquals(AiLink.Health, ask("explain my score").link)
        val f = ask("what is my forecast")
        assertEquals(AiLink.Forecast, f.link)
        assertTrue(f.text.contains("₹30,000"))
        assertTrue(f.text.contains("₹20,000 of your ₹50,000"))
    }

    @Test fun spendingByCategoryAndPeriod() {
        val c = ctx(transactions = listOf(
            tx(300.0), tx(200.0, date = "20/11/25"), tx(900.0, category = "Travel"),
            tx(5000.0, date = "15/10/25"), tx(7000.0, type = "income")))
        assertEquals("You spent ₹500 on Food & Dining this month.", ask("how much did i spend on food?", c).title)
        assertEquals("You spent ₹1,400 this month.", ask("How much have I spent?", c).title)
        assertEquals("You spent ₹200 today.", ask("what did i spend today", c).title)
        assertEquals("You spent ₹5,000 last month.", ask("how much did I spend last month", c).title)
        assertEquals("Nothing recorded.", ask("how much did i spend yesterday", c).title)
    }

    @Test fun biggestExpense() {
        val r = ask("what is my biggest expense", ctx(transactions = listOf(tx(300.0), tx(900.0, recipient = "Uber", category = "Travel"))))
        assertTrue(r.text.startsWith("₹900 to Uber"))
    }

    @Test fun budgetStatus() {
        assertEquals("You have no budgets yet.", ask("am i on budget").title)
        val c = ctx(transactions = listOf(tx(1200.0)), budgets = listOf(Budget("Food & Dining", 1000.0), Budget("Travel", 1000.0)))
        val r = ask("am I on budget?", c)
        assertEquals("You’ve crossed a budget.", r.title)
        assertTrue(r.text.contains("Food & Dining (120%)"))
    }

    @Test fun goalsAndRecurring() {
        val g = ask("how are my goals", ctx(goals = listOf(SavingsGoal(id = 1, name = "Trip", targetAmount = 1000.0, savedAmount = 250.0))))
        assertTrue(g.text.contains("Trip: 25%"))
        val r = ask("what bills are due", ctx(recurring = listOf(
            RecurringItem(name = "Rent", amount = 15000.0, category = "Rent", dayOfMonth = 1),
            RecurringItem(name = "Netflix", amount = 649.0, category = "Fun", dayOfMonth = 25))))
        assertTrue(r.text.indexOf("Netflix") < r.text.indexOf("Rent"))   // the 25th comes before the 1st
        assertTrue(r.text.contains("₹15,649"))
    }

    @Test fun incomeKept() {
        val r = ask("how much have i saved")
        assertEquals("This month you have kept ₹30,000 so far.", r.title)
        assertTrue(r.text.contains("60%"))
    }

    @Test fun helpAndUnknown() {
        assertEquals("Here’s what I can do.", ask("hello").title)
        assertEquals("I didn’t catch that.", ask("tell me a joke").title)
    }

    @Test fun greetingUsesFirstName() {
        assertEquals("Hi Janvi! I’ve looked at your November spending. What would you like to know?", PaisaAi.greeting("Janvi Yadav", "November"))
    }

    @Test fun suggestionsAreAnswerable() {
        PaisaAi.suggestions("November").forEach { assertNotEquals("I didn’t catch that.", ask(it).title) }
    }
}
