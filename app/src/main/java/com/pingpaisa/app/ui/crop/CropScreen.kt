package com.pingpaisa.app.ui.crop

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingpaisa.app.data.ImageStore
import com.pingpaisa.app.recognition.Box as Rectf
import com.pingpaisa.app.scanner.CropHandle
import com.pingpaisa.app.scanner.CropJob
import com.pingpaisa.app.scanner.CropMath
import com.pingpaisa.app.ui.theme.PP
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Crop editor shown after a capture or a gallery pick. Freeform rectangle: drag inside to move it, drag a corner or an
 * edge to resize it. The picture is drawn "fit" (never stretched); the selection is reported as fractions of the image
 * so the ORIGINAL (full resolution) picture can be cropped.
 */
@Composable
fun CropScreen(
    job: CropJob,
    loadBitmap: suspend (String) -> Bitmap?,
    onConfirm: (left: Float, top: Float, right: Float, bottom: Float) -> Unit,
    onCancel: () -> Unit,
    onRetake: (() -> Unit)?
) {
    BackHandler(onBack = onCancel)
    var loaded by remember(job.key) { mutableStateOf(false) }
    var bitmap by remember(job.key) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(job.key) { bitmap = loadBitmap(job.key); loaded = true }

    Column(Modifier.fillMaxSize().background(Color(0xFF0F1512)).statusBarsPadding().navigationBarsPadding()) {
        Text(
            "Crop to the product and its price", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)
        )
        Text(
            "Leave out ads, other products and other variants. Drag the corners or edges; drag inside to move.",
            color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp)
        )
        val bmp = bitmap
        var sel by remember(job.key, bmp) { mutableStateOf<Rectf?>(null) }
        var shownState by remember(job.key, bmp) { mutableStateOf<Rectf?>(null) }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                !loaded -> CircularProgressIndicator(color = PP.Accent)
                bmp == null -> Text("This image couldn't be opened.", color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
                else -> CropCanvas(bmp, sel, shownState) { s, sh -> sel = s; shownState = sh }
            }
        }

        // Two rows so no label ever has to wrap: small secondary actions on top, one full-width primary action below.
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onRetake != null) OutlinedButton(
                    onClick = onRetake, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) { OneLine("Retake", Color.White) }
                OutlinedButton(
                    onClick = onCancel, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) { OneLine("Cancel", Color.White.copy(alpha = 0.85f)) }
                OutlinedButton(
                    enabled = bmp != null, onClick = { shownState?.let { sh -> sel = sh } },
                    modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) { OneLine("Whole image", if (bmp != null) PP.Accent else Color.Gray) }
            }
            Button(
                enabled = bmp != null && sel != null && shownState != null,
                onClick = {
                    val s = sel; val sh = shownState
                    if (s != null && sh != null && sh.width > 0f && sh.height > 0f) {
                        onConfirm(
                            (s.left - sh.left) / sh.width, (s.top - sh.top) / sh.height,
                            (s.right - sh.left) / sh.width, (s.bottom - sh.top) / sh.height
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PP.Accent, contentColor = PP.Ink)
            ) { Text("Crop & Continue", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, softWrap = false) }
        }
    }
}

/** Button label that stays on one line and shrinks slightly instead of wrapping letter by letter. */
@Composable
private fun OneLine(text: String, color: Color) =
    Text(text, color = color, fontSize = 13.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)

@Composable
private fun CropCanvas(bmp: Bitmap, sel: Rectf?, shown: Rectf?, report: (Rectf, Rectf) -> Unit) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val fit = remember(bmp, wPx, hPx) { CropMath.fitInside(wPx, hPx, bmp.width, bmp.height) }
        val slop = with(density) { 28.dp.toPx() }
        val minSize = with(density) { 48.dp.toPx() }
        LaunchedEffect(fit) { if (sel == null || shown != fit) report(CropMath.defaultRect(fit), fit) }

        Image(bitmap = bmp.asImageBitmap(), contentDescription = "Image to crop", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        val r = sel
        val latest by rememberUpdatedState(sel)          // gesture callbacks must see the newest selection
        var active by remember(bmp) { mutableStateOf(CropHandle.NONE) }
        Canvas(
            Modifier.fillMaxSize().pointerInput(fit) {
                detectDragGestures(
                    onDragStart = { o -> active = latest?.let { CropMath.hitTest(it, o.x, o.y, slop) } ?: CropHandle.NONE },
                    onDragEnd = { active = CropHandle.NONE },
                    onDragCancel = { active = CropHandle.NONE }
                ) { change, drag ->
                    val cur = latest
                    if (cur != null && active != CropHandle.NONE) {
                        report(CropMath.drag(cur, active, drag.x, drag.y, fit, minSize), fit)
                        change.consume()
                    }
                }
            }
        ) {
            val rect = r ?: return@Canvas
            val dim = Color.Black.copy(alpha = 0.55f)
            drawRect(dim, Offset(0f, 0f), Size(size.width, rect.top))
            drawRect(dim, Offset(0f, rect.bottom), Size(size.width, size.height - rect.bottom))
            drawRect(dim, Offset(0f, rect.top), Size(rect.left, rect.height))
            drawRect(dim, Offset(rect.right, rect.top), Size(size.width - rect.right, rect.height))
            drawRect(Color.White, Offset(rect.left, rect.top), Size(rect.width, rect.height), style = Stroke(2.dp.toPx()))
            val g = Color.White.copy(alpha = 0.35f)
            for (i in 1..2) {
                val x = rect.left + rect.width * i / 3f
                val y = rect.top + rect.height * i / 3f
                drawLine(g, Offset(x, rect.top), Offset(x, rect.bottom), 1.dp.toPx())
                drawLine(g, Offset(rect.left, y), Offset(rect.right, y), 1.dp.toPx())
            }
            val hr = 7.dp.toPx()
            listOf(
                rect.left to rect.top, rect.right to rect.top, rect.left to rect.bottom, rect.right to rect.bottom,
                rect.centerX to rect.top, rect.centerX to rect.bottom, rect.left to rect.centerY, rect.right to rect.centerY
            ).forEach { (x, y) -> drawCircle(Color.White, hr, Offset(x, y)); drawCircle(PP.AccentDark, hr * 0.45f, Offset(x, y)) }
        }
    }
}

/** Shows the cropped picture while it is being read, so the user sees exactly what is being analysed. */
@Composable
fun ProcessingOverlay(previewPath: String, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    val bmp by produceState<Bitmap?>(null, previewPath) { value = withContext(Dispatchers.IO) { ImageStore.loadThumbnail(previewPath, 1600) } }
    Column(
        Modifier.fillMaxSize().background(Color(0xFF0F1512)).statusBarsPadding().navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Your cropped image", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            bmp?.let { Image(it.asImageBitmap(), "Cropped image", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
        }
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), color = PP.Accent, strokeWidth = 2.dp)
            Spacer(Modifier.size(10.dp))
            Text("Reading the product and price…", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            Spacer(Modifier.size(12.dp))
            TextButton(onClick = onCancel) { Text("Cancel", color = Color.White.copy(alpha = 0.8f)) }
        }
    }
}
