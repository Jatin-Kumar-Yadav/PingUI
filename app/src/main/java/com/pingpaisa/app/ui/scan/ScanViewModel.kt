package com.pingpaisa.app.ui.scan

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pingpaisa.app.PingPaisaApp
import com.pingpaisa.app.camera.LivePrice
import com.pingpaisa.app.data.ItemState
import com.pingpaisa.app.data.ImageSource
import com.pingpaisa.app.data.ImageStore
import com.pingpaisa.app.data.ImportedImage
import com.pingpaisa.app.data.PriceSource
import com.pingpaisa.app.data.Product
import com.pingpaisa.app.finance.AffordabilityCalculator
import com.pingpaisa.app.finance.Assessment
import com.pingpaisa.app.finance.Candidate
import com.pingpaisa.app.finance.Advice
import com.pingpaisa.app.finance.EmiOption
import com.pingpaisa.app.finance.FinancialProfile
import com.pingpaisa.app.finance.PurchaseAdvisor
import com.pingpaisa.app.finance.toMoneyDecimal
import com.pingpaisa.app.recognition.EmiScreenshotParser
import com.pingpaisa.app.recognition.OcrLine
import com.pingpaisa.app.finance.Priority
import com.pingpaisa.app.link.SharedContent
import com.pingpaisa.app.link.SharedLinkParser
import com.pingpaisa.app.recognition.AnalysisResult
import com.pingpaisa.app.recognition.FocusSpec
import com.pingpaisa.app.catalog.ProductClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import com.pingpaisa.app.scanner.CropJob
import com.pingpaisa.app.scanner.FlowEvent
import com.pingpaisa.app.scanner.FlowState
import com.pingpaisa.app.scanner.ScanFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import java.io.File

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    /** Exact same image / link already in the list: ask, never silently drop. */
    data class AskDuplicate(val token: String, val existingName: String) : UiEvent
    /** Analysis finished and looks like another item (same name and price). */
    data class AskPossibleDuplicate(val productId: String, val otherName: String) : UiEvent
}

