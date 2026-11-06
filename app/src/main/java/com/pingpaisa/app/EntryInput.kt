package com.pingpaisa.app

/**
 * Rules for the Add an expense screen's keypad and form. Plain Kotlin so they
 * can be unit-tested. The typed amount is kept as raw text ("1250.5"); it is
 * only grouped for display ("1,250.5").
 */
internal object AmountInput {
    const val MAX_WHOLE_DIGITS = 9     // up to ₹99,99,99,999
    const val MAX_DECIMALS = 2

    /** The amount text after pressing [key] ("0"–"9", "." or "⌫"). */
    fun applyKey(current: String, key: String): String = when (key) {
        "⌫" -> current.dropLast(1)
        "." -> when {
            current.contains('.') -> current
            current.isEmpty() -> "0."
            else -> "$current."
        }
        else -> {
            val dot = current.indexOf('.')
            when {
                key.length != 1 || !key[0].isDigit() -> current
                dot >= 0 && current.length - dot - 1 >= MAX_DECIMALS -> current
                dot < 0 && current == "0" -> key                      // "0" then "5" is "5", not "05"
                dot < 0 && current.length >= MAX_WHOLE_DIGITS -> current
                else -> current + key
            }
        }
    }

    /** "1250.5" -> "1,250.5" with Indian grouping; empty shows "0". */
    fun display(raw: String): String {
        if (raw.isEmpty()) return "0"
        val whole = raw.substringBefore('.')
        val decimals = if (raw.contains('.')) "." + raw.substringAfter('.') else ""
        val grouped = if (whole.length <= 3) whole
        else whole.dropLast(3).reversed().chunked(2).joinToString(",").reversed() + "," + whole.takeLast(3)
        return grouped + decimals
    }
}

internal data class EntryErrors(val amount: String? = null, val name: String? = null) {
    val isValid: Boolean get() = amount == null && name == null
}

internal object EntryValidation {
    const val MAX_NAME_LENGTH = 40

    fun validate(amountRaw: String, name: String, isIncome: Boolean): EntryErrors = EntryErrors(
        amount = if ((amountRaw.toDoubleOrNull() ?: 0.0) > 0.0) null else "Enter an amount above ₹0",
        name = when {
            name.isBlank() -> if (isIncome) "Who paid you?" else "Who did you pay?"
            name.trim().length > MAX_NAME_LENGTH -> "Keep it under $MAX_NAME_LENGTH characters"
            else -> null
        }
    )
}

/**
 * The quick category chips use the design's short labels; saved transactions use the
 * dataset's category names, so the rest of the app (styles, budgets, the score) matches.
 */
internal fun datasetCategoryFor(chipLabel: String): String = when (chipLabel) {
    "Food" -> "Food & Dining"
    "Bills" -> "Utilities & Bills"
    else -> chipLabel          // "Travel" and "Shopping" already match
}
