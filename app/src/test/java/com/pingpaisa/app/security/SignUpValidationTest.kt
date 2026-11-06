package com.pingpaisa.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignUpValidationTest {

    private fun check(
        name: String = "Aanya Mehta", salary: String = "40000",
        password: String = "paisa123", confirm: String = password
    ) = SignUpValidation.validate(name, salary, password, confirm)

    @Test fun validFormHasNoErrors() {
        val errors = check()
        assertTrue(errors.isValid)
        assertNull(errors.name); assertNull(errors.salary); assertNull(errors.password); assertNull(errors.confirm)
    }

    @Test fun nameIsRequired() {
        assertNotNull(check(name = "").name)
        assertNotNull(check(name = "   ").name)
        assertFalse(check(name = "").isValid)
    }

    @Test fun nameCannotBeTooLong() {
        assertNotNull(check(name = "A".repeat(41)).name)
        assertNull(check(name = "A".repeat(40)).name)
    }

    @Test fun salaryMustBeMoreThanZero() {
        assertNotNull(check(salary = "").salary)
        assertNotNull(check(salary = "0").salary)
        assertNotNull(check(salary = "000").salary)
        assertNull(check(salary = "1").salary)
        assertNull(check(salary = "85000").salary)
    }

    @Test fun passwordNeedsSixCharacters() {
        assertNotNull(check(password = "12345", confirm = "12345").password)
        assertNull(check(password = "123456", confirm = "123456").password)
    }

    @Test fun confirmationMustMatchExactly() {
        assertEquals("Passwords don't match", check(password = "paisa123", confirm = "paisa124").confirm)
        assertNotNull(check(password = "paisa123", confirm = "").confirm)
        assertNotNull(check(password = "paisa123", confirm = "Paisa123").confirm)   // case matters
        assertNull(check(password = "paisa123", confirm = "paisa123").confirm)
    }

    @Test fun everyBrokenFieldIsReportedAtOnce() {
        val errors = check(name = "", salary = "", password = "123", confirm = "9")
        assertNotNull(errors.name); assertNotNull(errors.salary)
        assertNotNull(errors.password); assertNotNull(errors.confirm)
    }
}
