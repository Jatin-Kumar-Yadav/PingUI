package com.pingpaisa.app.recognition

import android.graphics.Bitmap
import com.pingpaisa.app.catalog.CategoryRepository
import com.pingpaisa.app.catalog.Classification
import com.pingpaisa.app.catalog.ImageLabelHit
import com.pingpaisa.app.catalog.MatchSource
import com.pingpaisa.app.catalog.ProductClassifier
import com.pingpaisa.app.data.ImageStore
import com.pingpaisa.app.data.PriceLearningStore
import com.pingpaisa.app.data.PriceSource
import com.pingpaisa.app.link.FetchResult
import com.pingpaisa.app.link.ProductPageFetcher
import com.pingpaisa.app.link.SharedContent
import com.pingpaisa.app.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AnalysisResult(
    val name: String,
    val nameIsGuess: Boolean,
    val brand: String?,
    val price: Double?,
    val priceSource: PriceSource,
    val priceConfidence: Float?,
    val priceCandidates: List<Double>,
    val priceNote: String?,
    val priceRaw: String? = null,
    val classification: Classification,
    val message: String?,
    val imagePath: String? = null,
    val imageHash: String? = null,
    val variant: String? = null,
    /** Title or price was not read reliably: the user must confirm before the price is used. */
    val needsReview: Boolean = false
)

/**
 * The one pipeline behind camera, gallery, multi-image and shared-link inputs:
 *   image/link -> OCR + labels (+ optional AI) -> price (PriceExtractor) -> name -> category (ProductClassifier).
 * UI code never touches OCR or classification directly.
 */
