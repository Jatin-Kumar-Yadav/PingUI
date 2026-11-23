package com.pingpaisa.app.recognition

import kotlin.math.max
import kotlin.math.min

/** Plain geometry (no android.graphics) so the whole OCR-parsing layer is JVM-testable. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    fun containsCenterOf(other: Box): Boolean =
        other.centerX in left..right && other.centerY in top..bottom
}

data class OcrLine(val text: String, val box: Box? = null, val confidence: Float? = null)

enum class PriceKind { SELLING, MRP, UNKNOWN }

/**
 * One rupee-marked amount found in the text.
 * [value] is the numeric form (for the existing Double-based model), [amount] the exact decimal, and
 * [raw] the text exactly as OCR read it (e.g. "₹ 18,999") for display.
 */
data class PriceCandidate(
    val value: Double,
    val amount: java.math.BigDecimal,
    val raw: String,
    val kind: PriceKind,
    val hasCurrencyMarker: Boolean,
    val score: Double,
    val ocrConfidence: Float?,
    val box: Box?
)

data class PriceResult(
    val best: PriceCandidate?,
    val candidates: List<PriceCandidate>,   // best first, de-duplicated by value
    val confidence: Float,                  // 0..1, 0 when nothing was found
    val note: String?,
    /** True when the app must NOT pick a price on its own: ask the user (candidates are offered as choices). */
    val needsConfirmation: Boolean = false
) {
    val isLowConfidence: Boolean get() = best != null && (needsConfirmation || confidence < LOW_CONFIDENCE)
    /** The one price that can be used without asking, or null. */
    val confident: PriceCandidate? get() = best?.takeIf { !needsConfirmation && confidence >= LOW_CONFIDENCE }
    companion object {
        const val LOW_CONFIDENCE = 0.6f
        val NONE = PriceResult(null, emptyList(), 0f, null)
    }
}

/**
 * Finds the selling price in OCR text. THE RULE: a number is a price only when a rupee marker
 * (₹, ₨, Rs, Rs., INR, Rupees) sits directly in front of it (spaces allowed). Bare numbers, "Model 18999",
 * "500 GB", "20% off", "Price 499" and "18,999/-" are never prices.
 *
 * Among the rupee amounts it then separates the selling price from MRP / "was" / list prices and from
 * savings, discounts, cashback, EMI instalments, fees, down payments and exchange offers - by the words
 * around each amount, not by size alone. Prominence (print height) and the aiming box only break ties.
 * When it still cannot tell (two plain amounts, only an MRP, ...) [PriceResult.needsConfirmation] is set
 * and the caller asks the user instead of guessing.
 */
object PriceExtractor {
    // digits with optional Indian/western grouping and optional decimals; not glued to other digits.
    // A comma that merely follows the amount ("MRP ₹25,999, Offer ₹18,999") must not break the match.
    internal val AMOUNT = Regex("""(?<!\d)(?<!\d,)(\d{1,3}(?:,\d{2,3})+|\d+)(?:\.(\d{1,2}))?(?!\d)(?!,\d)""")
    /** Rupee notation immediately before the amount. */
    internal val RUPEE_END = Regex("""(?i)(?:₹|₨|(?<![a-z])rs\.?|(?<![a-z])inr\.?|(?<![a-z])rupees?)\s*$""")
    internal val MRP_CUE = Regex("""(?i)\bm\.?\s?r\.?\s?p\b|\bmax(?:imum)?\.?\s*retail|\blist\s*price|\bwas\b|\boriginal\s*price|\bregular\s*price|\bstrike|\bold\s*price|\bactual\s*price""")
    internal val SELL_CUE = Regex("""(?i)\b(?:offer|deal|sale|special|our|selling|now|today'?s?|buy\s*(?:at|for)|price|pay|only|at)\b""")
    /** Words in front of an amount that make it something other than the product price. */
    internal val NOT_PRICE_BEFORE = Regex(
        """(?i)\b(?:save|saving|savings|saved|discount|cashback|cash\s*back|coupon|emi|instal+ments?|installments?|exchange|bank\s*offers?|extra|additional|interest|fees?|charges?|processing|down\s*payment|downpayment|delivery|shipping|handling|convenience|gst|tax(?:es)?|off|subvention|subsidy|reward|points?|wallet|gift\s*card|up\s*to|upto|flat|get|earn|from|starting|starts?)\W*(?:of|at|:|-|upto|up\s*to)?\W*$"""
    )
    internal val NOT_PRICE_AFTER = Regex(
        """(?i)^\s*(?:%|percent\b|off\b|cashback\b|discount\b|savings?\b|saved\b|extra\b|emi\b|/\s*(?:m\b|mo\b|mth\b|month)|per\s*month\b|p\.?\s?m\b|a\s*month\b|monthly\b|x\s*\d|×\s*\d|for\s*\d+\s*(?:months?|mos?|m\b)|\d+\s*(?:months?|mos?)\b|gb\b|tb\b|mb\b|kg\b|mah\b|w\b|ml\b|l\b|mp\b|hz\b|inch\b|cm\b|mm\b|nits\b|\+(?!\d))"""
    )
    internal val LINE_NOT_PRICE = Regex("""(?i)\bno[\s-]*cost\s*emi\b|\bemi\b|\bper\s*month\b|\bbank\s*offer|\bexchange\b|\bcoupon\b|\byou\s*save\b""")

    /**
     * OCR often reads the ₹ glyph as a look-alike character. Used ONLY when no real rupee marker was found anywhere:
     * the character must touch a 3+ digit amount and not be part of a word. Such prices are always flagged "check this".
     */
    internal val LOOKALIKE_END = Regex("""(?<![A-Za-z0-9])[?TtFfZzEe\u0930]$""")

