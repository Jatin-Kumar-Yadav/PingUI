package com.pingpaisa.app

import com.pingpaisa.app.catalog.CategoryRepository
import com.pingpaisa.app.catalog.ProductClassifier
import com.pingpaisa.app.data.ItemState
import com.pingpaisa.app.data.PriceSource
import com.pingpaisa.app.data.Product
import com.pingpaisa.app.finance.AffordabilityCalculator
import com.pingpaisa.app.finance.Candidate
import com.pingpaisa.app.finance.FinancialProfile
import com.pingpaisa.app.link.SharedLinkParser
import com.pingpaisa.app.recognition.OcrLine
import com.pingpaisa.app.recognition.PriceExtractor
import com.pingpaisa.app.ui.scan.VerdictAction
import com.pingpaisa.app.ui.scan.VerdictKind
import com.pingpaisa.app.ui.scan.VerdictPresenter
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Exercises the modules together using the same data flow as ScanViewModel. */
class ScannerIntegrationTest {
    private val profile = FinancialProfile(netIncome = 100_000.0, fixedExpenses = 30_000.0,
        variableExpenses = 10_000.0, monthlySavingsTarget = 10_000.0,
        savings = 200_000.0, emergencyReserve = 100_000.0)

    private fun product(text: String): Product {
        val shared = SharedLinkParser.parse(text)!!
        val price = PriceExtractor.extract(shared.text.lines().map { OcrLine(it) }).best?.value
        val assets = File("src/main/assets")
        val repo = CategoryRepository.parse(File(assets, "india_expense_categorization_dataset.json").readText(),
            File(assets, "product_keywords.json").readText())
        val category = ProductClassifier(repo).classify(shared.text)
        return Product(name = shared.text, price = price, priceSource = PriceSource.SHARED_TEXT,
            category = category.category, subcategory = category.subcategory,
            state = if (price == null) ItemState.NEEDS_PRICE else ItemState.READY)
    }

    @Test fun sharedRupeePriceReachesRealVerdict() {
        val p = product("Samsung Smart TV Rs. 18,999")
        assertEquals(18_999.0, p.price!!, 0.0)
        assertEquals("Shopping", p.category)
        val a = AffordabilityCalculator.assess(profile, listOf(Candidate(p.id, p.price, p.priority)))
        val ui = VerdictPresenter.present(listOf(p), a, profile)
        assertEquals(VerdictKind.GREEN, ui.kind)
        assertEquals(VerdictAction.DETAILS, ui.action)
    }

    @Test fun missingProfileNeverProducesPositiveVerdict() {
        val ui = VerdictPresenter.present(listOf(product("Phone ₹12,999")), null, null)
        assertEquals(VerdictKind.NEUTRAL, ui.kind)
        assertEquals(VerdictAction.PROFILE, ui.action)
    }

    @Test fun unreadablePriceRequiresUserInput() {
        val p = product("Phone Model 18999 128 GB")
        assertNull(p.price)
        val a = AffordabilityCalculator.assess(profile, listOf(Candidate(p.id, p.price, p.priority)))
        assertEquals("Price needed", VerdictPresenter.present(listOf(p), a, profile).title)
    }

    @Test fun changingFinancesRecalculatesVerdict() {
        val p = product("Phone ₹18,999")
        val shortfall = profile.copy(netIncome = 20_000.0, savings = 100_000.0)
        val a = AffordabilityCalculator.assess(shortfall, listOf(Candidate(p.id, p.price, p.priority)))
        assertEquals(VerdictKind.RED, VerdictPresenter.present(listOf(p), a, shortfall).kind)
    }
}
