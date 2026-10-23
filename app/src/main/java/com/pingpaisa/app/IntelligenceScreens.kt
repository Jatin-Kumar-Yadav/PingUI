package com.pingpaisa.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caverock.androidsvg.SVG
import kotlin.math.hypot
import androidx.core.graphics.PathParser
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

@Composable
internal fun SvgArtwork(asset: String, modifier: Modifier) {
    val context = LocalContext.current
    val svg = remember(asset) {
        // AndroidSVG 1.4 parses pathLength but does not normalize dash lengths.
        // Adapt the renderer's input, never the original bundled artwork or geometry.
        val document = context.assets.open("svg/charts/$asset.svg").use {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it)
        }
        val paths = document.getElementsByTagName("path")
        for (index in 0 until paths.length) {
            val element = paths.item(index) as org.w3c.dom.Element
            val declared = element.getAttribute("pathLength").toFloatOrNull() ?: continue
            val path = PathParser.createPathFromPathData(element.getAttribute("d")) ?: continue
            val measure = android.graphics.PathMeasure(path, false)
            var length = 0f
            do { length += measure.length } while (measure.nextContour())
            if (declared <= 0f) continue
            val ratio = length / declared
            val dash = element.getAttribute("stroke-dasharray")
            if (dash.isNotBlank()) {
                element.setAttribute("stroke-dasharray", dash.split(Regex("[,\\s]+"))
                    .joinToString(" ") { (it.toFloat() * ratio).toString() })
            }
            element.getAttribute("stroke-dashoffset").toFloatOrNull()?.let {
                element.setAttribute("stroke-dashoffset", (it * ratio).toString())
            }
        }
        val writer = StringWriter()
        TransformerFactory.newInstance().newTransformer().transform(DOMSource(document), StreamResult(writer))
        SVG.getFromString(writer.toString())
    }
    Canvas(modifier) {
        val canvas = drawContext.canvas.nativeCanvas
        canvas.save()
        svg.renderToCanvas(canvas, android.graphics.RectF(0f, 0f, size.width, size.height))
        canvas.restore()
    }
}

