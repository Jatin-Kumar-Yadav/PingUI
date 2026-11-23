package com.pingpaisa.app.recognition

import java.math.BigDecimal

/**
 * What a product page / listing screenshot says about the product the user is looking at.
 * [price] is the CURRENT selling price of the selected variant, [title] the main product's title.
 */
data class ListingResult(
    val title: String?,
    val variant: String?,
    val price: PriceCandidate?,
    val candidates: List<PriceCandidate>,   // selling-price candidates, best first, one per value
    val confidence: Float,                  // 0..1
    val needsConfirmation: Boolean,         // true -> ask the user instead of trusting the pick
    val note: String?
)

/**
 * Layout-aware reading of e-commerce screenshots (and any image with several priced items).
 *
 * Real OCR drops or garbles the rupee sign on shopping pages ("￥43% 26,499 14,999", "#8,999", a bare "14,999"), so a
 * marker is a strong clue but not the only one. A number is accepted as a price only when there is evidence:
 *   - a rupee marker or a look-alike glyph glued to it, or
 *   - discount structure: "43% 26,499 14,999" (list price, then selling price), "17,999 50% off" (list price),
 *     or a bare amount directly under a card's "43% 26,499" line.
 * A bare number with none of that ("Model 18999", "500 GB", "14,269 ratings") is never a price.
 *
 * Each accepted amount is then ranked by where it sits and what surrounds it:
 *   - list/MRP/struck-through prices, EMI per month, exchange, fees, ratings and savings are excluded,
 *   - amounts inside another variant's card are demoted (the selected variant is read from "Variant: 64 GB + 4 GB"
 *     or from the title),
 *   - the price must sit under the main title (detail page) or beside it (listing card); prices above it belong to
 *     ads or variant pickers,
 *   - print size, marker strength and OCR confidence break the remaining ties.
 * Nothing here knows about any specific shop, product or position.
 */
object ListingReader {
    private enum class Marker(val weight: Double) { REAL(1.0), LOOKALIKE(0.7), STRUCT(0.3) }

    private class Tok(
        val value: BigDecimal, val raw: String, var kind: PriceKind, val marker: Marker,
        val line: OcrLine, val box: Box, val conf: Float?
    ) { var score = 0.0; var titled = false }

    private class Block(val text: String, val box: Box, val score: Double, val lineH: Float)

    private val MAX = BigDecimal(10_000_000)
    private val THOUSAND_DOT = Regex("""(?<=\d)\.(?=\d{3}(?!\d))""")
    // symbols OCR commonly prints instead of the rupee sign; letters are only believed next to a comma-grouped amount ("T7250 Processor" is a chip)
    private val LOOK_SYMBOL = Regex("""[?+±#￥¥$€]\s?$""")
    private val LOOK_LETTER = Regex("""(?<![A-Za-z0-9])[TtFfZzEe]\s?$""")
    private val MARK_CHARS = "₹₨?TtFfZzEe+±#￥¥$€ "
    // "From 29,499*", "Starting at 8,999", "Now 14,999": a price lead-in is evidence even when OCR dropped the rupee sign
    private val LEAD_IN = Regex("""(?i)(?<![A-Za-z])(?:from|starting\s*(?:at|from)?|just|only|now|at|offer\s*price|price)\s*[:*]?\s*$""")
    // "Add. 1,250 Off", "Flat 2,000 off": a discount amount, not a price (unless the line is a "43% 26,499" card line)
    private val OFF_LINE = Regex("""(?i)\boff\b|\dff\b|^\W*add\b""")
    private val AFTER_PCT = Regex("""(?i)%\s*(?:off)?\s*$""")
    private val FOLLOWED_BY_PCT = Regex("""^\s*\(?\d{1,3}\s*%""")
    private val LINE_EXCLUDE = Regex(
        """(?i)\bemi\b|per\s*month|exchange|bank\s*offers?|coupon|you\s*save|protect|promise|delivery|shipping|ratings?|reviews?|warranty|[★☆]"""
    )
    private val CARD_LABEL = Regex("""(?i)^\s*(\d{1,4})\s*(?:GB|TB)\s*\+\s*(\d{1,3})\s*(?:GB|TB)\s*$""")
    private val VARIANT_HEADER = Regex("""(?i)variant\W*(\d{1,4})\s*(?:GB|TB)\s*\+\s*(\d{1,3})\s*(?:GB|TB)""")
    private val COLOR_HEADER = Regex("""(?i)selected\s*colou?r\W*(.+)$""")
    private val TITLE_NOISE = Regex(
        """(?i)\b(?:login|sign\s*in|visit\s*(?:the\s*)?brand|selected\s*colou?r|variant|top\s*discount|buy|add\s*to|sponsored|compare|cart|wishlist|offers?|more|search|ratings?|reviews?|sale|free|delivery|in\s*stock|out\s*of\s*stock|left|emi|protect|promise|from)\b|^\s*(?:ad|home)\b|₹|%|[★☆]"""
    )
    private val AD_BADGE = Regex("""(?i)^\W*(?:ad|ads)\W*$""")
    private val RAM_KEY = Regex("""(?i)(\d{1,3})\s*GB\s*RAM""")
    private val GB_ANY = Regex("""(?i)(\d{1,4})\s*GB""")

