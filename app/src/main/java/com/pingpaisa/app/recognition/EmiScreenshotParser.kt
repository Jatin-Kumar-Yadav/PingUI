package com.pingpaisa.app.recognition

import com.pingpaisa.app.finance.EmiOption
import com.pingpaisa.app.finance.EmiSource
import java.math.BigDecimal

data class EmiParseResult(val options: List<EmiOption>, val notes: List<String>)

/**
 * Reads EMI offers from OCR text of a shopping-site screenshot. Everything it returns is a CANDIDATE
 * (confirmed = false): the user checks and edits it before any calculation uses it.
 *
 * Amounts are only trusted with a rupee marker AND a label that says what they are
 * ("/month", "EMI", "processing fee", "down payment", "cashback", "total payable"...). A bare number, or a rupee amount
 * with no EMI label, is never turned into an EMI figure. A "from ₹X/m" line gives the lowest instalment only: the tenure
 * is left empty and the user is asked for it.
 */
object EmiScreenshotParser {
    private val TITLE = Regex("""(?i)\b(bank|card|finance|bajaj|hdfc|icici|axis|sbi|kotak|idfc|rbl|amex|citi|hsbc|onecard|federal|indusind|yes bank|au small|paytm|simpl|zest|pay\s*later|credit|debit|nbfc|capital|fincorp|lender)\b""")
    private val MONTH_AFTER = Regex("""(?i)^\s*(?:/\s*(?:m|mo|mth|month)\b|per\s*month|p\.?\s?m\b|a\s*month|monthly|(?:x|×)\s*\d{1,3}\b|for\s*\d{1,3}\s*(?:months?|mos?)\b)""")
    private val BEFORE_EMI = Regex("""(?i)(?:emi|instal+ments?|installments?|monthly|per\s*month)\W{0,6}(?:of|is|:|-|@)?\W{0,3}$""")
    private val DOWN = Regex("""(?i)(?:down\s*payment|downpayment|initial\s*payment|pay\s*now|upfront|up-front)\W{0,6}(?:of|is|:|-)?\W{0,3}$""")
    private val FEE = Regex("""(?i)(?:processing|convenience|handling|loan|service|other|one[\s-]*time|booking)?\s*(?:fees?|charges?)\W{0,6}(?:of|is|:|-|\+)?\W{0,3}$""")
    private val CASHBACK = Regex("""(?i)(?:cash\s*back|instant\s*discount|discount|savings?|you\s*save|save|subvention|interest\s*subsid\w*|you\s*get|extra\s*off|off)\W{0,6}(?:of|is|:|-|upto|up\s*to)?\W{0,3}$""")
    private val TOTAL = Regex("""(?i)(?:total|payable|you\s*pay|overall|grand\s*total)(?:\s*(?:amount|payable|cost|repayment|payment))*\W{0,6}(?:of|is|:|-)?\W{0,3}$""")
    private val CASH_AFTER = Regex("""(?i)^\s*(?:off\b|cash\s*back|instant\s*discount|discount\b)""")
    private val INTEREST_AMOUNT = Regex("""(?i)interest\W{0,6}(?:of|is|:|-|charged)?\W{0,3}$""")
    private val FROM_CUE = Regex("""(?i)\b(?:from|starting|starts?\s*at|as\s*low\s*as|lowest)\b""")
    private val NO_COST = Regex("""(?i)no[\s-]*cost\s*emi|0\s*%\s*interest|zero\s*(?:cost|interest)|interest[\s-]*free""")
    private val TENURE = listOf(
        Regex("""(?i)(?<![/\d])(\d{1,3})\s*(?:months?|mos?\b\.?|mths?)\b"""),
        Regex("""(?i)(?<![/\d])(\d{1,3})\s*emis\b"""),
        Regex("""(?i)tenure\W{0,4}(\d{1,3})\b"""),
        Regex("""(?i)(?:x|×)\s*(\d{1,3})\b""")
    )
    private val TENURE_LIST = Regex("""(?i)\d{1,3}\s*(?:,|/|&|\bor\b|\band\b|-|to)\s*\d{1,3}(?:\s*(?:,|/|&|\bor\b|\band\b)\s*\d{1,3})*\s*(?:months?|mos?\b|mths?)""")
    private val YEARS = Regex("""(?i)(?<![/\d])(\d{1,2})\s*(?:years?|yrs?)\b""")
    private val RATE_AFTER = Regex("""(?i)(\d{1,2}(?:\.\d{1,2})?)\s*%\s*(?:p\.?\s?a\.?|per\s*annum|interest|rate|roi|yearly|/\s*yr|a\s*year)""")
    private val RATE_BEFORE = Regex("""(?i)(?:interest(?:\s*rate)?|rate|roi|@)\W{0,4}(\d{1,2}(?:\.\d{1,2})?)\s*%""")