@Composable
internal fun ScanScreen(menu: () -> Unit, back: () -> Unit) {
    var afford by rememberSaveable { mutableStateOf(true) }
    val transition = rememberInfiniteTransition(label = "Price scanning")
    val scanY by transition.animateFloat(30f, 245f,
        infiniteRepeatable(tween(1400, easing = CubicBezierEasing(.42f, 0f, .58f, 1f)), RepeatMode.Reverse),
        label = "Scan line")
    BoxWithConstraints(Modifier.fillMaxSize().background(Clay.Dark)) {
        val canvasHeight = (maxHeight - 66.dp).coerceAtLeast(450.dp)
        Box(Modifier.fillMaxWidth().height(canvasHeight).drawWithCache {
            val center = Offset(size.width * .25f, size.height * .18f)
            val brush = Brush.radialGradient(
                0f to Color(0xFF79847F), .48f to Color(0xFF35403B), 1f to Color(0xFF1E2824),
                center = center, radius = hypot(size.width * .75f, size.height * .82f))
            onDrawBehind { drawRect(brush) }
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MenuButton(menu, dark = true)
                Box(Modifier.size(36.dp).background(Color.White.copy(alpha = .12f), CircleShape)
                    .clickable(onClick = back), contentAlignment = Alignment.Center) {
                    NativeIcon(R.drawable.ic_pingpaisa_back, 18.dp, Color.White)
                }
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally)) {
                    NativeIcon(R.drawable.ic_pingpaisa_scan, tint = Color.White)
                    NativeText("Scan price", 11.sp, Color.White, FontWeight.Bold)
                }
                Box(Modifier.size(36.dp).background(Color.White.copy(alpha = .12f), CircleShape)
                    .clickable { afford = !afford }, contentAlignment = Alignment.Center) { NativeText("?", 16.sp, Color.White) }
            }
            Box(Modifier.widthIn(max = 296.dp).fillMaxWidth().height(281.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val corner = 48.dp.toPx()
                    val radius = 18.dp.toPx()
                    val w = size.width
                    val h = size.height
                    val p = Path().apply {
                        moveTo(0f, corner); lineTo(0f, radius); quadraticTo(0f, 0f, radius, 0f); lineTo(corner, 0f)
                        moveTo(w-corner, 0f); lineTo(w-radius, 0f); quadraticTo(w, 0f, w, radius); lineTo(w, corner)
                        moveTo(0f, h-corner); lineTo(0f, h-radius); quadraticTo(0f, h, radius, h); lineTo(corner, h)
                        moveTo(w-corner, h); lineTo(w-radius, h); quadraticTo(w, h, w, h-radius); lineTo(w, h-corner)
                    }
                    drawPath(p, Color.White.copy(alpha = .85f), style = Stroke(3.dp.toPx()))
                }
                Box(Modifier.align(Alignment.TopCenter).offset(y = scanY.dp).padding(horizontal = 18.dp)
                    .fillMaxWidth().height(2.dp).claySurface(RectangleShape, Color(0xFFBDDBB5), ClayShadow.Glow))
                Column(Modifier.riseIn(350, 10f).background(Color(0xFF1E2924).copy(alpha = .68f), RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    NativeText("PRICE DETECTED", 6.sp, Color(0xFFC6CCC9), tracking = .84.sp)
                    NativeText("₹18,999", 21.sp, Color.White, FontWeight.Bold, Modifier.padding(top = 3.dp))
                }
            }
            NativeText("Point the camera at a price tag", 8.sp, Color(0xFFC4CAC7))
            Box(Modifier.padding(top = 5.dp).size(62.dp).border(2.dp, Color.White.copy(alpha = .7f), CircleShape)
                .padding(6.dp).background(Clay.Cream, CircleShape).clickable { afford = !afford },
                contentAlignment = Alignment.Center) { NativeIcon(R.drawable.ic_pingpaisa_camera, 24.dp, Color(0xFF4D655B)) }
            Spacer(Modifier.height(250.dp))
        }
        Column(Modifier.align(Alignment.BottomCenter).riseIn(550, 28f).padding(start = 14.dp, end = 14.dp, bottom = 68.dp)
            .fillMaxWidth().claySurface(RoundedCornerShape(29.dp), shadow = ClayShadow.Verdict,
                gradient = if (afford) Color(0xFFDCEBD7) to Color(0xFFB6D0AF) else Color(0xFFF6DDDD) to Color(0xFFEEB8B8))
            .padding(17.dp)) {
            val ink = Color(0xFF40554C)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(40.dp).background(Color.White.copy(alpha = .55f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center) { NativeIcon(if (afford) R.drawable.ic_pingpaisa_check else R.drawable.ic_pingpaisa_lock, tint = ink) }
                Column {
                    Box(Modifier.height(24.dp), contentAlignment = Alignment.BottomStart) {
                        NativeText("YOUR VERDICT", 6.sp, ink, tracking = .78.sp, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    NativeText(if (afford) "Yes, you can afford it" else "Better to wait", 15.sp, ink, modifier = Modifier.padding(vertical = 3.dp))
                }
            }
            NativeText(if (afford) "This purchase fits your plan without touching savings." else "This would push your shopping budget over the safe limit.",
                8.sp, ink, modifier = Modifier.padding(vertical = 10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("₹17,320 left in monthly budget","Emergency fund stays protected",
                    "Health score impact: ${if (afford) "−2" else "−11"} points").forEach { text ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NativeText("✓", 7.sp, ink, FontWeight.ExtraBold)
                        NativeText(text, 7.sp, ink)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChatScreen(menu: () -> Unit, back: () -> Unit, navigate: (Screen) -> Unit) {
    var message by rememberSaveable { mutableStateOf("") }
    var sent by rememberSaveable { mutableStateOf(listOf<String>()) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(70.dp)
                .claySurface(RoundedCornerShape(bottomStart = 27.dp, bottomEnd = 27.dp), shadow = ClayShadow.None,
                    gradient = Color(0xFFDED8EF) to Color(0xFFBEB4DF)).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MenuButton(menu)
                Box(Modifier.size(32.dp).background(Color.White.copy(alpha = .45f), CircleShape).clickable(onClick = back),
                    contentAlignment = Alignment.Center) { NativeIcon(R.drawable.ic_pingpaisa_back, 17.dp) }
                Column(Modifier.weight(1f)) {
                    NativeText("Paisa AI", 11.sp)
                    Row(Modifier.height(24.dp).offset(y = 3.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.size(5.dp).background(Color(0xFF6C9E83), CircleShape))
                        NativeText("Private • On-device", 6.sp, Color(0xFF746F7C))
                    }
                }
                Box(Modifier.size(32.dp).background(Color.White.copy(alpha = .45f), CircleShape)
                    .clickable { message = "How can Paisa AI help?" }, contentAlignment = Alignment.Center) { NativeText("⋯", 16.sp) }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 170.dp)) {
                NativeText("TODAY", 6.sp, Color(0xFF969895), modifier = Modifier.align(Alignment.CenterHorizontally)
                    .background(Color(0xFFEBE8E3), CircleShape).padding(horizontal = 9.dp, vertical = 5.dp))
                Spacer(Modifier.height(14.dp))
                ChatBubble("Hi Aanya! I’ve looked at your June spending. What would you like to know?", time = "9:41 AM")
                Spacer(Modifier.height(12.dp))
                ChatBubble("Can I spend ₹20,000 on a new phone this month?", user = true)
                Spacer(Modifier.height(12.dp))
                ChatBubble("", rich = true)
                sent.forEach {
                    Spacer(Modifier.height(12.dp))
                    ChatBubble(it, user = true)
                }
            }
        }
        Row(Modifier.align(Alignment.BottomStart).padding(bottom = 112.dp).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Clay.Cream)))
            .horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Where can I save?","Explain my score","June forecast").forEachIndexed { i, label ->
                NativeText(label, 7.sp, Color(0xFF766B95), modifier = Modifier.background(Color(0xFFF7F3FB), CircleShape)
                    .border(1.dp, Color(0xFFD8D0E6), CircleShape).clickable {
                        when (i) { 1 -> navigate(Screen.Health); 2 -> navigate(Screen.Forecast); else -> message = label }
                    }.padding(horizontal = 10.dp, vertical = 7.dp))
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 66.dp)
            .fillMaxWidth().height(48.dp).claySurface(CircleShape, Color(0xFFF5F1EC), ClayShadow.Raised).padding(5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(37.dp).clickable { navigate(Screen.Entry) }, contentAlignment = Alignment.Center) { NativeIcon(R.drawable.ic_pingpaisa_plus) }
            BasicTextField(message, { message = it }, Modifier.weight(1f),
                textStyle = TextStyle(fontFamily = PingPaisaFont, fontSize = 8.sp, color = Clay.Ink, lineHeight = 12.sp),
                decorationBox = { inner ->
                    if (message.isEmpty()) NativeText("Ask about your money…", 8.sp, Color(0xFFA2A29F))
                    inner()
                })
            Box(Modifier.size(37.dp).background(Color(0xFF789F91), CircleShape)
                .clickable(onClickLabel = "Send message") { if (message.isNotBlank()) { sent = sent + message; message = "" } },
                contentAlignment = Alignment.Center) { NativeText("↑", 16.sp, Color.White) }
        }
    }
}