    fun read(lines: List<OcrLine>, focus: Box? = null, isStruck: ((Box) -> Boolean)? = null): ListingResult? {
        val boxed = lines.filter { it.box != null && it.text.isNotBlank() }
        if (boxed.size < 2) return null

        val toks = boxed.flatMap { scan(it, boxed, isStruck) }
        if (toks.isEmpty()) return null

        // Ads / recommended cards: next to an "AD" or "Sponsored" badge, or (print smaller than the product's own) above the
        // product's colour/variant selector. Their title and prices must not compete with the main product's.
        val adBoxes = boxed.filter { AD_BADGE.matches(it.text.trim()) }.map { it.box!! }
        fun inAd(b: Box) = adBoxes.any { a -> b.centerY in (a.top - 20f)..(a.bottom + 60f) }
        val headerTop = boxed.filter { VARIANT_HEADER.containsMatchIn(it.text) || COLOR_HEADER.containsMatchIn(it.text) }.minOfOrNull { it.box!!.top }
        var titles = titleBlocks(boxed).filter { !inAd(it.box) }
        if (headerTop != null) {
            val ref = titles.filter { it.box.top > headerTop }.maxOfOrNull { it.lineH }
            if (ref != null) titles = titles.filter { it.box.top > headerTop || it.lineH >= 0.85f * ref }
        }
        val (selKey, variantText) = variantInfo(boxed, titles)
        val cards = boxed.mapNotNull { l -> CARD_LABEL.find(l.text)?.let { m -> Triple(l, "${m.groupValues[1]}+${m.groupValues[2]}", l.box!!) } }

        val sellers = toks.filter { it.kind != PriceKind.MRP }
        val pool = sellers.ifEmpty { toks }
        val maxH = pool.maxOf { it.line.box!!.height }.takeIf { it > 0f } ?: 1f

        for (t in pool) {
            var s = 4.0 + t.marker.weight * 1.5 + (t.conf ?: 0.7f) * 1.0
            if (t.kind == PriceKind.SELLING) s += 1.0
            if (t.kind == PriceKind.MRP) s -= 4.0
            s += 3.0 * (t.line.box!!.height / maxH)
            if (focus != null) s += if (focus.containsCenterOf(t.line.box)) 1.0 else -0.5
            if (inAd(t.line.box)) s -= 8.0
            if (headerTop != null && t.line.box.bottom < headerTop - 4f && t.line.box.height < 0.75f * maxH) s -= 8.0
            // another variant's card?
            val card = cards.filter { (_, _, cb) -> cb.bottom <= t.line.box.top + 6 && t.line.box.top - cb.bottom < 130 && kotlin.math.abs(cb.left - t.line.box.left) < 30f + cb.width * 0.5f }
                .minByOrNull { (_, _, cb) -> t.line.box.top - cb.bottom }
            if (card != null && selKey != null) s += if (card.second == selKey) 0.5 else -5.0
            // layout relation to the main title
            if (titles.isNotEmpty()) {
                val rel = titles.mapNotNull { relation(t.line.box, it) }.maxOrNull()
                if (rel != null) { s += rel; t.titled = true } else s -= 4.0
            }
            t.score = s
        }

        // one candidate per value: keep the best-scoring occurrence
        val ranked = pool.groupBy { it.value.stripTrailingZeros() }.map { (_, g) -> g.maxByOrNull { it.score }!! }
            .sortedByDescending { it.score }
        val best = ranked.first()
        val second = ranked.getOrNull(1)
        val margin = if (second == null) 5.0 else best.score - second.score

        var conf = 0.40 + best.marker.weight * 0.15 + (if (best.titled) 0.15 else 0.0) +
            kotlin.math.min(0.25, kotlin.math.max(0.0, margin) * 0.05) + ((best.conf ?: 0.7f) - 0.5f).coerceIn(-0.2f, 0.4f) * 0.1
        val onlyMrp = sellers.isEmpty()
        if (best.marker == Marker.STRUCT && !best.titled) conf = kotlin.math.min(conf, 0.5)
        if (onlyMrp) conf = kotlin.math.min(conf, 0.45)
        val ambiguous = margin < 1.0
        if (ambiguous) conf = kotlin.math.min(conf, 0.5)
        val note = when {
            onlyMrp -> "Only a list price (MRP) was readable - the selling price may be lower."
            ambiguous -> "Several prices look equally likely - please pick the selling price."
            !best.titled && titles.isNotEmpty() -> "The price isn't next to a clear product title - please check it."
            else -> null
        }

        val title = titles.mapNotNull { b -> relation(best.line.box!!, b)?.let { b to (it + 0.4 * b.score) } }
            .maxByOrNull { it.second }?.first?.text
        val cands = ranked.map { PriceCandidate(it.value.toDouble(), it.value, it.raw, it.kind, it.marker != Marker.STRUCT, it.score, it.conf, it.box) }
        return ListingResult(
            title = title?.let(::cleanTitle), variant = variantText, price = cands.first(), candidates = cands,
            confidence = conf.coerceIn(0.0, 1.0).toFloat(), needsConfirmation = ambiguous || onlyMrp || conf < PriceResult.LOW_CONFIDENCE, note = note
        )
    }