class ProductAnalyzer(
    private val ocr: OcrEngine,
    private val repo: CategoryRepository,
    private val classifier: ProductClassifier,
    private val gemini: GeminiRecognizer?,
    private val fetcher: ProductPageFetcher,
    private val images: ImageStore,
    private val priceLearning: PriceLearningStore? = null
) {
    suspend fun analyzeImage(path: String, focus: FocusSpec?): AnalysisResult = withContext(Dispatchers.Default) {
        val bmp = ImageStore.decodeForOcr(path)
            ?: return@withContext failed("Couldn't open that image. Enter the details yourself.")
        var message: String? = null
        val lines = try { ocr.readText(bmp) } catch (e: Exception) {
            message = "Text recognition failed - enter the price yourself."; emptyList()
        }
        val focusBox = focus?.let { ViewportMapper.focusInImage(it, bmp.width, bmp.height) }
        // 1) layout-aware reading (title + selected-variant price + exclusion of ads/EMI/MRP) ...
        val listing = ListingReader.read(lines, focusBox, { box -> isStruck(bmp, box) }, priceLearning?.weights().orEmpty())
        // 2) ... else the simple rupee-marker extractor (single price tags, plain text)
        val simple = if (listing == null) PriceExtractor.extract(lines, focusBox) else null
        val labels = try { ocr.labels(bmp) } catch (e: Exception) { emptyList() }
        val allText = lines.joinToString(" ") { it.text }

        val title = listing?.title
        var cls = title?.let { classifier.classify(it, labels) }?.takeIf { it.source != MatchSource.UNMATCHED }
            ?: classifier.classify(allText, labels)
        var name = title ?: ProductNamer.deriveName(lines)
        var guess = false
        var brand = findBrand(title ?: allText)

        if (gemini?.enabled == true && (name == null || cls.confidence < 0.8f)) {
            gemini.identify(bmp, allText)?.let { ai ->
                if (ai.name != null) { name = ai.name; guess = false }
                brand = ai.brand ?: brand
                classifier.fromSuggestion(ai.category, ai.subcategory, "Suggested by AI from the photo, matched to your dataset")
                    ?.let { cls = it }
            }
        }
        if (name == null) {
            val top = labels.maxByOrNull { it.confidence }
            name = top?.text ?: "Unknown product"
            guess = true
        } else if (name!!.length < 4) guess = true

        // Always auto-fill the best price; a shaky read is flagged for confirmation and the user can always edit it.
        val chosen: PriceCandidate?
        val confidence: Float
        val needsConfirmation: Boolean
        val note: String?
        val candidates: List<Double>
        if (listing != null) {
            chosen = listing.price; confidence = listing.confidence; needsConfirmation = listing.needsConfirmation
            note = listing.note; candidates = listing.candidates.map { it.value }
        } else {
            chosen = simple!!.best; confidence = simple.confidence; needsConfirmation = simple.isLowConfidence
            note = simple.note; candidates = simple.candidates.map { it.value }
        }
        if (chosen == null) {
            message = message ?: if (lines.isEmpty()) "No readable text found. Enter the price yourself." else "No price found. Enter it yourself."
        } else if (needsConfirmation) {
            message = note ?: "Please check the price before it is used."
        }
        AnalysisResult(
            name = name!!, nameIsGuess = guess, brand = brand,
            price = chosen?.value, priceSource = if (chosen != null) PriceSource.OCR else PriceSource.NONE,
            priceConfidence = chosen?.let { confidence },
            priceCandidates = candidates, priceNote = if (chosen != null) note else null,
            priceRaw = chosen?.raw, classification = cls, message = message,
            variant = listing?.variant, needsReview = chosen != null && (needsConfirmation || guess)
        )
    }

    /** True when a horizontal strike-through line crosses the printed amount in [box] (a list price / MRP). */
    private fun isStruck(bmp: android.graphics.Bitmap, box: Box): Boolean {
        val l = box.left.toInt().coerceIn(0, bmp.width - 1)
        val t = box.top.toInt().coerceIn(0, bmp.height - 1)
        val w = (box.right.toInt() - l).coerceIn(0, bmp.width - l)
        val h = (box.bottom.toInt() - t).coerceIn(0, bmp.height - t)
        if (w < 12 || h < 8) return false
        return try {
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, l, t, w, h)
            for (i in px.indices) {
                val c = px[i]
                px[i] = ((0.299f * ((c shr 16) and 0xFF)) + (0.587f * ((c shr 8) and 0xFF)) + (0.114f * (c and 0xFF))).toInt()
            }
            StrikeDetector.isStruck(px, w, h)
        } catch (e: Exception) { false }
    }

    suspend fun analyzeLink(content: SharedContent): AnalysisResult = withContext(Dispatchers.Default) {
        val url = content.url ?: return@withContext analyzeText(content)
        val textPrice = PriceExtractor.extract(content.text.lines().map { OcrLine(it) }).best
        when (val r = fetcher.fetch(url)) {
            is FetchResult.Success -> {
                val info = r.info
                val image = r.imageBytes?.let { images.saveBytes(it) }
                val title = info.title ?: content.text.takeIf { it.isNotBlank() }
                val brand = info.brand ?: findBrand(listOfNotNull(title, content.text).joinToString(" "))
                var note: String? = null
                var message: String? = null
                val price: Double?
                val source: PriceSource
                when {
                    info.priceUsable -> {
                        price = info.price; source = PriceSource.PAGE
                        if (textPrice != null && textPrice.value != info.price) {
                            note = "The shared message said ${Money.format(textPrice.value)}; the page lists ${Money.format(info.price!!)}."
                        }
                    }
                    textPrice != null -> { price = textPrice.value; source = PriceSource.SHARED_TEXT }
                    else -> {
                        price = null; source = PriceSource.NONE
                        message = if (info.price != null) "The page lists the price in ${info.currency}. Enter it in rupees."
                        else "The page didn't publish a price. Enter it yourself."
                    }
                }
                val labels: List<ImageLabelHit> = if (image != null) {
                    ImageStore.decodeForOcr(image.path)?.let { runCatching { ocr.labels(it) }.getOrDefault(emptyList()) } ?: emptyList()
                } else emptyList()
                val text = listOfNotNull(title, brand, content.text).joinToString(" ")
                val cls = classifier.classify(text, labels, content.host)
                AnalysisResult(
                    name = title ?: "Product from ${content.host ?: "link"}", nameIsGuess = title == null, brand = brand,
                    price = price, priceSource = source, priceConfidence = null,
                    priceCandidates = listOfNotNull(info.price, textPrice?.value).distinct(), priceNote = note,
                    priceRaw = if (source == PriceSource.SHARED_TEXT) textPrice?.raw else null,
                    classification = cls, message = message, imagePath = image?.path, imageHash = image?.hash
                )
            }
            is FetchResult.Failure -> {
                val cls = classifier.classify(content.text, emptyList(), content.host)
                AnalysisResult(
                    name = content.text.ifBlank { "Product from ${content.host ?: "link"}" }.take(80),
                    nameIsGuess = true, brand = findBrand(content.text),
                    price = textPrice?.value, priceSource = if (textPrice != null) PriceSource.SHARED_TEXT else PriceSource.NONE,
                    priceConfidence = null, priceCandidates = listOfNotNull(textPrice?.value), priceNote = null, priceRaw = textPrice?.raw,
                    classification = cls, message = r.message + " Enter the details yourself."
                )
            }
        }
    }

    /** Shared plain text without a link ("iPhone 15 Rs 59,999"). */
    fun analyzeText(content: SharedContent): AnalysisResult {
        val price = PriceExtractor.extract(content.text.lines().map { OcrLine(it) })
        val chosen = price.best
        val cls = classifier.classify(content.text)
        return AnalysisResult(
            name = content.text.take(80), nameIsGuess = true, brand = findBrand(content.text),
            price = chosen?.value, priceSource = if (chosen != null) PriceSource.SHARED_TEXT else PriceSource.NONE,
            priceConfidence = null, priceCandidates = price.candidates.map { it.value }, priceNote = null, priceRaw = chosen?.raw,
            classification = cls,
            message = when {
                price.best == null -> "No rupee price (₹ / Rs) in the shared text. Enter it yourself."
                price.isLowConfidence -> price.note ?: "Price filled in automatically - please check it."
                else -> null
            }
        )
    }

    private fun failed(msg: String) = AnalysisResult(
            name = "Unknown product", nameIsGuess = true, brand = null, price = null,
            priceSource = PriceSource.NONE, priceConfidence = null, priceCandidates = emptyList(),
            priceNote = null, classification = classifier.unmatched(), message = msg
    )

    private fun findBrand(text: String): String? {
        val t = ProductClassifier.normalize(text)
        return repo.brands.sortedByDescending { it.length }.firstOrNull { b ->
            Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(ProductClassifier.normalize(b)) + "(?![\\p{L}\\p{N}])").containsMatchIn(t)
        }
    }
}