    private class Block(var title: String?) { val lines = ArrayList<OcrLine>(); fun hasAmount() = lines.any { RUPEE_ANY.containsMatchIn(it.text) } }
    private val RUPEE_ANY = Regex("""(?i)(?:₹|₨|(?<![a-z])rs\.?|(?<![a-z])inr\.?)\s*\d""")

    private class Amt(val value: BigDecimal, val before: String, val after: String)

    fun parse(lines: List<OcrLine>, price: BigDecimal? = null): EmiParseResult {
        val clean = lines.filter { it.text.isNotBlank() }
        if (clean.isEmpty()) return EmiParseResult(emptyList(), listOf("No readable text in that image. Try a sharper, closer screenshot or enter the EMI yourself."))
        val avgConf = clean.mapNotNull { it.confidence }.average().takeIf { !it.isNaN() }

        val blocks = split(clean)
        val options = ArrayList<EmiOption>()
        blocks.forEach { options += optionsFrom(it, price) }
        // identical offers read twice (e.g. scrolled screenshots)
        val unique = options.distinctBy { Triple(it.lender.lowercase(), it.monthlyEmi?.stripTrailingZeros(), it.tenureMonths) }

        val notes = ArrayList<String>()
        if (unique.isEmpty()) {
            notes += "I couldn't find EMI offers in that image (no rupee amount labelled as an EMI or per-month). Add them manually, or upload a clearer screenshot of the EMI plans."
        } else {
            if (unique.any { it.tenureMonths == null }) notes += "Some offers show only the lowest monthly EMI (\"from ₹X/m\"), so their tenure is missing. Open the offer on the site for the tenure, or enter it yourself."
            if (unique.any { it.monthlyEmi == null }) notes += "Some offers have no monthly amount - enter it."
            notes += "These are read from a screenshot and may be wrong. Check each one, then confirm it."
        }
        if (avgConf != null && avgConf < 0.5) notes += "The text looks blurry (low OCR confidence), so double-check every number."
        return EmiParseResult(unique, notes)
    }

    // ---- splitting the screenshot into one block per offer ----
    private fun split(lines: List<OcrLine>): List<Block> {
        val blocks = ArrayList<Block>()
        var cur = Block(null)
        val heights = lines.mapNotNull { it.box?.height }.sorted()
        val medianH = heights.getOrNull(heights.size / 2) ?: 0f
        var prev: OcrLine? = null
        for (l in lines) {
            val t = l.text.trim()
            val isTitle = looksLikeTitle(t)
            val pb = prev?.box; val lb = l.box
            val gap = if (pb != null && lb != null && medianH > 0f) lb.top - pb.bottom else 0f
            val bigGap = gap > medianH * 2.2f
            if (isTitle || (bigGap && cur.hasAmount())) {
                if (cur.hasAmount()) { blocks += cur; cur = Block(if (isTitle) t else null) }
                else { cur = Block(if (isTitle) t else cur.title) }       // nothing useful yet: restart with the newer title
                if (!isTitle) cur.lines += l
            } else cur.lines += l
            prev = l
        }
        if (cur.hasAmount()) blocks += cur
        return blocks
    }

    private fun looksLikeTitle(t: String): Boolean =
        t.length in 4..60 && TITLE.containsMatchIn(t) && t.none { it.isDigit() } && !t.contains('₹') && !Regex("""(?i)^\s*(?:no[\s-]*cost\s*emi|emi)\b""").containsMatchIn(t)