    // ---------------------------------------------------------------- amounts

    private fun scan(line: OcrLine, all: List<OcrLine>, isStruck: ((Box) -> Boolean)?): List<Tok> {
        val text = THOUSAND_DOT.replace(line.text, ",")
        if (LINE_EXCLUDE.containsMatchIn(text)) return emptyList()
        if (OFF_LINE.containsMatchIn(text) && !text.contains('%')) return emptyList()
        val box = line.box!!
        val ms = PriceExtractor.AMOUNT.findAll(text).toList()
        val out = ArrayList<Tok>()
        var prevEnd = 0
        var prev: Tok? = null
        ms.forEachIndexed { i, m ->
            val before = text.substring(0, m.range.first)
            val after = text.substring(m.range.last + 1)
            val gap = text.substring(prevEnd, m.range.first)
            prevEnd = m.range.last + 1
            val intPart = m.groupValues[1]
            val dec = m.groupValues[2]
            val value = BigDecimal(intPart.replace(",", "") + if (dec.isNotEmpty()) ".$dec" else "")
            if (value.signum() <= 0 || value > MAX) return@forEachIndexed
            if (PriceExtractor.NOT_PRICE_AFTER.containsMatchIn(after)) return@forEachIndexed

            val real = PriceExtractor.RUPEE_END.containsMatchIn(before)
            val digits = intPart.count { it.isDigit() }
            val lead = !real && digits >= 3 && LEAD_IN.containsMatchIn(before)
            val look = !real && digits >= 3 && (LOOK_SYMBOL.containsMatchIn(before) || (intPart.contains(',') && LOOK_LETTER.containsMatchIn(before)))
            val ctx = (if (real) PriceExtractor.RUPEE_END.replace(gap, "") else gap).takeLast(32)
            if (PriceExtractor.NOT_PRICE_BEFORE.containsMatchIn(LEAD_IN.replace(ctx.trimEnd(), "").trimEnd())) return@forEachIndexed

            val afterPct = AFTER_PCT.containsMatchIn(before)
            val pctOffAfter = FOLLOWED_BY_PCT.containsMatchIn(after)
            val gapBlank = gap.all { it.isWhitespace() || it in MARK_CHARS }
            val followsAmount = prev != null && gapBlank
            val loneBareAmount = text.trim().let { t -> t.replace(m.value, "").all { it.isWhitespace() || it in MARK_CHARS } }
            val marker = when {
                real -> Marker.REAL
                look || lead -> Marker.LOOKALIKE
                afterPct || pctOffAfter -> Marker.STRUCT
                followsAmount && prev!!.value > value -> Marker.STRUCT
                loneBareAmount && isCardPriceBelowDiscount(line, all) -> Marker.STRUCT
                else -> return@forEachIndexed
            }
            val prevLineTail = all.getOrNull(all.indexOf(line) - 1)?.text.orEmpty().trim().takeLast(24)
            var kind = when {
                PriceExtractor.MRP_CUE.containsMatchIn(ctx) ||
                    (ctx.isBlank() && PriceExtractor.MRP_CUE.containsMatchIn(prevLineTail)) -> PriceKind.MRP
                pctOffAfter -> PriceKind.MRP
                afterPct && !real && !look -> PriceKind.MRP
                followsAmount -> PriceKind.SELLING
                PriceExtractor.SELL_CUE.containsMatchIn(ctx) || lead -> PriceKind.SELLING
                else -> PriceKind.UNKNOWN
            }
            // approximate where this amount sits inside the line (OCR gives one box per line)
            val len = text.length.coerceAtLeast(1)
            val tokBox = Box(
                box.left + box.width * m.range.first / len, box.top,
                box.left + box.width * (m.range.last + 1) / len, box.bottom
            )
            if (kind == PriceKind.UNKNOWN && isStruck?.invoke(tokBox) == true) kind = PriceKind.MRP
            val shown = text.substring((m.range.first - 1).coerceAtLeast(0), m.range.last + 1).trim()
            val tok = Tok(value, shown, kind, marker, line, tokBox, line.confidence)
            out += tok
            prev = tok
        }
        return out
    }

