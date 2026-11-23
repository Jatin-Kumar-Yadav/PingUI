package com.pingpaisa.app

import com.pingpaisa.app.finance.EmiSource
import com.pingpaisa.app.recognition.EmiScreenshotParser
import com.pingpaisa.app.recognition.OcrLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class EmiScreenshotParserTest {
    private fun lines(vararg t: String) = t.map { OcrLine(it) }
    private fun parse(vararg t: String) = EmiScreenshotParser.parse(lines(*t), BigDecimal("16656"))

    /** The "Choose EMI Option" list from a shopping app (the reference screenshot). */
    private val bankList = arrayOf(
        "Choose EMI Option", "Help", "Search for bank", "No Cost EMI +", "Offers +", "Credit Card & Bajaj EMI",
        "Axis Bank Credit Card", "No Cost EMI", "from ₹2,776/m", "1 offer applicable Details",
        "ICICI Bank Credit Card", "No Cost EMI", "from ₹2,777/m", "2 offer applicable Details",
        "Flipkart Axis Bank Credit Card", "EMI", "from ₹826/m", "2 offer applicable Details",
        "Bajaj Finance EMI Network Card", "No Cost EMI", "from ₹1,854/m",
        "Flipkart Bajaj Finance Insta EMI ...", "No Cost EMI", "from ₹2,782/m"
    )

    @Test fun bankListGivesOneCandidatePerBank() {
        val r = parse(*bankList)
        assertEquals(5, r.options.size)
        val axis = r.options[0]
        assertEquals("Axis Bank Credit Card", axis.lender)
        assertEquals(BigDecimal("2776"), axis.monthlyEmi)
        assertTrue(axis.noCostAdvertised)
        assertTrue(axis.fromLowest)
        assertNull(axis.tenureMonths)                    // never invented
        assertEquals(EmiSource.SCREENSHOT, axis.source)
        assertEquals(BigDecimal("16656"), axis.quotedForPrice)
        assertEquals(BigDecimal("826"), r.options[2].monthlyEmi)
        assertFalse(r.options[2].noCostAdvertised)       // plain EMI, not no-cost
        assertEquals(BigDecimal("1854"), r.options[3].monthlyEmi)
        assertEquals(BigDecimal("2782"), r.options[4].monthlyEmi)
    }

    @Test fun everyScreenshotOptionStartsUnconfirmedAndAsksForTheTenure() {
        val r = parse(*bankList)
        assertTrue(r.options.none { it.confirmed })
        assertTrue(r.notes.any { it.contains("tenure") })
        assertTrue(r.notes.any { it.contains("may be wrong") })
    }

    @Test fun detailTableGivesOneOptionPerTenureWithSharedCharges() {
        val r = parse(
            "HDFC Bank Credit Card", "No Cost EMI",
            "3 months ₹6,333/month", "6 months ₹3,200/month 15% p.a.", "12 months ₹1,750/month",
            "Processing fee ₹199", "Down payment ₹1,000"
        )
        assertEquals(3, r.options.size)
        assertEquals(listOf(3, 6, 12), r.options.map { it.tenureMonths })
        assertEquals(listOf("6333", "3200", "1750"), r.options.map { it.monthlyEmi!!.toPlainString() })
        assertTrue(r.options.all { it.processingFee == BigDecimal("199") && it.downPayment == BigDecimal("1000") && it.noCostAdvertised })
        assertEquals(BigDecimal("15"), r.options[1].annualRatePct ?: r.options[0].annualRatePct)
    }

    @Test fun singleOfferReadsTotalCashbackAndRate() {
        val r = parse(
            "SBI Credit Card", "EMI ₹3,200 x 6 months", "Interest rate 14.5% p.a.",
            "Total payable ₹19,200", "Instant discount ₹500", "Processing fee ₹99"
        )
        val o = r.options.single()
        assertEquals(BigDecimal("3200"), o.monthlyEmi)
        assertEquals(6, o.tenureMonths)
        assertEquals(BigDecimal("14.5"), o.annualRatePct)
        assertEquals(BigDecimal("19200"), o.totalPayable)
        assertEquals(BigDecimal("500"), o.cashback)
        assertEquals(BigDecimal("99"), o.processingFee)
    }

    @Test fun numbersWithoutRupeeMarkerAreNeverEmiAmounts() {
        val r = parse("Axis Bank Credit Card", "6 months 3,167 per month", "12 months 1,750")
        assertTrue(r.options.isEmpty())
    }

    @Test fun otherRupeeAmountsAreNotTakenAsEmi() {
        val r = parse("Samsung Galaxy A54 5G", "₹18,999", "M.R.P. ₹25,999", "Save ₹7,000", "8GB 256GB")
        assertTrue(r.options.isEmpty())
        assertTrue(r.notes.single().contains("couldn't find EMI offers"))
    }

    @Test fun unrelatedOrEmptyImagesGiveAHelpfulNote() {
        assertTrue(parse().notes.single().contains("No readable text"))
        assertTrue(parse("Hello world", "Contact us 9876543210").options.isEmpty())
    }

    @Test fun ambiguousTenureListIsLeftEmptyForTheUser() {
        val r = parse("ICICI Bank Credit Card", "No Cost EMI available for 3, 6, 9 months", "from ₹2,777/m")
        assertNull(r.options.single().tenureMonths)
        assertEquals(BigDecimal("2777"), r.options.single().monthlyEmi)
    }

    @Test fun lowOcrConfidenceIsMentioned() {
        val r = EmiScreenshotParser.parse(
            listOf(OcrLine("Axis Bank Credit Card", null, 0.3f), OcrLine("from ₹2,776/m", null, 0.3f)), null
        )
        assertTrue(r.notes.any { it.contains("blurry") })
    }

    @Test fun sameOfferSeenTwiceIsListedOnce() {
        val r = parse("Axis Bank Credit Card", "from ₹2,776/m", "Axis Bank Credit Card", "from ₹2,776/m")
        assertEquals(1, r.options.size)
    }
}
