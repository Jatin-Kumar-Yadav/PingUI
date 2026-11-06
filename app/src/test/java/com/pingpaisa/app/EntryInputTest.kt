package com.pingpaisa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryInputTest {

    private fun type(vararg keys: String) = keys.fold("") { acc, k -> AmountInput.applyKey(acc, k) }

    @Test fun digitsBuildTheAmount() {
        assertEquals("1250", type("1", "2", "5", "0"))
    }

    @Test fun leadingZeroIsReplaced() {
        assertEquals("5", type("0", "5"))
        assertEquals("0.5", type("0", ".", "5"))
    }

    @Test fun decimalPointRules() {
        assertEquals("0.", type("."))                       // a bare point becomes "0."
        assertEquals("12.5", type("1", "2", ".", "5", "."))  // a second point is ignored
        assertEquals("12.55", type("1", "2", ".", "5", "5", "9"))   // two decimals at most
    }

    @Test fun backspaceRemovesTheLastCharacter() {
        assertEquals("12", type("1", "2", "3", "⌫"))
        assertEquals("", type("⌫"))
    }

    @Test fun wholePartIsCappedAtNineDigits() {
        assertEquals("999999999", type("9", "9", "9", "9", "9", "9", "9", "9", "9", "9"))
        assertEquals("999999999.5", type("9", "9", "9", "9", "9", "9", "9", "9", "9", ".", "5"))
    }

    @Test fun displayUsesIndianGrouping() {
        assertEquals("0", AmountInput.display(""))
        assertEquals("320", AmountInput.display("320"))
        assertEquals("1,250", AmountInput.display("1250"))
        assertEquals("1,23,456.5", AmountInput.display("123456.5"))
        assertEquals("12,34,56,789", AmountInput.display("123456789"))
        assertEquals("0.", AmountInput.display("0."))
    }

    @Test fun formNeedsAnAmountAndAName() {
        assertTrue(EntryValidation.validate("320", "Third Wave Coffee", false).isValid)
        assertNotNull(EntryValidation.validate("", "Cafe", false).amount)
        assertNotNull(EntryValidation.validate("0", "Cafe", false).amount)
        assertNotNull(EntryValidation.validate("0.", "Cafe", false).amount)
        assertEquals("Who did you pay?", EntryValidation.validate("320", " ", false).name)
        assertEquals("Who paid you?", EntryValidation.validate("320", "", true).name)
        assertNotNull(EntryValidation.validate("320", "x".repeat(41), false).name)
        assertNull(EntryValidation.validate("320", "x".repeat(40), false).name)
        assertFalse(EntryValidation.validate("", "", false).isValid)
    }

    @Test fun chipLabelsMapToDatasetCategories() {
        assertEquals("Food & Dining", datasetCategoryFor("Food"))
        assertEquals("Utilities & Bills", datasetCategoryFor("Bills"))
        assertEquals("Travel", datasetCategoryFor("Travel"))
        assertEquals("Shopping", datasetCategoryFor("Shopping"))
    }
}
