package com.pingpaisa.app

import com.pingpaisa.app.data.MerchantOverride
import com.pingpaisa.app.data.Transaction
import com.pingpaisa.app.data.isIncome

/** One remembered merchant. [key] is how it is stored (lowercase); [name] is how the payments spell it. */
internal data class MerchantRow(val key: String, val name: String, val category: String, val payments: Int)

/** One category in use. [custom] = typed in by the user, not from the built-in list; only those can be renamed or removed. */
internal data class CategoryRow(val name: String, val payments: Int, val custom: Boolean)

/** Settings → Merchants and Categories (step 54): the lists and the row subtitles. Plain Kotlin, unit-tested. */
internal object ManageUi {

    fun merchants(overrides: List<MerchantOverride>, all: List<Transaction>): List<MerchantRow> {
        val byKey = all.filter { !it.isIncome }.groupBy { it.recipient.trim().lowercase() }
        return overrides.map { o ->
            val payments = byKey[o.recipient].orEmpty()
            // Newest payment's spelling ("Swiggy"), else the stored name with capitals ("swiggy" → "Swiggy").
            val name = payments.firstOrNull()?.recipient?.trim()
                ?: o.recipient.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
            MerchantRow(o.recipient, name, o.category, payments.size)
        }.sortedBy { it.name.lowercase() }
    }

    /**
     * Every category that payments or remembered merchants use (never "Income"), with its number of
     * payments; most used first. Built-in means in the dataset's list or "Uncategorized".
     */
    fun categories(all: List<Transaction>, overrides: List<MerchantOverride>, builtIn: List<String>): List<CategoryRow> {
        val counts = all.filter { !it.isIncome }.groupingBy { it.category.trim() }.eachCount()
        val known = (builtIn + "Uncategorized").map { it.lowercase() }.toSet()
        return (counts.keys + overrides.map { it.category.trim() })
            .filter { it.isNotEmpty() && !it.equals("Income", ignoreCase = true) }
            .distinct()
            .map { CategoryRow(it, counts[it] ?: 0, it.lowercase() !in known) }
            .sortedWith(compareByDescending<CategoryRow> { it.payments }.thenBy { it.name.lowercase() })
    }

    fun merchantsSubtitle(count: Int): String = when (count) {
        0 -> "None yet · set one from a payment's details"
        1 -> "1 remembered merchant"
        else -> "$count remembered merchants"
    }

    fun categoriesSubtitle(rows: List<CategoryRow>): String {
        val custom = rows.count { it.custom }
        val used = "${rows.size} in use"
        return when (custom) {
            0 -> "$used · no custom categories"
            1 -> "$used · 1 custom category"
            else -> "$used · $custom custom categories"
        }
    }

    /** Rename check: the picker's rules, plus "not the same name". Null when fine. */
    fun renameError(old: String, new: String): String? =
        customCategoryError(new) ?: if (new.trim() == old) "That's already its name" else null
}
