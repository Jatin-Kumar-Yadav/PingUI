package com.pingpaisa.app.data

import android.content.Context
import com.pingpaisa.app.recognition.OcrLine
import com.pingpaisa.app.recognition.PriceExtractor
import org.json.JSONObject
import java.math.BigDecimal
import java.security.MessageDigest

/**
 * Small, private on-device feedback model. It learns which OCR word contexts tend to surround
 * the amount the user confirmed. Images and raw OCR text are never persisted by this model.
 */
class PriceLearningStore(context: Context) {
    private val prefs = context.getSharedPreferences("price_extraction_learning", Context.MODE_PRIVATE)
    private val lock = Any()

    fun weights(): Map<String, Double> = synchronized(lock) {
        val stored = runCatching { JSONObject(prefs.getString("features", "{}") ?: "{}") }.getOrNull()
            ?: return@synchronized emptyMap()
        stored.keys().asSequence().filter { it.removePrefix("word:") in FEATURES }.associateWith { key ->
            val row = stored.optJSONObject(key) ?: JSONObject()
            val positive = row.optInt("positive", 0)
            val negative = row.optInt("negative", 0)
            ((positive - negative).toDouble() / (positive + negative + 3)).coerceIn(-1.0, 1.0)
        }
    }

    /**
     * One confirmation per image. Correct OCR reads earn a positive signal. A correction supplies
     * a negative example for the rejected OCR choice and a positive example only when the chosen
     * amount itself can be located in OCR, so a price typed from memory is never misattributed.
     */
    fun record(lines: List<OcrLine>, predicted: Double?, chosen: Double, imageHash: String? = null): Boolean = synchronized(lock) {
        val stableId = imageHash?.let(::digest)
        val priorEvents = runCatching {
            org.json.JSONArray(prefs.getString("events", "[]") ?: "[]").let { array ->
                (0 until array.length()).map { array.getString(it) }.toSet()
            }
        }.getOrDefault(emptySet())
        if (stableId != null && stableId in priorEvents) return@synchronized false
        val selected = sampleFor(lines, chosen)
        val corrected = predicted != null && kotlin.math.abs(predicted - chosen) > 0.009
        val rejected = predicted?.takeIf { corrected }?.let { sampleFor(lines, it) }
        if (selected == null && rejected == null) return@synchronized false
        // Correct reads are positive evidence for the accepted amount's surrounding price cues.
        // A correction only counts as a complete labelled pair when both amounts are in OCR.
        val acceptedFeatures = selected ?: if (!corrected) rejected else null
        if (acceptedFeatures == null && rejected == null) return@synchronized false
        val stored = runCatching { JSONObject(prefs.getString("features", "{}") ?: "{}") }.getOrNull()
            ?: JSONObject()
        // Older builds stored arbitrary OCR words. Remove them during migration so product names
        // are neither kept as learner data nor allowed to bias future scans.
        stored.keys().asSequence().filterNot { it.removePrefix("word:") in FEATURES }
            .toList().forEach { stored.remove(it) }
        fun applySample(features: Set<String>, field: String) {
            features.forEach { key ->
                val row = stored.optJSONObject(key) ?: JSONObject()
                row.put(field, (row.optInt(field, 0) + 1).coerceAtMost(100))
                stored.put(key, row)
            }
        }
        rejected?.let { applySample(it, "negative") }
        acceptedFeatures?.let { applySample(it, "positive") }
        val eventId = stableId ?: imageHash?.let { digest("$it|${predicted ?: "none"}|$chosen") }
        val events = (priorEvents + listOfNotNull(eventId)).toList().takeLast(500)
        prefs.edit().putString("features", stored.toString())
            .putString("events", org.json.JSONArray(events).toString())
            .putInt("corrections", prefs.getInt("corrections", 0) + if (corrected) 1 else 0)
            .putInt("confirmed", prefs.getInt("confirmed", 0) + 1)
            .apply()
        true
    }

    fun confirmedCount(): Int = prefs.getInt("confirmed", 0)
    fun correctionCount(): Int = prefs.getInt("corrections", 0)

    private fun sampleFor(lines: List<OcrLine>, target: Double): Set<String>? {
        val amount = BigDecimal.valueOf(target).stripTrailingZeros()
        val index = lines.indexOfFirst { line ->
            PriceExtractor.AMOUNT.findAll(line.text).any { match ->
                val parsed = runCatching { BigDecimal(match.groupValues[1].replace(",", "") + if (match.groupValues[2].isNotEmpty()) ".${match.groupValues[2]}" else "") }.getOrNull()
                parsed?.stripTrailingZeros() == amount
            }
        }
        if (index < 0) return null
        val line = lines[index]
        val matchedText = PriceExtractor.AMOUNT.findAll(line.text).firstOrNull { match ->
            runCatching { BigDecimal(match.groupValues[1].replace(",", "") + if (match.groupValues[2].isNotEmpty()) ".${match.groupValues[2]}" else "").stripTrailingZeros() == amount }.getOrDefault(false)
        } ?: return null
        val priorAmountEnd = PriceExtractor.AMOUNT.findAll(line.text)
            .filter { it.range.last < matchedText.range.first }
            .lastOrNull()?.range?.last?.plus(1) ?: 0
        val start = maxOf(priorAmountEnd, (matchedText.range.first - 48).coerceAtLeast(0))
        val end = (matchedText.range.last + 49).coerceAtMost(line.text.length)
        val context = line.text.substring(start, end).removeRange(
            matchedText.range.first - start, matchedText.range.last + 1 - start
        )
        return features(context)
    }

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        /** Shared by the learner and ranker, so inference uses the same context representation. */
        private val FEATURES = setOf(
            "mrp", "list", "original", "regular", "was", "strike", "actual",
            "price", "deal", "offer", "sale", "selling", "now", "today", "buy",
            "only", "emi", "month", "monthly", "per", "shipping", "delivery",
            "fee", "fees", "charge", "charges", "discount", "cashback", "coupon",
            "exchange", "bank", "save", "savings", "off", "tax", "taxes",
            "inclusive", "cart", "bag", "starting", "from", "at", "down",
            "installment", "installments", "reward", "rewards", "points", "protect",
            "promise", "handling", "processing", "gst", "flat", "extra", "additional"
        )
        fun features(text: String): Set<String> {
            val words = text.lowercase().split(Regex("[^\\p{L}]+")).filter { it.length >= 3 }
            return words.filter { it in FEATURES }.map { "word:$it" }.toSet()
        }
    }
}
