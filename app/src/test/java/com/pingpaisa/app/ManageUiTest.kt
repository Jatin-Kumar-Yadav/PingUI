package com.pingpaisa.app

import com.pingpaisa.app.data.MerchantOverride
import com.pingpaisa.app.data.Transaction
import org.junit.Assert.*
import org.junit.Test

class ManageUiTest {
    private var n = 0
    private fun tx(recipient: String, category: String, type: String = "expense") =
        Transaction(amount = 100.0, recipient = recipient, date = "01/11/25", refNumber = "r${n++}", rawText = "", category = category, type = type)

    private val builtIn = listOf("Food & Dining", "Travel", "Shopping")

    @Test fun merchantsUseThePaymentsSpellingAndCount() {
        val rows = ManageUi.merchants(
            listOf(MerchantOverride("swiggy", "Food & Dining"), MerchantOverride("old shop", "Shopping")),
            listOf(tx("Swiggy", "Food & Dining"), tx("SWIGGY ", "Food & Dining"), tx("Swiggy", "Income", type = "income")))
        assertEquals(listOf("Old Shop", "Swiggy"), rows.map { it.name })   // sorted; no payment left → capitalised key
        assertEquals(2, rows.first { it.key == "swiggy" }.payments)        // income not counted
        assertEquals(0, rows.first { it.key == "old shop" }.payments)
    }

    @Test fun categoriesCountAndMarkCustom() {
        val rows = ManageUi.categories(
            listOf(tx("A", "Food & Dining"), tx("B", "Food & Dining"), tx("C", "Friends"), tx("D", "Uncategorized"), tx("E", "Income", type = "income")),
            listOf(MerchantOverride("x", "Gym")), builtIn)
        assertEquals(listOf("Food & Dining", "Friends", "Uncategorized", "Gym"), rows.map { it.name })
        assertEquals(listOf(false, true, false, true), rows.map { it.custom })
        assertEquals(0, rows.last().payments)   // only a remembered merchant uses Gym
    }

    @Test fun builtInMatchIgnoresCase() {
        assertFalse(ManageUi.categories(listOf(tx("A", "travel")), emptyList(), builtIn).single().custom)
    }

    @Test fun subtitles() {
        assertEquals("None yet · set one from a payment's details", ManageUi.merchantsSubtitle(0))
        assertEquals("1 remembered merchant", ManageUi.merchantsSubtitle(1))
        assertEquals("5 remembered merchants", ManageUi.merchantsSubtitle(5))
        assertEquals("2 in use · 1 custom category",
            ManageUi.categoriesSubtitle(listOf(CategoryRow("A", 1, false), CategoryRow("B", 1, true))))
        assertEquals("0 in use · no custom categories", ManageUi.categoriesSubtitle(emptyList()))
    }

    @Test fun renameRules() {
        assertNull(ManageUi.renameError("Friends", "Friends & Family"))
        assertNotNull(ManageUi.renameError("Friends", "Friends"))
        assertNotNull(ManageUi.renameError("Friends", "  "))
        assertNotNull(ManageUi.renameError("Friends", "Income"))
    }
}
