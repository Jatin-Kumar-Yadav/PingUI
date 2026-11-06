package com.pingpaisa.app

/** The icon and colour used to show a category (or an income) anywhere in the app. */
internal data class CategoryStyle(val icon: Int, val tone: Tone)

/**
 * Maps the dataset's category names to PingUI's icons and colours. Categories the
 * user typed in themselves ("Friends & Family") get a stable colour picked from
 * the name, so the same category always looks the same.
 */
internal fun categoryStyle(category: String, isIncome: Boolean = false): CategoryStyle {
    if (isIncome) return CategoryStyle(R.drawable.ic_pingpaisa_wallet, Tone.Teal)
    return when (category.trim().lowercase()) {
        "food & dining" -> CategoryStyle(R.drawable.ic_pingpaisa_food, Tone.Peach)
        "grocery" -> CategoryStyle(R.drawable.ic_pingpaisa_bag, Tone.Sage)
        "shopping" -> CategoryStyle(R.drawable.ic_pingpaisa_bag, Tone.Pink)
        "travel" -> CategoryStyle(R.drawable.ic_pingpaisa_car, Tone.Sage)
        "stay/accommodation" -> CategoryStyle(R.drawable.ic_pingpaisa_home, Tone.Teal)
        "entertainment" -> CategoryStyle(R.drawable.ic_pingpaisa_sparkle, Tone.Lavender)
        "digital subscriptions & software" -> CategoryStyle(R.drawable.ic_pingpaisa_repeat, Tone.Lavender)
        "logistics & courier" -> CategoryStyle(R.drawable.ic_pingpaisa_arrow, Tone.Cream)
        "automobile" -> CategoryStyle(R.drawable.ic_pingpaisa_car, Tone.Peach)
        "health/medical" -> CategoryStyle(R.drawable.ic_pingpaisa_shield, Tone.Pink)
        "utilities & bills" -> CategoryStyle(R.drawable.ic_pingpaisa_bill, Tone.Lavender)
        "education" -> CategoryStyle(R.drawable.ic_pingpaisa_brain, Tone.Teal)
        "financial services" -> CategoryStyle(R.drawable.ic_pingpaisa_chart, Tone.Teal)
        "personal care" -> CategoryStyle(R.drawable.ic_pingpaisa_sparkle, Tone.Pink)
        "stationary/office supplies" -> CategoryStyle(R.drawable.ic_pingpaisa_edit, Tone.Cream)
        "home services" -> CategoryStyle(R.drawable.ic_pingpaisa_home, Tone.Sage)
        "transfers" -> CategoryStyle(R.drawable.ic_pingpaisa_arrow, Tone.Teal)
        "miscellaneous", "uncategorized", "" -> CategoryStyle(R.drawable.ic_pingpaisa_wallet, Tone.Cream)
        else -> {
            val tones = listOf(Tone.Sage, Tone.Teal, Tone.Peach, Tone.Pink, Tone.Lavender)
            CategoryStyle(R.drawable.ic_pingpaisa_wallet, tones[Math.floorMod(category.trim().lowercase().hashCode(), tones.size)])
        }
    }
}