@OptIn(FlowPreview::class)
class ScanViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app as PingPaisaApp
    private val images = ctx.imageStore
    private val analyzer = ctx.analyzer
    private val classifier = ctx.classifier
    val categories = ctx.categories

    private val _products = MutableStateFlow(ctx.productStore.load())
    val products: StateFlow<List<Product>> = _products

    val profile: StateFlow<FinancialProfile?> = ctx.profileStore.profile
    val profileIsPersistent: Boolean get() = ctx.profileStore.persistent

    /** Recomputed instantly whenever a price, priority or the product list changes. */
    val assessment: StateFlow<Assessment?> = combine(_products, profile) { list, p ->
        if (p == null || !p.isUsable()) null
        else AffordabilityCalculator.assess(
            p, list.filter { it.state != ItemState.PROCESSING }.map { Candidate(it.id, if (it.needsReview) null else it.price, it.priority) },
            ctx.expenseHistory.lastCompleteMonthTotals()
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Pay-in-full vs EMI for every product the user said has EMI options. Recomputed on every edit of a price,
     * an EMI option or the financial profile, so the numbers on screen always match the inputs.
     */
    val advice: StateFlow<Map<String, Advice>> = combine(_products, profile) { list, p ->
        if (p == null || !p.isUsable()) emptyMap()
        else list.filter { it.state != ItemState.PROCESSING && it.emiAvailable == true && !it.needsReview && it.emiOptions.isNotEmpty() && (it.price ?: 0.0) > 0.0 }
            .associate { it.id to PurchaseAdvisor.advise(p, it.id, it.price!!.toMoneyDecimal(), it.priority, it.emiOptions, ctx.expenseHistory.lastCompleteMonthTotals()) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Products whose EMI screenshot is being read. */
    val emiBusy = MutableStateFlow<Set<String>>(emptySet())
    /** What the last screenshot read found or could not find, per product (shown inside the Edit sheet, which covers snackbars). */
    val emiNotes = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    private fun setNotes(id: String, notes: List<String>) = emiNotes.update { it + (id to notes) }

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId
    /** Timestamp of the last time a product was captured or tapped; the chip shows it for a few seconds. */
    val pinnedAt = MutableStateFlow(0L)

    val live = MutableStateFlow<LivePrice?>(null)

    // ---------- scanner state ----------

    private val prefs = app.getSharedPreferences("scanner_ui", android.content.Context.MODE_PRIVATE)
    /** The user's explicit camera switch. Only the user changes it: gallery, crop, results and resume never do. */
    val cameraOn = MutableStateFlow(prefs.getBoolean("camera_on", true))
    fun setCameraOn(on: Boolean) {
        cameraOn.value = on
        prefs.edit().putBoolean("camera_on", on).apply()
        if (!on) live.value = null
    }

    private val _flow = MutableStateFlow<FlowState>(FlowState.Idle)
    /** Capture/gallery -> crop -> process -> verify. One state at a time. */
    val flow: StateFlow<FlowState> = _flow
    private fun dispatch(e: FlowEvent) { _flow.update { ScanFlow.reduce(it, e) } }
    private val sources = HashMap<String, ImageSource>()
    private val jobs = HashMap<String, Job>()
    private var jobCounter = 0

    private val eventChannel = Channel<UiEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private class Pending(val proceed: () -> Unit, val discard: () -> Unit)
    private val pending = HashMap<String, Pending>()
    private val gate = Semaphore(2)

    init {
        viewModelScope.launch(Dispatchers.IO) { _products.drop(1).debounce(400).collect { ctx.productStore.save(it) } }
    }

    // ---------- inputs ----------

    /** Gallery / shared images: each one is cropped first, one after another. Nothing is processed uncropped. */
    fun addGalleryImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val list = uris.map { uri -> val key = "u${jobCounter++}:$uri"; sources[key] = ImageSource.OfUri(uri); CropJob(key, false) }
        dispatch(FlowEvent.Picked(list))
    }

    /** A photo taken with the camera: open the crop editor (ignored while another image is being handled). */
    fun addCapturedImage(file: File) {
        if (_flow.value != FlowState.Idle) { file.delete(); return }
        val key = "c${jobCounter++}:${file.name}"
        sources[key] = ImageSource.OfFile(file)
        dispatch(FlowEvent.Captured(CropJob(key, true)))
    }

    suspend fun cropBitmap(key: String): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        sources[key]?.let { images.decodeForCrop(it) }
    }

    /** The user confirmed a selection (fractions of the image). Crops the original, then reads it. */
    fun confirmCrop(key: String, left: Float, top: Float, right: Float, bottom: Float) {
        val src = sources[key] ?: run { dispatch(FlowEvent.CropCancelled); return }
        viewModelScope.launch {
            val img = try { withContext(Dispatchers.IO) { images.importCropped(src, left, top, right, bottom) } }
            catch (e: Throwable) { send(UiEvent.Message("Couldn't crop that image. Try again or pick another.")); return@launch }
            val current = (_flow.value as? FlowState.Cropping)?.job?.key
            if (current != key) { images.delete(img.path); return@launch }      // cancelled while cropping
            releaseSource(key)
            val dup = _products.value.firstOrNull { it.contentHash == img.hash }
            if (dup != null) {
                dispatch(FlowEvent.CropCancelled)
                val token = "img:${img.path}"
                pending[token] = Pending({ startImage(img, fromCrop = false) }, { images.delete(img.path) })
                send(UiEvent.AskDuplicate(token, dup.name))
            } else startImage(img, fromCrop = true)
        }
    }

    fun cancelCrop(key: String) { releaseSource(key); dispatch(FlowEvent.CropCancelled) }
    fun retake(key: String) { releaseSource(key); dispatch(FlowEvent.Retake) }

    /** Leaves the "reading..." screen without waiting for the result; the late result is discarded. */
    fun cancelProcessing(productId: String) { removeProduct(productId) }

    fun reviewClosed() { dispatch(FlowEvent.ReviewClosed) }

    private fun releaseSource(key: String) {
        (sources.remove(key) as? ImageSource.OfFile)?.file?.delete()
    }

    /** Entry point for Android share: link, text with a link, plain text, and/or images. */
    fun handleShared(text: String?, subject: String?, uris: List<Uri>) {
        if (uris.isNotEmpty()) addGalleryImages(uris)
        val content = SharedLinkParser.parse(listOfNotNull(subject?.takeIf { it.isNotBlank() }, text).joinToString("\n")) ?: return
        val url = content.url
        if (url == null) { startText(content); return }
        val key = SharedLinkParser.normalizedKey(url)
        val dup = _products.value.firstOrNull { it.sourceUrl != null && SharedLinkParser.normalizedKey(it.sourceUrl) == key }
        if (dup == null) startLink(content) else {
            val token = "link:$key"
            pending[token] = Pending({ startLink(content) }, {})
            send(UiEvent.AskDuplicate(token, dup.name))
        }
    }

    // ---------- edits ----------

    fun select(id: String?) {
        _selectedId.value = id
        if (id != null) pinnedAt.value = System.currentTimeMillis()
    }

    fun updateProduct(id: String, name: String, price: Double?, category: String, subcategory: String, priority: Priority, variant: String? = null) {
        update(id) { p ->
            val edited = p.edited.toMutableSet()
            val priceChanged = price != p.price
            if (name.trim() != p.name) edited += "name"
            if (priceChanged) edited += "price"
            if (category != p.category || subcategory != p.subcategory) edited += "category"
            val cls = if (category != p.category || subcategory != p.subcategory) classifier.manual(category, subcategory) else null
            p.copy(
                name = name.trim().ifBlank { p.name }, nameIsGuess = false,
                variant = variant?.trim()?.ifBlank { null }, needsReview = false,      // saving is the user's confirmation
                price = price, priceSource = if (priceChanged) (if (price == null) PriceSource.NONE else PriceSource.MANUAL) else p.priceSource,
                priceConfidence = if (priceChanged) null else p.priceConfidence,
                priceNote = if (priceChanged) null else p.priceNote,
                priceRaw = if (priceChanged) null else p.priceRaw,
                category = category, subcategory = subcategory,
                classSource = cls?.source ?: p.classSource, classConfidence = cls?.confidence ?: p.classConfidence,
                classEvidence = cls?.evidence ?: p.classEvidence,
                priority = priority, edited = edited,
                state = if (price == null) ItemState.NEEDS_PRICE else ItemState.READY,
                message = if (price != null) null else p.message
            )
        }
    }

    // ---------- EMI ----------

    fun setEmiAvailable(productId: String, available: Boolean?) = update(productId) { it.copy(emiAvailable = available) }

    /** Adds a new option or replaces the one with the same id. A saved option is a confirmed option. */
    fun saveEmiOption(productId: String, option: EmiOption) = update(productId) { p ->
        val o = option.copy(confirmed = true, quotedForPrice = option.quotedForPrice ?: p.price?.toMoneyDecimal())
        val exists = p.emiOptions.any { it.id == o.id }
        p.copy(emiAvailable = true, emiOptions = if (exists) p.emiOptions.map { if (it.id == o.id) o else it } else p.emiOptions + o)
    }

    fun removeEmiOption(productId: String, optionId: String) =
        update(productId) { p -> p.copy(emiOptions = p.emiOptions.filterNot { it.id == optionId }) }

    /** OCR an EMI screenshot. The image is only read, never kept; results are unconfirmed candidates. */
    fun readEmiScreenshot(productId: String, uri: Uri) {
        viewModelScope.launch {
            emiBusy.update { it + productId }
            setNotes(productId, emptyList())
            try {
                val product = _products.value.firstOrNull { it.id == productId } ?: return@launch
                val result = try {
                    withContext(Dispatchers.Default) {
                        val img = withContext(Dispatchers.IO) { images.importUri(uri) }
                        try {
                            val bmp = ImageStore.decodeForOcr(img.path) ?: throw IllegalStateException("decode")
                            val lines: List<OcrLine> = ctx.ocr.readText(bmp)
                            EmiScreenshotParser.parse(lines, product.price?.toMoneyDecimal())
                        } finally { images.delete(img.path) }
                    }
                } catch (e: Exception) {
                    setNotes(productId, listOf("Couldn't read that image. Try another screenshot or enter the EMI manually.")); return@launch
                }
                if (result.options.isEmpty()) {
                    setNotes(productId, result.notes.ifEmpty { listOf("No EMI details found in that image.") }); return@launch
                }
                update(productId) { p ->
                    val known = p.emiOptions.map { Triple(it.lender.lowercase(), it.monthlyEmi?.stripTrailingZeros(), it.tenureMonths) }.toSet()
                    val fresh = result.options.filter { Triple(it.lender.lowercase(), it.monthlyEmi?.stripTrailingZeros(), it.tenureMonths) !in known }
                    p.copy(emiAvailable = true, emiOptions = p.emiOptions + fresh)
                }
                setNotes(productId, listOf("Found ${result.options.size} EMI option${if (result.options.size == 1) "" else "s"}.") + result.notes)
            } finally { emiBusy.update { it - productId } }
        }
    }

    fun removeProduct(id: String) {
        val p = _products.value.firstOrNull { it.id == id } ?: return
        jobs.remove(id)?.cancel()
        when (val f = _flow.value) {
            is FlowState.Processing -> if (f.productId == id) dispatch(FlowEvent.ProcessingCancelled)
            is FlowState.Reviewing -> if (f.productId == id) dispatch(FlowEvent.ReviewClosed)
            else -> Unit
        }
        images.delete(p.imagePath)
        _products.update { l -> l.filterNot { it.id == id } }
        if (_selectedId.value == id) _selectedId.value = null
    }

    fun clearAll() {
        jobs.values.forEach { it.cancel() }; jobs.clear()
        if (_flow.value is FlowState.Processing || _flow.value is FlowState.Reviewing) _flow.value = FlowState.Idle
        _products.value = emptyList()
        _selectedId.value = null
        images.clearAll()
    }

    fun saveProfile(p: FinancialProfile) = ctx.profileStore.save(p)

    fun resolveDuplicate(token: String, keep: Boolean) {
        pending.remove(token)?.let { if (keep) it.proceed() else it.discard() }
    }

    fun resolvePossibleDuplicate(productId: String, keep: Boolean) { if (!keep) removeProduct(productId) }

    // ---------- pipeline ----------

    private fun startImage(img: ImportedImage, fromCrop: Boolean) {
        val id = begin(Product(imagePath = img.path, contentHash = img.hash, name = "Reading image…"))
        if (fromCrop) dispatch(FlowEvent.CropConfirmed(id, img.path))
        run(id) { analyzer.analyzeImage(img.path, null) }
    }

    private fun startLink(content: SharedContent) {
        val id = begin(Product(sourceUrl = content.url, name = "Reading product page…"))
        run(id) { analyzer.analyzeLink(content) }
    }

    private fun startText(content: SharedContent) {
        val id = begin(Product(name = "Reading shared text…"))
        run(id) { analyzer.analyzeText(content) }
    }

    private fun begin(p: Product): String {
        _products.update { it + p }
        select(p.id)
        return p.id
    }

    private fun run(id: String, work: suspend () -> AnalysisResult) {
        jobs[id] = viewModelScope.launch {
            val result = gate.withPermit { try { work() } catch (e: CancellationException) { throw e } catch (e: Exception) { null } }
            jobs.remove(id)
            apply(id, result)
        }
    }

    private fun apply(id: String, r: AnalysisResult?) {
        if (_products.value.none { it.id == id }) return          // removed while analysing
        update(id) { p ->
            if (r == null) p.copy(
                name = "Unknown product", nameIsGuess = true, state = ItemState.NEEDS_INFO,
                message = "Couldn't analyse this. Enter the details yourself."
            ) else p.copy(
                name = r.name, nameIsGuess = r.nameIsGuess, brand = r.brand,
                variant = r.variant, needsReview = r.needsReview,
                price = r.price, priceSource = r.priceSource, priceConfidence = r.priceConfidence,
                priceCandidates = r.priceCandidates, priceNote = r.priceNote, priceRaw = r.priceRaw,
                category = r.classification.category, subcategory = r.classification.subcategory,
                classSource = r.classification.source, classConfidence = r.classification.confidence,
                classEvidence = r.classification.evidence,
                imagePath = r.imagePath ?: p.imagePath, contentHash = r.imageHash ?: p.contentHash,
                state = if (r.price == null) ItemState.NEEDS_PRICE else ItemState.READY, message = r.message
            )
        }
        val done = _products.value.firstOrNull { it.id == id } ?: return
        // Moves "reading..." on to the confirmation sheet (needs review) or back to the scanner (confident result).
        dispatch(FlowEvent.Analysed(id, needsReview = done.needsReview || done.price == null || done.nameIsGuess))
        val price = done.price ?: return
        if (done.nameIsGuess) return
        val twin = _products.value.firstOrNull {
            it.id != id && it.price == price && it.state != ItemState.PROCESSING &&
                ProductClassifier.normalize(it.name) == ProductClassifier.normalize(done.name)
        }
        if (twin != null) send(UiEvent.AskPossibleDuplicate(id, twin.name))
    }

    private fun update(id: String, f: (Product) -> Product) {
        _products.update { list -> list.map { if (it.id == id) f(it) else it } }
    }

    private fun send(e: UiEvent) { eventChannel.trySend(e) }
}