@Composable
private fun ColumnScope.ChatBubble(text: String, user: Boolean = false, rich: Boolean = false, time: String = "9:42 AM") {
    Box(Modifier.fillMaxWidth(.84f).align(if (user) Alignment.End else Alignment.Start).riseIn(if (user) 250 else 150, 10f)) {
        Row(Modifier.fillMaxWidth().claySurface(
            RoundedCornerShape(topStart = 19.dp, topEnd = 19.dp, bottomStart = if (user) 19.dp else 7.dp, bottomEnd = if (user) 7.dp else 19.dp),
            if (user) Color(0xFFC5D9C0) else Color(0xFFF9F6FB)).padding(start = 12.dp, end = 12.dp, top = 11.dp, bottom = if (rich) 13.dp else 11.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!user) NativeIcon(if (rich) R.drawable.ic_pingpaisa_brain else R.drawable.ic_pingpaisa_sparkle,
                if (rich) 15.dp else 14.dp, Color(0xFF8074A8))
            Column(Modifier.weight(1f)) {
                if (rich) {
                    NativeText("Yes — but ₹18,000 is safer.", 9.sp, weight = FontWeight.Bold, lineHeight = 13.95.sp)
                    NativeText("At ₹20,000, you’ll still save ₹12,320 this month, though your shopping budget will be 12% over.",
                        9.sp, lineHeight = 13.95.sp)
                    // The reference grid contributes a 6dp row gap as well as the 8dp metric margin.
                    Row(Modifier.padding(top = 14.dp).fillMaxWidth().background(Color(0xFFEEEAF6), RoundedCornerShape(13.dp)).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { NativeText("After purchase", 6.sp); NativeText("₹12,320", 13.sp, weight = FontWeight.Bold) }
                        NativeText("Safe", 7.sp, Color(0xFF668572), modifier = Modifier.background(Color(0xFFDCE9D8), CircleShape).padding(horizontal = 7.dp, vertical = 4.dp))
                    }
                } else NativeText(text, 9.sp, lineHeight = 13.95.sp)
            }
        }
        NativeText(time, 5.sp, Color(0xFFAAAAAA), modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-8).dp, y = 12.dp))
    }
}