    /** A bare amount on its own line, directly under a card line like "43% 26,499" (same column). */
    private fun isCardPriceBelowDiscount(line: OcrLine, all: List<OcrLine>): Boolean {
        val b = line.box!!
        return all.any { o ->
            val ob = o.box ?: return@any false
            o !== line && o.text.contains('%') && PriceExtractor.AMOUNT.containsMatchIn(o.text) &&
                ob.bottom <= b.top + 6f && b.top - ob.bottom < 1.6f * b.height && kotlin.math.abs(ob.left - b.left) < 40f
        }
    }

    // ---------------------------------------------------------------- titles

    private fun isTitleish(t: String): Boolean {
        val s = t.trim()
        val letters = s.count { it.isLetter() }
        val digits = s.count { it.isDigit() }
        return letters >= 3 && s.length <= 140 && digits <= s.length * 0.35 && !TITLE_NOISE.containsMatchIn(s) &&
            s.count { it == '/' } < 2
    }

    private fun adjacent(a: Box, b: Box): Boolean =
        kotlin.math.abs(a.left - b.left) <= 1.2f * a.height && b.top - a.bottom in -2f..(0.9f * a.height) &&
            b.height / a.height in 0.65f..1.5f

    private fun titleBlocks(all: List<OcrLine>): List<Block> {
        // chain lines down a column; unrelated text in other columns (a phone mock-up, a spec list) may sit between two
        // wrapped title lines, so continuation is searched among ALL unused lines, not just the next one in reading order
        val sorted = all.sortedWith(compareBy({ it.box!!.top }, { it.box!!.left }))
        val used = HashSet<OcrLine>()
        val blocks = ArrayList<Block>()
        for (start in sorted) {
            if (start in used || !isTitleish(start.text)) continue
            val cur = arrayListOf(start); used += start
            while (true) {
                val last = cur.last()
                val open = cur.joinToString(" ") { it.text }.let { s -> s.count { it == '(' } > s.count { it == ')' } }
                val next = sorted.filter { it !in used && adjacent(last.box!!, it.box!!) && (isTitleish(it.text) || open) }
                    .minByOrNull { it.box!!.top } ?: break
                cur += next; used += next
            }
            val text = cur.joinToString(" ") { it.text.trim() }.replace(Regex("\\s+"), " ")
            val box = Box(cur.minOf { it.box!!.left }, cur.minOf { it.box!!.top }, cur.maxOf { it.box!!.right }, cur.maxOf { it.box!!.bottom })
            val sc = titleScore(text)
            if (sc >= 2.0) blocks += Block(text, box, sc, cur.map { it.box!!.height }.average().toFloat())
        }
        return blocks.ifEmpty { headline(all) }
    }

