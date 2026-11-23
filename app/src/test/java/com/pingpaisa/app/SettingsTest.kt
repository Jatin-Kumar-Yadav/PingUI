package com.pingpaisa.app

import com.pingpaisa.app.security.SignUpValidation
import org.junit.Assert.*
import org.junit.Test

class SettingsTest {

    @Test fun initials() {
        assertEquals("JY", SettingsAccount.initialsOf("Janvi Yadav"))
        assertEquals("J", SettingsAccount.initialsOf("  janvi "))
        assertEquals("JK", SettingsAccount.initialsOf("Jatin Kumar Yadav"))
        assertEquals("?", SettingsAccount.initialsOf("   "))
    }

    @Test fun salaryTexts() {
        val a = SettingsAccount("Janvi", 125000.0)
        assertEquals("Monthly salary ₹1,25,000", a.salaryText)
        assertEquals("125000", a.salaryInput)
    }

    @Test fun profileUsesSignUpRules() {
        assertTrue(SignUpValidation.validateProfile("Janvi", "40000").isValid)
        assertNotNull(SignUpValidation.validateProfile(" ", "40000").name)
        assertNotNull(SignUpValidation.validateProfile("Janvi", "0").salary)
        assertNotNull(SignUpValidation.validateProfile("Janvi", "").salary)
        assertNull(SignUpValidation.validateProfile("Janvi", "").password)   // not asked here
    }

    @Test fun passwordChangeRules() {
        assertTrue(SignUpValidation.validatePasswordChange("old123", "new456", "new456").isValid)
        assertNotNull(SignUpValidation.validatePasswordChange("", "new456", "new456").current)
        assertNotNull(SignUpValidation.validatePasswordChange("old123", "abc", "abc").new)          // too short
        assertNotNull(SignUpValidation.validatePasswordChange("old123", "old123", "old123").new)    // unchanged
        assertNotNull(SignUpValidation.validatePasswordChange("old123", "new456", "new457").confirm)
    }

    @Test fun paymentCaptureText() {
        assertTrue(paymentCaptureSubtitle(true).startsWith("On"))
        assertTrue(paymentCaptureSubtitle(false).startsWith("Off"))
    }

    @Test fun appLockText() {
        assertTrue(appLockSubtitle(true).startsWith("On"))
        assertTrue(appLockSubtitle(false).startsWith("Off"))
    }

    @Test fun resetListsEverythingItDeletes() {
        val text = resetDeletes.joinToString(" ").lowercase()
        listOf("account", "password", "transactions", "budgets", "goals", "recurring", "merchants", "loan", "scanned").forEach {
            assertTrue("reset list should mention $it", text.contains(it))
        }
    }
}
