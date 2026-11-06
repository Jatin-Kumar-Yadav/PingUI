package com.pingpaisa.app

import com.pingpaisa.app.data.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class DetailUiTest {

    private val today = LocalDate.of(2026, 11, 6)
    private val en = Locale.ENGLISH

    private fun ui(t: Transaction) = DetailUi.from(t, today, en)

    private val smsCoffee = Transaction(id = 7, amount = 320.0, recipient = "Third Wave Coffee", date = "06/11/26",
        refNumber = "626022634234", rawText = "", source = "SMS", category = "Food & Dining", subcategory = "Cafe & Coffee")

    @Test fun smsPaymentShowsAmountStatusAndReference() {
        val d = ui(smsCoffee)
        assertEquals(7, d.id)
        assertEquals("−₹320", d.amount)
        assertEquals("Successful", d.status)
        assertEquals("Today • UPI", d.subline)
        assertEquals(listOf(
            "Category" to "Food & Dining", "Type" to "Cafe & Coffee",
            "Date" to "06 Nov 2026", "UPI reference" to "626022634234"
        ), d.rows)
        assertFalse(d.isIncome)
    }

    @Test fun manualEntryHidesTheMadeUpReference() {
        val d = ui(smsCoffee.copy(source = "manual", refNumber = "MANUAL-1730000000000", subcategory = "Miscellaneous", date = "05/11/26"))
        assertEquals("Recorded", d.status)
        assertEquals("Yesterday • Manual", d.subline)
        assertEquals(listOf("Category" to "Food & Dining", "Date" to "05 Nov 2026", "Added" to "Manually"), d.rows)
    }

    @Test fun incomeShowsPlusSignAndReceived() {
        val d = ui(Transaction(id = 9, amount = 5000.0, recipient = "Freelance payment", date = "01/11/26", refNumber = "MANUAL-2",
            rawText = "", source = "manual", category = "Income", subcategory = "Other income", type = Transaction.TYPE_INCOME))
        assertEquals("+₹5,000", d.amount)
        assertEquals("Received", d.status)
        assertTrue(d.isIncome)
        assertEquals("Category" to "Income", d.rows.first())
        assertEquals("01 Nov • Manual", d.subline)
    }

    @Test fun editableFieldsComeFromTheTransaction() {
        val d = ui(smsCoffee.copy(note = "With Nisha"))
        assertEquals("Third Wave Coffee", d.recipient)
        assertEquals("Food & Dining", d.category)
        assertEquals("With Nisha", d.note)
        assertTrue(d.canRecategorize)
        assertFalse(ui(smsCoffee.copy(type = Transaction.TYPE_INCOME)).canRecategorize)
    }

    @Test fun gridShowsQuickCategoriesThenMore() {
        val tiles = detailCategoryTiles("Food & Dining")
        assertEquals(listOf("Food", "Travel", "Shopping", "Bills", "More"), tiles.map { it.label })
        assertEquals(listOf("Food & Dining", "Travel", "Shopping", "Utilities & Bills", null), tiles.map { it.category })
    }

    @Test fun gridAddsTheSelectedCategoryWhenItIsNotAQuickOne() {
        val tiles = detailCategoryTiles("Health/Medical")
        assertEquals(listOf("Food", "Travel", "Shopping", "Bills", "Health/Medical", "More"), tiles.map { it.label })
        assertEquals("Health/Medical", tiles[4].category)
    }

    @Test fun pickerListMergesDatasetAndCustomCategoriesOnce() {
        val list = pickableCategories(
            dataset = listOf("Travel", "Food & Dining", "Uncategorized"),
            inUse = listOf("friends & family", "Travel", " Friends & Family ", "Income", "")
        )
        assertEquals(listOf("Food & Dining", "friends & family", "Travel", "Uncategorized"), list)
    }

    @Test fun customCategoryRules() {
        assertEquals("Type a category name", customCategoryError("  "))
        assertEquals("Keep it under 30 characters", customCategoryError("x".repeat(31)))
        assertTrue(customCategoryError("income")!!.contains("Income"))
        assertEquals(null, customCategoryError("Friends & Family"))
    }

    @Test fun unreadableDateIsShownAsIs() {
        val d = ui(smsCoffee.copy(date = "31/02/26"))
        assertTrue(d.rows.contains("Date" to "31/02/26"))
    }
}
