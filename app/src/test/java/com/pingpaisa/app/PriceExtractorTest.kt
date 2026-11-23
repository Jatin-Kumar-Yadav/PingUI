package com.pingpaisa.app

import com.pingpaisa.app.recognition.Box
import com.pingpaisa.app.recognition.OcrLine
import com.pingpaisa.app.recognition.PriceExtractor
import com.pingpaisa.app.recognition.PriceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rupee rule: a number is a price only when a rupee marker sits directly in front of it. */
class PriceExtractorTest {
    private fun lines(vararg t: String) = t.map { OcrLine(it) }
    private fun best(vararg t: String) = PriceExtractor.extract(lines(*t)).best?.value
    private fun amounts(vararg t: String) = PriceExtractor.extract(lines(*t)).candidates.map { it.value }.sorted()

    // ---- the table from the requirements ----
    @Test fun acceptsRupeeSymbol() {
        assertEquals(18999.0, best("₹18,999")!!, 0.0)
        assertEquals(18999.0, best("₹ 18,999")!!, 0.0)
        assertEquals(1299.50, best("₹1,299.50")!!, 0.0)
    }

    @Test fun acceptsRupeeNotation() {
        assertEquals(18999.0, best("Rs. 18,999")!!, 0.0)
        assertEquals(18999.0, best("Rs.18,999")!!, 0.0)
        assertEquals(18999.0, best("Rs 18,999")!!, 0.0)
        assertEquals(18999.0, best("INR 18999")!!, 0.0)
        assertEquals(18999.0, best("Rupees 18,999")!!, 0.0)
        assertEquals(1299.0, best("Rs. 1,299/-")!!, 0.0)
        assertEquals(123456.0, best("₹1,23,456")!!, 0.0)
    }

    @Test fun neverAcceptsNumbersWithoutARupeeMarker() {
        assertNull(best("18,999"))
        assertNull(best("18999"))
        assertNull(best("Model 18999"))
        assertNull(best("500 GB"))
        assertNull(best("20% discount"))
        assertNull(best("Price 499"))
        assertNull(best("Only 18,999/-"))
        assertNull(best("8GB RAM 128GB storage"))
        assertNull(best("Call 9876543210"))
        assertNull(best("Pin 110001"))
        assertNull(best("12/05/2024"))
        assertNull(best("Rated 4.5 stars 2,345 ratings"))
        assertNull(best("5000 mAh battery"))
        assertNull(best("Flat 24% off"))
        assertNull(best("Model SM-A546 5G 8/256"))
    }

    @Test fun rupeeSignDoesNotLeakToUnmarkedNumbersOnTheSameLine() {
        // "₹18,999" is a price; the 256 and 5000 beside it are not
        val r = PriceExtractor.extract(lines("256 GB 5000 mAh ₹18,999"))
        assertEquals(listOf(18999.0), r.candidates.map { it.value })
    }

    @Test fun rsInsideWordsIsNotARupeeMarker() {
        assertNull(best("Specs 8"))
        assertNull(best("Hours 24"))
        assertNull(best("Ears 2 pack"))
        assertNull(best("Mirs 499"))
    }

    @Test fun mrpAndOfferBothFoundSellingPriceChosen() {
        val r = PriceExtractor.extract(lines("MRP ₹25,999, Offer ₹18,999"))
        assertEquals(listOf(18999.0, 25999.0), r.candidates.map { it.value }.sorted())
        assertEquals(18999.0, r.best!!.value, 0.0)
        assertEquals(PriceKind.SELLING, r.best!!.kind)
        assertNotNull(r.confident)
        assertTrue(r.candidates.any { it.value == 25999.0 && it.kind == PriceKind.MRP })
    }

    @Test fun mrpOnPreviousLineIsStillMrp() {
        val r = PriceExtractor.extract(lines("M.R.P.:", "₹24,999", "Deal Price ₹18,999"))
        assertEquals(18999.0, r.best!!.value, 0.0)
    }

    // ---- other monetary values are not the selling price ----
    @Test fun ignoresEmiDiscountAndFeeAmounts() {
        val r = PriceExtractor.extract(lines("₹18,999", "No Cost EMI ₹3,167/month", "You save ₹2,000", "Cashback ₹500", "Processing fee ₹199", "Down payment ₹1,000"))
        assertEquals(listOf(18999.0), r.candidates.map { it.value })
        assertNotNull(r.confident)
    }