    private class Raw(
        val value: java.math.BigDecimal, val raw: String, val kind: PriceKind, val base: Double,
        val conf: Float?, val box: Box?, val inferred: Boolean = false
    )

    fun extract(lines: List<OcrLine>, focus: Box? = null): PriceResult {
        var raws = collect(lines, inferred = false)
        val inferredOnly = raws.isEmpty()
        if (inferredOnly) raws = collect(lines, inferred = true)
        if (raws.isEmpty()) return PriceResult.NONE

        val maxHeight = raws.mapNotNull { it.box?.height }.maxOrNull()?.takeIf { it > 0f }
        val scored = raws.map { r ->
            var s = r.base
            if (r.box != null && maxHeight != null) s += 2.0 * (r.box.height / maxHeight)
            if (focus != null && r.box != null) s += if (focus.containsCenterOf(r.box)) 1.0 else -0.5
            r to s
        }
        val mrpValues = scored.filter { it.first.kind == PriceKind.MRP }.map { it.first.value }
        // A rupee amount that is lower than a labelled MRP/"was" price and is not itself labelled MRP is the selling price.
        val adjusted = scored.map { (r, s) ->
            val demote = r.kind == PriceKind.MRP && scored.any { (o, _) ->
                o.kind != PriceKind.MRP && mrpValues.any { m -> o.value < m }
            }
            val promote = r.kind != PriceKind.MRP && mrpValues.any { m -> r.value < m }
            r to (s + (if (demote) -3.0 else 0.0) + (if (promote) 1.0 else 0.0))
        }
        val candidates = adjusted
            .groupBy { it.first.value.stripTrailingZeros() }
            .map { (_, group) -> group.maxByOrNull { it.second }!! }
            .sortedByDescending { it.second }
            .map { (r, s) -> PriceCandidate(r.value.toDouble(), r.value, r.raw, r.kind, !r.inferred, s, r.conf, r.box) }

        val best = candidates.first()
        val second = candidates.getOrNull(1)
        val margin = if (second == null) 5.0 else best.score - second.score
        var conf = 0.45 + 0.25 * (best.ocrConfidence ?: 0.7f) + min(0.25, max(0.0, margin) * 0.06)
        val onlyMrp = candidates.all { it.kind == PriceKind.MRP }
        val ambiguous = second != null && margin < 1.0
        if (onlyMrp || ambiguous || inferredOnly) conf = min(conf, 0.5)
        val note = when {
            inferredOnly -> "The ₹ sign wasn't clearly read - please check this price."
            onlyMrp -> "Only an MRP / list price was found - the selling price may be lower."
            ambiguous -> "Several rupee prices were found and the selling price isn't clear."
            else -> null
        }
        return PriceResult(best, candidates, conf.coerceIn(0.0, 1.0).toFloat(), note, needsConfirmation = onlyMrp || ambiguous || inferredOnly)
    }

    private fun collect(lines: List<OcrLine>, inferred: Boolean): ArrayList<Raw> {
        val raws = ArrayList<Raw>()
        lines.forEachIndexed { index, line ->
            val prev = lines.getOrNull(index - 1)?.text.orEmpty()
            var segStart = 0
            AMOUNT.findAll(line.text).toList().forEach { m ->
                val segment = line.text.substring(segStart, m.range.first)
                evaluate(line, prev, segment, m, inferred)?.let { raws += it }
                segStart = m.range.last + 1
            }
        }
        return raws
    }

    private fun evaluate(line: OcrLine, prevLine: String, segment: String, m: MatchResult, inferred: Boolean = false): Raw? {
        val text = line.text
        val dec = m.groupValues[2]
        val intPart = m.groupValues[1]
        val value = java.math.BigDecimal(intPart.replace(",", "") + if (dec.isNotEmpty()) ".$dec" else "")
        val before = text.substring(0, m.range.first)
        val after = text.substring(m.range.last + 1)

        // 1. The rupee marker is mandatory and must touch the number.
        val markerRegex = if (inferred) LOOKALIKE_END else RUPEE_END
        val marker = markerRegex.find(before) ?: return null
        if (inferred && intPart.replace(",", "").length < 3) return null
        if (value.signum() <= 0 || value > java.math.BigDecimal(10_000_000)) return null

        // 2. What kind of money is this?
        // text between the previous amount and this one, minus the rupee marker itself
        val context = markerRegex.replace(segment, "").takeLast(32)
        if (NOT_PRICE_AFTER.containsMatchIn(after)) return null
        if (NOT_PRICE_BEFORE.containsMatchIn(context.trimEnd())) return null
        // amount at the start of a line: the label may be on the previous line ("EMI from" / "You save")
        val prevTail = prevLine.trim().takeLast(24)
        if (context.isBlank() && prevTail.isNotBlank() && NOT_PRICE_BEFORE.containsMatchIn(prevTail)) return null
        if (context.isBlank() && LINE_NOT_PRICE.containsMatchIn(prevTail)) return null
        if (LINE_NOT_PRICE.containsMatchIn(context)) return null

        val prevEndsWithMrp = context.isBlank() && MRP_CUE.containsMatchIn(prevTail)
        val kind = when {
            MRP_CUE.containsMatchIn(context) || prevEndsWithMrp -> PriceKind.MRP
            SELL_CUE.containsMatchIn(context) -> PriceKind.SELLING
            else -> PriceKind.UNKNOWN
        }
        val shown = text.substring(marker.range.first, m.range.last + 1).trim()
        var base = 4.0
        if (kind == PriceKind.SELLING) base += 1.0
        if (kind == PriceKind.MRP) base -= 0.5
        if (intPart.contains(',')) base += 0.3
        base += (line.confidence ?: 0.7f).toDouble()
        return Raw(value, shown, kind, base, line.confidence, line.box, inferred)
    }
}