    /** Promo banners have a plain headline ("motorola edge 70 Fusion") with no brackets or GB: take the biggest print. */
    private fun headline(all: List<OcrLine>): List<Block> {
        val l = all.filter { isTitleish(it.text) && it.text.trim().split(Regex("\\s+")).size >= 2 && it.text.count { c -> c.isLetter() } >= 6 }
            .maxByOrNull { it.box!!.height } ?: return emptyList()
        return listOf(Block(l.text.trim(), l.box!!, 2.0, l.box.height))
    }

    private fun titleScore(t: String): Double {
        var s = 0.0
        if (t.contains('(') && t.contains(')')) s += 2.5
        if (GB_ANY.containsMatchIn(t)) s += 1.5
        if (Regex("(?i)\\bRAM\\b").containsMatchIn(t)) s += 0.5
        if (t.first().isUpperCase()) s += 0.5
        if (t.split(' ').size >= 3) s += 0.5
        if (t.length >= 20) s += 0.5
        if (t.first().isDigit() || t.contains(" | ") || t.contains(" / ")) s -= 3.0
        if (t.none { it.isUpperCase() }) s -= 1.0
        return s
    }

    /** Positive = the price belongs to this title: right under it (same column) or beside it (listing card). */
    private fun relation(p: Box, t: Block): Double? {
        val tb = t.box
        val bonus = 0.4 * t.score
        val below = p.top - tb.bottom
        if (below >= -8f && below <= 450f && kotlin.math.abs(p.left - tb.left) <= kotlin.math.max(80f, 3f * p.height)) {
            return bonus + 2.0 - below / 200.0
        }
        val horiz = p.left - tb.right
        val v = p.top - tb.top
        if (horiz >= -8f && horiz <= 900f && v >= -90f && v <= 160f) {
            return bonus + 1.5 - horiz / 500.0 - kotlin.math.abs(v) / 200.0
        }
        return null
    }

    // ---------------------------------------------------------------- variant

    private fun variantInfo(all: List<OcrLine>, titles: List<Block>): Pair<String?, String?> {
        var key: String? = null
        var spec: String? = null
        for (l in all) {
            val m = VARIANT_HEADER.find(l.text) ?: continue
            key = "${m.groupValues[1]}+${m.groupValues[2]}"; spec = "${m.groupValues[1]} GB + ${m.groupValues[2]} GB"; break
        }
        if (key == null) {
            val t = titles.maxByOrNull { it.score }?.text
            val ram = t?.let { RAM_KEY.find(it) }?.groupValues?.get(1)
            val storage = t?.let { GB_ANY.findAll(it).map { m -> m.groupValues[1] }.firstOrNull { g -> g != ram } }
            if (ram != null && storage != null) { key = "$storage+$ram"; spec = "$storage GB + $ram GB" }
        }
        val color = all.firstNotNullOfOrNull { l -> COLOR_HEADER.find(l.text)?.groupValues?.get(1)?.trim()?.takeIf { it.length >= 3 } }
        val text = listOfNotNull(color, spec).joinToString(" · ").ifBlank { null }
        return key to text
    }

    private fun cleanTitle(t: String): String =
        t.replace(Regex("\\s+"), " ").replace(Regex("\\)\\s*\\("), ") (").replace(Regex("(?i)\\s*\\.{2,}\\s*more\\s*$|\\s*…\\s*more\\s*$"), "").trim()
}
