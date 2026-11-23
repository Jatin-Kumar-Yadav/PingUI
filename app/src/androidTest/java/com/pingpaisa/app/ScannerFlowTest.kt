package com.pingpaisa.app

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.pingpaisa.app.data.ItemState
import com.pingpaisa.app.finance.FinancialProfile
import com.pingpaisa.app.finance.FinancialProfileStore
import com.pingpaisa.app.ui.scan.ScanViewModel
import com.pingpaisa.app.recognition.PriceExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ScannerFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val cameraPermission = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    private lateinit var scanner: ScanViewModel

    @Before fun reset() {
        compose.runOnUiThread {
            scanner = ViewModelProvider(compose.activity)[ScanViewModel::class.java]
            scanner.clearAll()
            (compose.activity.application as PingPaisaApp).profileStore.clear()
            compose.activity.setContent { PingPaisa(Screen.Home, scanner, compose.activity.shareVersion) }
        }
    }

    @Test fun settingsProfileDrivesScannerAndSurvivesNavigation() {
        compose.onNodeWithText("You").performClick()
        compose.onNode(hasText("Financial profile") and hasText("Income, savings, expenses & purchase policy")).performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Monthly take-home income")).performTextReplacement("100000")
        compose.onNodeWithText("Save profile").performClick()
        compose.onNode(hasText("Settings") and !hasClickAction()).assertIsDisplayed()
        compose.runOnIdle { assertEquals(100_000.0, scanner.profile.value!!.netIncome, 0.0) }
        compose.onNodeWithText("Scan", useUnmergedTree = true).performClick()
        compose.onNode(hasText("Scan price") and !hasClickAction()).assertIsDisplayed()
        compose.runOnIdle { scanner.handleShared("Samsung Smart TV Rs. 18,999", null, emptyList()) }
        compose.waitUntil(20_000) { scanner.products.value.any { it.state == ItemState.READY } && scanner.assessment.value != null }
        compose.onNodeWithText("Yes, you can afford it").assertIsDisplayed()
        compose.onNodeWithText("Home", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Scan", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Yes, you can afford it").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, scanner.products.value.size)
            assertEquals(100_000.0, FinancialProfileStore(compose.activity).profile.value!!.netIncome, 0.0)
        }
    }

    @Test fun androidShareOpensScannerAndDoesNotReplayOnRecreation() {
        compose.runOnUiThread {
            compose.activity.startActivity(Intent(compose.activity, MainActivity::class.java).apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Samsung phone Rs. 12,999")
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
        }
        compose.waitUntil(20_000) { scanner.products.value.any { it.price == 12_999.0 } }
        compose.onNode(hasText("Scan price") and !hasClickAction()).assertIsDisplayed()
        compose.onNodeWithText("Add your finances").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, scanner.products.value.size) }
    }

    @Test fun galleryImageRunsBundledOcrAndProductPersistence() {
        val file = File(compose.activity.cacheDir, "scanner-test-price.png")
        val bitmap = Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 70f }
            drawText("Samsung Smart TV", 70f, 160f, paint)
            drawText("Price Rs. 12,999", 70f, 310f, paint)
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val lines = runBlocking { (compose.activity.application as PingPaisaApp).ocr.readText(bitmap) }
        assertNotNull("OCR text: ${lines.map { it.text }}", PriceExtractor.extract(lines).best)
        compose.onNodeWithText("Scan", useUnmergedTree = true).performClick()
        compose.runOnIdle { scanner.addGalleryImages(listOf(Uri.fromFile(file))) }
        compose.waitUntil(45_000) { scanner.products.value.any { it.state != ItemState.PROCESSING } }
        compose.runOnIdle {
            val product = scanner.products.value.single()
            assertNotNull("OCR failed: ${product.message}; name=${product.name}; candidates=${product.priceCandidates}", product.price)
            assertEquals(12_999.0, product.price!!, 0.0)
            assertEquals("Shopping", scanner.products.value.single().category)
        }
        val store = (compose.activity.application as PingPaisaApp).productStore
        compose.waitUntil(10_000) { store.load().singleOrNull()?.price == 12_999.0 }
        assertEquals(12_999.0, store.load().single().price!!, 0.0)
        file.delete()
    }
}