    @Test fun emiStartingFromIsNotAPrice() {
        assertNull(best("EMI from ₹2,776/m"))
        assertNull(best("from ₹826/m"))
        assertNull(best("₹3,167 x 6 months"))
    }

    @Test fun sellingPriceBeatsMrpUsingPrintSize() {
        val r = PriceExtractor.extract(
            listOf(
                OcrLine("M.R.P.: ₹24,999", Box(0f, 100f, 300f, 120f)),
                OcrLine("₹18,999", Box(0f, 10f, 300f, 80f)),
                OcrLine("24% off", Box(0f, 130f, 100f, 150f))
            )
        )
        assertEquals(18999.0, r.best!!.value, 0.0)
        assertTrue(r.candidates.any { it.value == 24999.0 && it.kind == PriceKind.MRP })
    }

    // ---- never guess ----
    @Test fun onlyAnMrpIsNotUsedWithoutAsking() {
        val r = PriceExtractor.extract(lines("MRP ₹499 inclusive of all taxes"))
        assertEquals(PriceKind.MRP, r.best!!.kind)
        assertNotNull(r.note)
        assertNull(r.confident)
        assertTrue(r.needsConfirmation)
    }

    @Test fun twoPlainRupeeAmountsAreAmbiguousSoWeAsk() {
        val r = PriceExtractor.extract(lines("₹1,999", "₹2,499"))
        assertEquals(2, r.candidates.size)
        assertNull(r.confident)
        assertTrue(r.needsConfirmation)
    }

    @Test fun singleMarkedPriceIsConfident() {
        val r = PriceExtractor.extract(lines("₹18,999"))
        assertTrue(r.confidence >= 0.8f)
        assertNotNull(r.confident)
        assertFalse(r.needsConfirmation)
    }

    @Test fun displayTextAndExactDecimalAreKept() {
        val c = PriceExtractor.extract(lines("Now ₹ 1,299.50 only")).best!!
        assertEquals("₹ 1,299.50", c.raw)
        assertEquals(java.math.BigDecimal("1299.50"), c.amount)
        assertTrue(c.hasCurrencyMarker)
    }

    @Test fun aimingBoxBreaksTies() {
        val inside = OcrLine("₹1,999", Box(100f, 100f, 200f, 140f))
        val outside = OcrLine("₹2,499", Box(400f, 400f, 500f, 440f))
        val r = PriceExtractor.extract(listOf(outside, inside), focus = Box(50f, 50f, 300f, 300f))
        assertEquals(1999.0, r.best!!.value, 0.0)
    }

    @Test fun biggerPrintWinsWithoutFocus() {
        val big = OcrLine("₹1,999", Box(0f, 0f, 200f, 80f))
        val small = OcrLine("₹2,499", Box(0f, 100f, 80f, 120f))
        assertEquals(1999.0, PriceExtractor.extract(listOf(small, big)).best!!.value, 0.0)
    }

    @Test fun emptyInputAndNoRupeeText() {
        assertNull(PriceExtractor.extract(emptyList()).best)
        assertNull(PriceExtractor.extract(lines("Samsung Galaxy A54 5G", "8GB + 256GB", "Awesome Graphite")).best)
    }

    // ---- OCR misreads the rupee glyph ----
    @Test fun misreadRupeeGlyphIsUsedOnlyAsAFlaggedFallback() {
        val r = PriceExtractor.extract(lines("Galaxy M14 5G", "?18,999"))
        assertEquals(18999.0, r.best!!.value, 0.0)
        assertFalse(r.best!!.hasCurrencyMarker)
        assertTrue(r.needsConfirmation)
        assertTrue(r.isLowConfidence)
    }

    @Test fun lookalikeIsIgnoredWhenARealMarkerExists() {
        val r = PriceExtractor.extract(lines("T12345", "₹18,999"))
        assertEquals(listOf(18999.0), r.candidates.map { it.value })
    }

    @Test fun lookalikeNeverMakesModelNumbersIntoPrices() {
        assertNull(PriceExtractor.extract(lines("Model 18999", "500 GB", "20% discount")).best)
    }
}