    // ---- reading one block ----
    private fun optionsFrom(b: Block, price: BigDecimal?): List<EmiOption> {
        val blockText = b.lines.joinToString(" ") { it.text }
        val noCost = NO_COST.containsMatchIn(blockText)
        var down = BigDecimal.ZERO; var fee = BigDecimal.ZERO; var cash = BigDecimal.ZERO
        var total: BigDecimal? = null
        var rateBlock: BigDecimal? = null
        val rows = ArrayList<Pair<BigDecimal, Int?>>()      // monthly amount, tenure from the same line
        var from = false

        for (l in b.lines) {
            val amts = amounts(l.text)
            val lineTenure = tenureIn(l.text)
            val lineRate = rateIn(l.text)
            if (lineRate != null && rateBlock == null) rateBlock = lineRate
            var monthlyOnLine: BigDecimal? = null
            for (a in amts) {
                val before = a.before.takeLast(34)
                when {
                    DOWN.containsMatchIn(before) -> down = a.value
                    TOTAL.containsMatchIn(before) -> if (total == null) total = a.value
                    INTEREST_AMOUNT.containsMatchIn(before) -> {}
                    FEE.containsMatchIn(before) && !MONTH_AFTER.containsMatchIn(a.after) -> fee += a.value
                    MONTH_AFTER.containsMatchIn(a.after) || BEFORE_EMI.containsMatchIn(before) -> {
                        if (monthlyOnLine == null) monthlyOnLine = a.value
                        if (FROM_CUE.containsMatchIn(before) && lineTenure == null) from = true
                    }
                    (CASHBACK.containsMatchIn(before) || CASH_AFTER.containsMatchIn(a.after)) && !a.after.trimStart().startsWith("%") -> cash += a.value
                }
            }
            if (monthlyOnLine != null) rows += monthlyOnLine to lineTenure
        }

        val title = b.title
        val evidence = (listOfNotNull(title) + b.lines.map { it.text }).joinToString(" | ").take(220)
        fun make(m: BigDecimal?, n: Int?, only: Boolean, fromLowest: Boolean) = EmiOption(
            lender = title.orEmpty(), monthlyEmi = m, tenureMonths = n, annualRatePct = rateBlock,
            downPayment = down, processingFee = fee, cashback = cash, totalPayable = if (only) total else null,
            noCostAdvertised = noCost, source = EmiSource.SCREENSHOT, confirmed = false, quotedForPrice = price,
            fromLowest = fromLowest, evidence = evidence
        )

        val withTenure = rows.filter { it.second != null }
        return when {
            withTenure.isNotEmpty() -> withTenure.map { (m, n) -> make(m, n, withTenure.size == 1, false) }
            rows.isNotEmpty() -> {
                // one offer: the instalment is known, the tenure maybe stated elsewhere in the block
                val n = tenureIn(blockText)
                listOf(make(rows.first().first, n, true, from && n == null))
            }
            total != null || tenureIn(blockText) != null && noCost -> {
                val n = tenureIn(blockText)
                if (n == null && total == null) emptyList() else listOf(make(null, n, true, false))
            }
            else -> emptyList()
        }
    }

    /** Rupee amounts with the words immediately before and after each one. */
    private fun amounts(text: String): List<Amt> {
        val out = ArrayList<Amt>()
        var segStart = 0
        for (m in PriceExtractor.AMOUNT.findAll(text)) {
            val before = text.substring(segStart, m.range.first)
            val marker = PriceExtractor.RUPEE_END.find(text.substring(0, m.range.first))
            segStart = m.range.last + 1
            if (marker == null) continue                       // no rupee marker: not a money amount
            val dec = m.groupValues[2]
            val v = BigDecimal(m.groupValues[1].replace(",", "") + if (dec.isNotEmpty()) ".$dec" else "")
            if (v.signum() <= 0) continue
            out += Amt(v, PriceExtractor.RUPEE_END.replace(before, ""), text.substring(m.range.last + 1))
        }
        return out
    }

    private fun tenureIn(text: String): Int? {
        if (TENURE_LIST.containsMatchIn(text)) return null      // "3, 6, 9 months": which one? ask
        val found = LinkedHashSet<Int>()
        for (re in TENURE) re.findAll(text).forEach { found += it.groupValues[1].toInt() }
        YEARS.findAll(text).forEach { found += it.groupValues[1].toInt() * 12 }
        val ok = found.filter { it in 1..EMI_MAX }
        return if (ok.size == 1) ok.first() else null          // a list like "3, 6, 9 months" is ambiguous: ask
    }

    private fun rateIn(text: String): BigDecimal? {
        val m = RATE_AFTER.find(text)?.groupValues?.get(1) ?: RATE_BEFORE.find(text)?.groupValues?.get(1) ?: return null
        return m.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 && it <= BigDecimal(100) }
    }

    private const val EMI_MAX = 120
}
