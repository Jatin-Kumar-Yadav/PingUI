package com.pingpaisa.app.security

/** One message per field; null means that field is fine. */
data class SignUpErrors(
    val name: String? = null,
    val salary: String? = null,
    val password: String? = null,
    val confirm: String? = null
) {
    val isValid: Boolean get() = name == null && salary == null && password == null && confirm == null
}

/**
 * Rules for creating an account. Kept as plain Kotlin (no Android) so they can
 * be unit-tested and reused by the "edit profile" and "change password"
 * screens in Settings.
 */
object SignUpValidation {

    const val MIN_PASSWORD_LENGTH = 6
    const val MAX_NAME_LENGTH = 40

    fun validate(name: String, salary: String, password: String, confirm: String): SignUpErrors =
        SignUpErrors(
            name = validateName(name),
            salary = validateSalary(salary),
            password = validatePassword(password),
            confirm = validateConfirm(password, confirm)
        )

    fun validateName(name: String): String? = when {
        name.isBlank() -> "Please enter your name"
        name.trim().length > MAX_NAME_LENGTH -> "Keep your name under $MAX_NAME_LENGTH characters"
        else -> null
    }

    /** [salary] is the typed text: digits only, in rupees per month. */
    fun validateSalary(salary: String): String? {
        if (salary.isBlank()) return "Enter your monthly salary"
        val value = salary.toDoubleOrNull()
        return if (value == null || value <= 0.0) "Salary must be more than ₹0" else null
    }

    fun validatePassword(password: String): String? =
        if (password.length < MIN_PASSWORD_LENGTH) "Use at least $MIN_PASSWORD_LENGTH characters" else null

    fun validateConfirm(password: String, confirm: String): String? =
        if (confirm != password) "Passwords don't match" else null

    /** Settings → Edit profile: the same name and salary rules as sign-up. */
    fun validateProfile(name: String, salary: String): SignUpErrors =
        SignUpErrors(name = validateName(name), salary = validateSalary(salary))

    /** Settings → Change password. The current password is checked against the stored hash separately. */
    fun validatePasswordChange(current: String, new: String, confirm: String): PasswordChangeErrors = PasswordChangeErrors(
        current = if (current.isEmpty()) "Enter your current password" else null,
        new = validatePassword(new) ?: if (current.isNotEmpty() && new == current) "Choose a password different from the current one" else null,
        confirm = validateConfirm(new, confirm)
    )
}

/** One message per field of the change-password sheet; null means that field is fine. */
data class PasswordChangeErrors(val current: String? = null, val new: String? = null, val confirm: String? = null) {
    val isValid: Boolean get() = current == null && new == null && confirm == null
}