@Composable
internal fun ForecastScreen(menu: () -> Unit, back: () -> Unit) {
    ScreenScroll(padded = false) {
        Column(verticalArrangement = Arrangement.spacedBy((-11).dp)) {
            Column(Modifier.fillMaxWidth().heightIn(min = 145.dp).claySurface(
                RoundedCornerShape(bottomStart = 43.dp, bottomEnd = 43.dp), shadow = ClayShadow.None,
                gradient = Color(0xFFE5E0F3) to Color(0xFFC9C0E8)).padding(horizontal = 20.dp, vertical = 10.dp)) {
                NativeHeader("June forecast", "SMART PREDICTIONS", menu, back, action = { ClayBadge(R.drawable.ic_pingpaisa_sparkle, Tone.Cream) })
                NativeText("Powered by your last 6 months of spending", 8.sp, Color(0xFF746E85), modifier = Modifier.padding(top = 4.dp))
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                Column(Modifier.fillMaxWidth().claySurface(RoundedCornerShape(26.dp), Color(0xFFFAF8FC), ClayShadow.Raised).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ClayBadge(R.drawable.ic_pingpaisa_brain, Tone.Lavender)
                        Column { NativeText("AI INSIGHT", 7.sp, Color(0xFF8B82A2), tracking = .84.sp)
                            NativeText("You’re on track", 10.sp, weight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp)) }
                    }
                    NativeText("₹54,200", 29.sp, tracking = (-1.45).sp, modifier = Modifier.padding(top = 13.dp))
                    NativeText("Projected total spend by 30 June", 8.sp, Color(0xFF949095), modifier = Modifier.padding(top = 1.dp, bottom = 12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        MiniProgress(.92f, Color(0xFF9589C3), Modifier.weight(1f), 5.dp)
                        NativeText("92% confidence", 7.sp, Color(0xFF77717F))
                    }
                }
                Spacer(Modifier.height(13.dp))
                Column(Modifier.fillMaxWidth().heightIn(min = 245.dp).claySurface(RoundedCornerShape(26.dp), shadow = ClayShadow.Raised).padding(14.dp)) {
                    Row { NativeText("Spend trajectory", 15.sp, tracking = (-.375).sp, modifier = Modifier.weight(1f)); NativeText("₹60K", 10.sp, Color(0xFF719487), FontWeight.ExtraBold) }
                    SvgArtwork("spend_trajectory", Modifier.fillMaxWidth().height(155.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf("1 Jun","Today","30 Jun").forEach { NativeText(it, 7.sp, Color(0xFF9A9B98)) } }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().claySurface(RoundedCornerShape(20.dp), Color(0xFFF8E2E0)).padding(11.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    ClayBadge(R.drawable.ic_pingpaisa_sparkle, Tone.Pink)
                    Column(Modifier.weight(1f)) { NativeText("Dining spike detected", 9.sp, weight = FontWeight.Bold)
                        NativeText("Food spend may cross budget by ₹2,100.", 7.sp, Color(0xFF917C79), modifier = Modifier.padding(top = 3.dp)) }
                    NativeIcon(R.drawable.ic_pingpaisa_chevron, 18.dp, Color(0xFF9D8581))
                }
            }
        }
    }
}

@Composable
internal fun HeatmapScreen(menu: () -> Unit, back: () -> Unit) {
    var month by rememberSaveable { mutableStateOf("June") }
    var selected by rememberSaveable { mutableIntStateOf(17) }
    val levels = listOf(0,1,0,2,0,3,1,0,2,4,3,0,1,2,0,1,3,4,2,0,0,1,2,3,4,1,0,2,3,1,2,0,4,3,1)
    val colors = listOf(Color(0xFFEFEDE8),Color(0xFFD9E5D5),Clay.Sage,Color(0xFFEFC5A9),Color(0xFFE9948F))
    ScreenScroll {
        NativeHeader("Your money map", "SPENDING RHYTHM", menu, back, action = { RoundAction(R.drawable.ic_pingpaisa_calendar, "Calendar") {} })
        Spacer(Modifier.height(18.dp))
        SegmentedTabs(listOf("April","May","June"), month, { month = it })
        Spacer(Modifier.height(17.dp))
        Column(Modifier.fillMaxWidth().claySurface(RoundedCornerShape(27.dp), shadow = ClayShadow.Raised).padding(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { NativeText("$month activity", 7.sp, Color(0xFF999999)); NativeText("₹42,680", 17.sp, weight = FontWeight.Bold) }
                NativeText("23 active days", 7.sp, Color(0xFF658274), FontWeight.ExtraBold,
                    Modifier.background(Color(0xFFE7EEE4), CircleShape).padding(horizontal = 8.dp, vertical = 6.dp))
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 3.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("M","T","W","T","F","S","S").forEach { NativeText(it, 7.sp, Color(0xFFA0A19E), modifier = Modifier.weight(1f), align = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                levels.chunked(7).forEachIndexed { week, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        row.forEachIndexed { day, level ->
                            val index = week * 7 + day
                            Box(Modifier.weight(1f).aspectRatio(1f).background(colors[level], RoundedCornerShape(9.dp))
                                .then(if (selected == index) Modifier.border(2.dp, Color.White, RoundedCornerShape(9.dp)) else Modifier)
                                .clickable { selected = index }, contentAlignment = Alignment.Center) {
                                NativeText(if (index < 2) "" else "${index - 1}", 7.sp, Color(0xFF59665F))
                            }
                        }
                    }
                }
            }
            Row(Modifier.align(Alignment.End).padding(top = 13.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                NativeText("Less", 6.sp, Color(0xFFAAAAAA))
                colors.forEach { Box(Modifier.size(10.dp).background(it, RoundedCornerShape(3.dp))) }
                NativeText("More", 6.sp, Color(0xFFAAAAAA))
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().claySurface(RoundedCornerShape(21.dp),
            gradient = Color(0xFFF5D6D1) to Color(0xFFF0C5C2)).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                NativeText(if (selected == 17) "WED, 18 JUNE" else "${selected + 1} ${month.uppercase()}", 7.sp, Color(0xFF967572), tracking = .7.sp)
                NativeText(if (selected == 17) "Highest spend day" else "Daily spending", 10.sp, weight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            }
            NativeText("₹6,840", 16.sp)
        }
        Row(Modifier.padding(top = 13.dp).padding(11.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ClayBadge(R.drawable.ic_pingpaisa_sparkle, Tone.Lavender)
            NativeText("You spend 22% more on weekends, mostly on dining and travel.", 8.sp, Color(0xFF747B77))
        }
    }
}

@Composable
internal fun HealthScreen(menu: () -> Unit, back: () -> Unit) {
    ScreenScroll(padded = false) {
        Column(verticalArrangement = Arrangement.spacedBy((-16).dp)) {
            Column(Modifier.fillMaxWidth().heightIn(min = 358.dp).claySurface(
                RoundedCornerShape(bottomStart = 47.dp, bottomEnd = 47.dp), shadow = ClayShadow.None,
                gradient = Color(0xFFEDF1E8) to Color(0xFFD0DFCB), angle = 155f)
                .drawWithCache {
                    val outline = ClayBlobShape.createOutline(androidx.compose.ui.geometry.Size(190.dp.toPx(),180.dp.toPx()),layoutDirection,this) as Outline.Generic
                    val path = Path().apply { addPath(outline.path,Offset(size.width - 113.dp.toPx(),22.dp.toPx())) }
                    onDrawBehind { drawPath(path,Clay.Lavender.copy(alpha = .42f)) }
                }.padding(start = 20.dp, end = 20.dp, top = 10.dp)) {
                NativeHeader("Financial health", "JUNE 2025", menu, back)
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth().height(216.dp)) {
                    SvgArtwork("health_gauge", Modifier.align(Alignment.TopCenter).widthIn(max = 285.dp).fillMaxWidth().height(164.dp))
                    Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 17.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        NativeText("782", 43.sp, weight = FontWeight.Bold, tracking = (-3.01).sp)
                        NativeText("EXCELLENT", 7.sp, Color(0xFF608173), FontWeight.ExtraBold, tracking = 1.05.sp)
                    }
                }
                NativeText("Up 24 points since May", 8.sp, Color(0xFF6B7C74), modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                Column(Modifier.fillMaxWidth().claySurface(RoundedCornerShape(26.dp), shadow = ClayShadow.Raised).padding(17.dp)) {
                    NativeText("What shapes your score", 12.sp, modifier = Modifier.padding(bottom = 13.dp))
                    listOf("Savings rate" to 88,"Budget control" to 74,"Bill consistency" to 94,"Debt health" to 68).forEachIndexed { i, (label, value) ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 5.dp)) {
                            NativeText(label, 8.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f)); NativeText("$value/100", 8.sp, Color(0xFF999999))
                        }
                        MiniProgress(value / 100f, listOf(Color(0xFF8EAE88),Color(0xFFEDC093),Color(0xFFA89CCC),Color(0xFFE7AAA8))[i])
                        Spacer(Modifier.height(11.dp))
                    }
                }
                Row(Modifier.padding(top = 12.dp).fillMaxWidth().background(Color(0xFFE7E2F3), RoundedCornerShape(21.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    ClayBadge(R.drawable.ic_pingpaisa_sparkle, Tone.Lavender)
                    Column { NativeText("AI RECOMMENDATION", 6.sp, tracking = .6.sp)
                        NativeText("Reduce dining by ₹1,800 to cross an 800 score next month.", 8.sp, lineHeight = 11.6.sp, modifier = Modifier.padding(top = 3.dp)) }
                }
            }
        }
    }
}
