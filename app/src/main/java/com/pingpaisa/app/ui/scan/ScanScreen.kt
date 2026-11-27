package com.pingpaisa.app.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pingpaisa.app.PingPaisaApp
import com.pingpaisa.app.camera.CameraController
import com.pingpaisa.app.camera.awaitCameraProvider
import com.pingpaisa.app.data.ItemState
import com.pingpaisa.app.data.PriceSource
import com.pingpaisa.app.data.Product
import com.pingpaisa.app.finance.PurchaseAdvisor
import com.pingpaisa.app.recognition.FocusSpec
import com.pingpaisa.app.scanner.CameraInputs
import com.pingpaisa.app.scanner.CameraPermission
import com.pingpaisa.app.scanner.CameraPhase
import com.pingpaisa.app.scanner.CameraState
import com.pingpaisa.app.scanner.FlowState
import com.pingpaisa.app.scanner.ToggleGuard
import com.pingpaisa.app.ui.crop.CropScreen
import com.pingpaisa.app.ui.crop.ProcessingOverlay
import com.pingpaisa.app.ui.theme.PP
import com.pingpaisa.app.util.Money
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.pingpaisa.app.recognition.Box as FocusBox

@Composable
fun ScanScreen(vm: ScanViewModel, onOpenProfile: () -> Unit, onMenu: () -> Unit = {}, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val app = context.applicationContext as PingPaisaApp
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    val products by vm.products.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val assessment by vm.assessment.collectAsStateWithLifecycle()
    val selectedId by vm.selectedId.collectAsStateWithLifecycle()
    val pinnedAt by vm.pinnedAt.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val advices by vm.advice.collectAsStateWithLifecycle()
    val emiBusy by vm.emiBusy.collectAsStateWithLifecycle()
    val emiNotes by vm.emiNotes.collectAsStateWithLifecycle()
    val cameraOn by vm.cameraOn.collectAsStateWithLifecycle()
    val flow by vm.flow.collectAsStateWithLifecycle()
    val learningConfirmed by vm.learnedConfirmations.collectAsStateWithLifecycle()
    val learningCorrections by vm.learnedCorrections.collectAsStateWithLifecycle()

    // ---- camera state: the user's switch + permission + hardware decide ONE phase; nothing else starts the camera
    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var permission by remember { mutableStateOf(if (hasPermission()) CameraPermission.GRANTED else CameraPermission.UNKNOWN) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    var bindFailed by remember { mutableStateOf(false) }
    var retryTick by remember { mutableIntStateOf(0) }
    var resumed by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    val phase = CameraState.phase(CameraInputs(cameraOn, permission, hasCamera, bindFailed))

    var capturing by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var detailsOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var duplicate by remember { mutableStateOf<UiEvent.AskDuplicate?>(null) }
    var possibleDup by remember { mutableStateOf<UiEvent.AskPossibleDuplicate?>(null) }
    var pinnedActive by remember { mutableStateOf(false) }
    val toggleGuard = remember { ToggleGuard() }

    val controller = remember { CameraController(context, app.ocr) { vm.live.value = it } }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        askedOnce = true
        permission = when {
            granted -> CameraPermission.GRANTED
            activity != null && !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA) -> CameraPermission.DENIED_FOREVER
            else -> CameraPermission.DENIED
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) vm.addGalleryImages(uris)       // each one goes to the crop editor first
    }
    var emiTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    val emiPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val target = emiTargetId
        if (uri != null && target != null) vm.readEmiScreenshot(target, uri)
    }
    // EMI page captured live with the phone's camera app (separate from the scanner preview, which pauses while it is open)
    var emiShot by rememberSaveable { mutableStateOf<String?>(null) }
    val emiCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val target = emiTargetId
        val uri = emiShot?.let { Uri.parse(it) }
        if (ok && uri != null && target != null) vm.readEmiScreenshot(target, uri)
        emiShot = null
    }
    fun launchEmiCamera(productId: String) {
        try {
            val dir = java.io.File(context.cacheDir, "emi_capture").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val file = java.io.File(dir, "emi_${System.currentTimeMillis()}.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            emiTargetId = productId; emiShot = uri.toString()
            emiCamera.launch(uri)
        } catch (e: Exception) {
            scope.launch { snack.showSnackbar("Couldn't open the camera. You can upload a screenshot instead.") }
        }
    }
    val emiCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val id = emiTargetId
        if (granted) { permission = CameraPermission.GRANTED; if (id != null) launchEmiCamera(id) }
        else scope.launch { snack.showSnackbar("Camera permission is needed to take a photo. You can upload a screenshot instead.") }
    }
    fun captureEmi(productId: String) {
        emiTargetId = productId
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) launchEmiCamera(productId) else emiCameraPermission.launch(Manifest.permission.CAMERA)
    }
    fun openGallery() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    fun openSettings() = context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))

    // Ask for permission only while the user wants the camera on (once); never turns the camera on by itself.
    LaunchedEffect(cameraOn, permission) {
        if (cameraOn && hasCamera && permission == CameraPermission.UNKNOWN && !askedOnce) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> {
                    resumed = true
                    // e.g. back from system settings. Only updates permission; the ON/OFF choice is untouched.
                    if (hasPermission() && permission != CameraPermission.GRANTED) { permission = CameraPermission.GRANTED; bindFailed = false }
                }
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Bind when the phase becomes ACTIVE, unbind the moment it stops being ACTIVE. The camera stays bound while the
    // gallery, crop editor or result sheets are open (no restart, no flicker); only live reading is paused then.
    val active = phase == CameraPhase.ACTIVE
    LaunchedEffect(active, retryTick) {
        if (active) {
            try {
                val provider = context.awaitCameraProvider()
                controller.bind(provider, lifecycleOwner, previewView)
                bindFailed = false
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) { bindFailed = true }
        } else controller.unbind()
    }
    DisposableEffect(Unit) { onDispose { controller.release(); vm.live.value = null } }

    val overlayOpen = flow != FlowState.Idle || editingId != null || detailsOpen || helpOpen || confirmClear || duplicate != null || possibleDup != null
    LaunchedEffect(active, resumed, overlayOpen, flow) {
        controller.setAnalysisEnabled(active && resumed && !overlayOpen)
    }

    // Keep the screen on while scanning.
    val view = LocalView.current
    DisposableEffect(view) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }

    // The pinned price is shown for a few seconds after a capture or tap (no polling, no recomposition tick).
    LaunchedEffect(pinnedAt) {
        if (pinnedAt > 0L) { pinnedActive = true; delay(5000); pinnedActive = false }
    }

    // One-shot events from the view model.
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is UiEvent.Message -> { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(e.text) }
                is UiEvent.AskDuplicate -> duplicate = e
                is UiEvent.AskPossibleDuplicate -> possibleDup = e
            }
        }
    }

    val ui = VerdictPresenter.present(products, assessment, profile, advices)
    val selected = products.firstOrNull { it.id == selectedId }
    val pinned = selected != null && pinnedActive
    val anyProcessing = products.any { it.state == ItemState.PROCESSING }

    // Chip content
    val chipLabel: String
    val chipValue: String
    val chipDim: Boolean
    when {
        anyProcessing -> { chipLabel = "ANALYSING"; chipValue = "Reading…"; chipDim = true }
        pinned && selected != null -> {
            chipLabel = when {
                selected.price == null -> "PRICE NEEDED"
                selected.needsReview || selected.lowConfidencePrice -> "CHECK THIS PRICE"
                selected.priceSource == PriceSource.MANUAL -> "YOUR PRICE"
                selected.priceSource == PriceSource.PAGE -> "LISTED PRICE"
                else -> "PRICE DETECTED"
            }
            chipValue = selected.price?.let { Money.format(it) } ?: "Tap to add"
            chipDim = selected.price == null || selected.needsReview || selected.lowConfidencePrice
        }
        phase == CameraPhase.OFF -> { chipLabel = "CAMERA OFF"; chipValue = "₹ – – –"; chipDim = true }
        live != null -> { chipLabel = "PRICE DETECTED"; chipValue = Money.format(live!!.value); chipDim = false }
        else -> { chipLabel = "LOOKING FOR A PRICE"; chipValue = "₹ – – –"; chipDim = true }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(PP.Charcoal)) {
        val maxW = maxWidth
        val maxH = maxHeight
        val frameW = maxW * 0.67f
        val frameH = (maxH * 0.31f).coerceIn(190.dp, 270.dp)

        // The preview view stays in the tree for the whole life of the screen (no re-attaching); when the camera is off it
        // is hidden under the opaque static background below, so a frozen last frame can never show through.
        AndroidView(
            factory = { previewView }, modifier = Modifier.fillMaxSize(),
            update = { it.visibility = if (active) android.view.View.VISIBLE else android.view.View.INVISIBLE }
        )
        val scrimAlpha = if (active) 0.62f else 1f
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(Color(0xFF3B4A43).copy(alpha = scrimAlpha), Color(0xFF1B2421).copy(alpha = scrimAlpha)),
                    center = Offset.Unspecified, radius = 1400f
                )
            )
        )

        Column(Modifier.fillMaxSize()) {
            // Top bar
            Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 4.dp).height(48.dp)) {
                Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                    CircleButton(Icons.Rounded.Menu, "Menu") { menuOpen = true }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = PP.Surface) {
                            DropdownMenuItem(text = { Text("Financial profile", color = PP.TextHi) }, onClick = { menuOpen = false; onOpenProfile() })
                            DropdownMenuItem(
                                text = { Text("Clear all scanned items", color = PP.TextHi) },
                                enabled = products.isNotEmpty(),
                                onClick = { menuOpen = false; confirmClear = true }
                            )
                        }
                    }
                    CircleButton(Icons.Rounded.ArrowBack, "Back") { activity?.moveTaskToBack(true) }
                }
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CropFree, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Scan price", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(Modifier.align(Alignment.CenterEnd)) {
                    CircleButton(null, "Help", label = "?") { helpOpen = true }
                }
            }
            if (learningConfirmed > 0) {
                Text(
                    "Learning from $learningConfirmed confirmed price${if (learningConfirmed == 1) "" else "s"}" +
                        if (learningCorrections > 0) " · $learningCorrections corrected" else "",
                    color = Color.White.copy(alpha = .62f), fontSize = 10.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                )
            }

            // Scan frame
            Box(Modifier.fillMaxWidth().padding(top = 14.dp), contentAlignment = Alignment.TopCenter) {
                Box(
                    Modifier.width(frameW).height(frameH).onGloballyPositioned { c ->
                        val b = c.boundsInRoot()
                        val rw = view.width; val rh = view.height
                        if (rw > 0 && rh > 0) controller.focus.set(FocusSpec(rw, rh, FocusBox(b.left, b.top, b.right, b.bottom)))
                    },
                    contentAlignment = Alignment.Center
                ) {
                    ScanFrameOverlay(Modifier.fillMaxSize(), animated = active)
                    PriceChip(
                        chipLabel, chipValue, chipDim, anyProcessing,
                        onClick = if (pinned && selected != null) { { editingId = selected.id } } else null
                    )
                }
            }
            Text(
                if (phase == CameraPhase.OFF) "Camera is off - switch it on to scan, or add a photo" else "Point the camera at a price tag", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )

            // Capture row
            Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(64.dp).padding(horizontal = 20.dp)) {
                Box(Modifier.align(Alignment.CenterStart)) {
                    CircleButton(Icons.Outlined.PhotoLibrary, "Choose photos from gallery", size = 44.dp) { openGallery() }
                }
                Box(
                    Modifier.align(Alignment.Center).size(60.dp).border(3.dp, Color.White.copy(alpha = if (active) 1f else 0.55f), CircleShape).padding(5.dp)
                        .clip(CircleShape).background(Color.White.copy(alpha = if (active) 1f else 0.55f))
                        .clickable(enabled = !capturing, role = Role.Button) {
                            when {
                                !cameraOn -> scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar("The camera is off. Switch it on to take a photo, or choose one from your gallery.") }
                                !CameraState.canCapture(phase, flow) -> scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar("The camera isn't ready yet.") }
                                else -> {
                                    capturing = true
                                    scope.launch {
                                        try { vm.addCapturedImage(controller.capture()) }
                                        catch (e: Exception) { snack.showSnackbar("Couldn't take the photo. Try again.") }
                                        finally { capturing = false }
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (capturing) CircularProgressIndicator(Modifier.size(24.dp), color = PP.AccentDark, strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.CameraAlt, "Capture", tint = if (active) PP.Ink else PP.NavIdle)
                }
                // ON/OFF slider right next to the capture button
                CameraToggle(
                    on = cameraOn,
                    onToggle = { want ->
                        if (toggleGuard.accept(android.os.SystemClock.elapsedRealtime())) {
                            vm.setCameraOn(want)
                            if (want) { bindFailed = false; retryTick++ }
                        }
                    },
                    modifier = Modifier.align(Alignment.Center).offset(x = 30.dp + 14.dp + 26.dp)
                )
            }

            // Tray or camera status
            Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
                if (products.isNotEmpty()) {
                    ProductTray(
                        products, assessment, selectedId,
                        onSelect = { vm.select(it.id); editingId = it.id },
                        onRemove = { vm.removeProduct(it.id) },
                        onAdd = { openGallery() },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                } else {
                    CameraStatus(phase, onAllow = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        onSettings = { openSettings() }, onRetry = { bindFailed = false; retryTick++ })
                }
            }
            if (products.isNotEmpty()) {
                CameraStatus(phase, onAllow = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onSettings = { openSettings() }, onRetry = { bindFailed = false; retryTick++ }, compact = true)
            }

            VerdictCard(
                ui, modifier = Modifier.padding(horizontal = 13.dp).padding(bottom = 10.dp),
                onClick = when (ui.action) {
                    VerdictAction.PROFILE -> onOpenProfile
                    VerdictAction.DETAILS -> { { detailsOpen = true } }
                    VerdictAction.NONE -> null
                }
            )
        }

        // Crop editor / "reading your cropped image" cover the scanner; the camera underneath stays as it is.
        val f = flow
        if (f is FlowState.Cropping || f is FlowState.Processing) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {       // swallow touches: nothing below reacts
                when (f) {
                    is FlowState.Cropping -> CropScreen(
                        job = f.job, loadBitmap = { vm.cropBitmap(it) },
                        onConfirm = { l, t, r, b -> vm.confirmCrop(f.job.key, l, t, r, b) },
                        onCancel = { vm.cancelCrop(f.job.key) },
                        onRetake = if (f.job.fromCamera) ({ vm.retake(f.job.key) }) else null
                    )
                    is FlowState.Processing -> ProcessingOverlay(f.previewPath) { vm.cancelProcessing(f.productId) }
                    else -> Unit
                }
            }
        }

        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 150.dp))
    }

    // Sheets and dialogs
    val reviewId = (flow as? FlowState.Reviewing)?.productId
    val editing = products.firstOrNull { it.id == (reviewId ?: editingId) }
    if (editing != null) {
        val isReview = reviewId != null
        EditProductSheet(
            editing, app.categories, assessment?.line(editing.id),
            review = isReview || editing.needsReview,
            onSave = { n, p, c, s, pr, v -> vm.updateProduct(editing.id, n, p, c, s, pr, v); if (isReview) vm.reviewClosed(); editingId = null },
            onRemove = { vm.removeProduct(editing.id); editingId = null },
            onDismiss = { if (isReview) vm.reviewClosed(); editingId = null },
            advice = advices[editing.id], emiBusy = editing.id in emiBusy, emiNotes = emiNotes[editing.id].orEmpty(),
            onEmiAvailable = { vm.setEmiAvailable(editing.id, it) },
            onEmiUpload = { emiTargetId = editing.id; emiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onEmiCapture = { captureEmi(editing.id) },
            onEmiSave = { vm.saveEmiOption(editing.id, it) },
            onEmiRemove = { vm.removeEmiOption(editing.id, it) }
        )
    } else if (editingId != null) editingId = null

    val a = assessment
    if (detailsOpen && a != null) VerdictDetailsSheet(
        a, products, ui, onDismiss = { detailsOpen = false }, advices = advices,
        combinedWarning = profile?.let { PurchaseAdvisor.combinedEmiWarning(it, advices.values) }
    )
    else if (detailsOpen) detailsOpen = false
    if (helpOpen) HelpDialog { helpOpen = false }

    duplicate?.let { d ->
        AlertDialog(
            onDismissRequest = {}, containerColor = PP.Surface, titleContentColor = PP.TextHi, textContentColor = PP.TextLo,
            title = { Text("Already in your list") },
            text = { Text("\"${d.existingName}\" looks like the same photo or link. Add it again as a separate item?") },
            confirmButton = { TextButton(onClick = { vm.resolveDuplicate(d.token, true); duplicate = null }) { Text("Add again", color = PP.Accent) } },
            dismissButton = { TextButton(onClick = { vm.resolveDuplicate(d.token, false); duplicate = null }) { Text("Skip it", color = PP.TextLo) } }
        )
    }
    possibleDup?.let { d ->
        AlertDialog(
            onDismissRequest = {}, containerColor = PP.Surface, titleContentColor = PP.TextHi, textContentColor = PP.TextLo,
            title = { Text("Possible duplicate") },
            text = { Text("This looks the same as \"${d.otherName}\" (same name and price). Keep both?") },
            confirmButton = { TextButton(onClick = { vm.resolvePossibleDuplicate(d.productId, true); possibleDup = null }) { Text("Keep both", color = PP.Accent) } },
            dismissButton = { TextButton(onClick = { vm.resolvePossibleDuplicate(d.productId, false); possibleDup = null }) { Text("Remove new one", color = PP.TextLo) } }
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false }, containerColor = PP.Surface, titleContentColor = PP.TextHi, textContentColor = PP.TextLo,
            title = { Text("Clear all items?") },
            text = { Text("This removes every scanned product and its saved photo from this device.") },
            confirmButton = { TextButton(onClick = { vm.clearAll(); confirmClear = false }) { Text("Clear", color = PP.Error) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel", color = PP.TextLo) } }
        )
    }
}

@Composable
private fun CameraStatus(
    state: CameraPhase, onAllow: () -> Unit, onSettings: () -> Unit, onRetry: () -> Unit, compact: Boolean = false
) {
    val msg: String
    val action: Pair<String, () -> Unit>?
    when (state) {
        CameraPhase.NO_PERMISSION -> { msg = "Camera access is needed to read price tags. You can still add photos from your gallery."; action = "Allow camera" to onAllow }
        CameraPhase.DENIED_FOREVER -> { msg = "Camera access is turned off. Enable it in Settings, or add photos from your gallery."; action = "Open settings" to onSettings }
        CameraPhase.ERROR -> { msg = "The camera couldn't start. It may be in use by another app."; action = "Try again" to onRetry }
        CameraPhase.NO_CAMERA -> { msg = "This device has no usable camera. Add photos from your gallery instead."; action = null }
        else -> return
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = if (compact) 4.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(msg, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, textAlign = TextAlign.Center)
        if (action != null) {
            Button(
                onClick = action.second, modifier = Modifier.padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PP.Accent, contentColor = PP.Ink)
            ) { Text(action.first, fontWeight = FontWeight.SemiBold) }
        }
    }
}
