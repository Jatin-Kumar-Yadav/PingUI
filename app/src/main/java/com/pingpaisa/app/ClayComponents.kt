package com.pingpaisa.app

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlin.math.*

internal enum class Tone(val fill: Color, val ink: Color) {
    Sage(Color(0xFFD8E5D4), Color(0xFF64806C)),
    Teal(Color(0xFFC4DBD3), Color(0xFF527B6D)),
    Peach(Color(0xFFF7DFC7), Color(0xFFA37452)),
    Pink(Color(0xFFF6D8D8), Color(0xFF9E6868)),
    Lavender(Color(0xFFDED8F1), Color(0xFF75699C)),
    Cream(Color(0xFFF4F0EA), Color(0xFF77817C))
}

internal enum class ClayShadow { None, Soft, Raised, Badge, Menu, Primary, Avatar, Pressed, Glow, Verdict }

internal val ClayBlobShape = GenericShape { size, _ ->
    addRoundRect(RoundRect(Rect(Offset.Zero, size),
        CornerRadius(size.width * .42f, size.height * .46f),
        CornerRadius(size.width * .58f, size.height * .40f),
        CornerRadius(size.width * .64f, size.height * .60f),
        CornerRadius(size.width * .36f, size.height * .54f)))
}

@Composable
internal fun NativeText(
    text: String, size: TextUnit = 10.sp, color: Color = Clay.Ink,
    weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier,
    tracking: TextUnit = 0.sp, lineHeight: TextUnit = size * 1.5f,
    maxLines: Int = Int.MAX_VALUE, align: TextAlign? = null
) {
    val styled = buildAnnotatedString {
        text.forEach { character ->
            val fallback = when (character) {
                '✓', '⋯' -> PingPaisaMath
                '⌫' -> PingPaisaSymbols2
                '⌄' -> PingPaisaSymbols
                else -> null
            }
            if (fallback == null) append(character)
            else withStyle(SpanStyle(fontFamily = fallback)) { append(character) }
        }
    }
    Text(styled, modifier, style = TextStyle(
        fontFamily = PingPaisaFont, fontSynthesis = FontSynthesis.None,
        fontSize = size, fontWeight = weight, lineHeight = lineHeight,
        letterSpacing = tracking, color = color, textAlign = align ?: TextAlign.Start,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    ), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun NativeIcon(id: Int, size: Dp = 20.dp, tint: Color = Clay.Ink, description: String? = null) {
    Icon(painterResource(id), description, Modifier.size(size), tint = tint)
}

private data class ShadowLayer(val color: Color, val x: Float, val y: Float, val blur: Float)

// CSS box-shadow blur is a Gaussian sigma of blur/2; Android's mask radius is not sigma.
private fun maskRadius(blur: Float) = ((blur / 2f - .5f) / .57735f).coerceAtLeast(.01f)

internal fun Modifier.claySurface(
    shape: Shape = RoundedCornerShape(20.dp), fill: Color = Color(0xFFFAF8F4),
    shadow: ClayShadow = ClayShadow.Soft, gradient: Pair<Color, Color>? = null,
    angle: Float = 145f, inset: Boolean = false
): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply {
        when (outline) {
            is Outline.Rounded -> addRoundRect(outline.roundRect)
            is Outline.Rectangle -> addRect(outline.rect)
            is Outline.Generic -> addPath(outline.path)
        }
    }
    val layers = when (shadow) {
        ClayShadow.None -> emptyList()
        ClayShadow.Soft -> listOf(
            ShadowLayer(Color(0xFF665C50).copy(alpha = .1f), 4f, 5f, 11f),
            ShadowLayer(Color.White.copy(alpha = .76f), -3f, -3f, 9f))
        ClayShadow.Raised -> listOf(
            ShadowLayer(Color(0xFF685B4D).copy(alpha = .12f), 7f, 8f, 16f),
            ShadowLayer(Color.White.copy(alpha = .82f), -5f, -5f, 12f))
        ClayShadow.Badge -> listOf(ShadowLayer(Color(0xFF43413B).copy(alpha = .09f), 3f, 4f, 8f))
        ClayShadow.Menu -> listOf(ShadowLayer(Color(0xFF435B52).copy(alpha = .18f), 3f, 4f, 8f))
        ClayShadow.Primary -> listOf(ShadowLayer(Color(0xFF47675C).copy(alpha = .25f), 7f, 9f, 16f))
        ClayShadow.Glow -> listOf(ShadowLayer(Color(0xFFB7D9B0), 0f, 0f, 13f))
        ClayShadow.Verdict -> listOf(ShadowLayer(Color.Black.copy(alpha = .3f), 0f, 14f, 30f))
        ClayShadow.Avatar, ClayShadow.Pressed -> emptyList()
    }
    val padding = 32.dp.roundToPx()
    val width = ceil(size.width).toInt().coerceAtLeast(1)
    val height = ceil(size.height).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width + padding * 2, height + padding * 2, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.translate(padding.toFloat(), padding.toFloat())
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    layers.asReversed().forEach { layer ->
        paint.color = layer.color.toArgb()
        paint.maskFilter = BlurMaskFilter(maskRadius(layer.blur * density), BlurMaskFilter.Blur.NORMAL)
        canvas.save()
        canvas.translate(layer.x * density, layer.y * density)
        canvas.drawPath(path.asAndroidPath(), paint)
        canvas.restore()
    }
    val outer = bitmap.asImageBitmap()
    val inner = if (inset || shadow in listOf(ClayShadow.Badge, ClayShadow.Menu, ClayShadow.Avatar, ClayShadow.Pressed)) {
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(image)
        c.clipPath(path.asAndroidPath())
        val inverse = android.graphics.Path(path.asAndroidPath()).apply {
            fillType = android.graphics.Path.FillType.INVERSE_WINDING
            val shift = if (shadow == ClayShadow.Avatar || shadow == ClayShadow.Primary) 3.dp else if (shadow == ClayShadow.Pressed) 4.dp else 2.dp
            offset(shift.toPx(), (if (shadow == ClayShadow.Pressed) 5.dp else shift).toPx())
        }
        paint.color = when (shadow) {
            ClayShadow.Menu -> Color.White.copy(alpha = .45f)
            ClayShadow.Primary, ClayShadow.Avatar -> Color.White.copy(alpha = .25f)
            ClayShadow.Pressed -> Color(0xFF334D44).copy(alpha = .24f)
            else -> Color.White.copy(alpha = .5f)
        }.toArgb()
        val blur = when (shadow) { ClayShadow.Menu -> 4.dp; ClayShadow.Avatar -> 6.dp; ClayShadow.Pressed -> 8.dp; else -> 5.dp }
        paint.maskFilter = BlurMaskFilter(maskRadius(blur.toPx()), BlurMaskFilter.Blur.NORMAL)
        c.drawPath(inverse, paint)
        image.asImageBitmap()
    } else null
    val brush = gradient?.let { (from, to) ->
        val rad = Math.toRadians(angle.toDouble())
        val dx = sin(rad).toFloat()
        val dy = -cos(rad).toFloat()
        val length = abs(size.width * dx) + abs(size.height * dy)
        val half = Offset(dx * length / 2f, dy * length / 2f)
        Brush.linearGradient(listOf(from, to), Offset(size.width / 2f, size.height / 2f) - half, Offset(size.width / 2f, size.height / 2f) + half)
    }
    onDrawBehind {
        drawImage(outer, Offset(-padding.toFloat(), -padding.toFloat()))
        if (brush == null) drawOutline(outline, fill) else drawOutline(outline, brush)
        if (inner != null) clipPath(path) { drawImage(inner) }
    }
}.clip(shape)

@Composable
internal fun ClayBadge(
    icon: Int, tone: Tone = Tone.Sage, small: Boolean = false,
    modifier: Modifier = Modifier, size: Dp = if (small) 29.dp else 43.dp,
    iconSize: Dp = if (small) 16.dp else 20.dp
) {
    Box(modifier.widthIn(min = size).height(size).claySurface(
        RoundedCornerShape(if (small) 10.dp else if (size > 43.dp) 20.dp else 15.dp),
        tone.fill, ClayShadow.Badge
    ), contentAlignment = Alignment.Center) { NativeIcon(icon, iconSize, tone.ink) }
}

@Composable
internal fun MenuButton(onClick: () -> Unit, dark: Boolean = false) {
    Box(Modifier.size(40.dp).claySurface(
        CircleShape, Color.White.copy(alpha = .14f),
        if (dark) ClayShadow.None else ClayShadow.Menu,
        if (dark) null else Color(0xFFC9DDC5) to Color(0xFF9FBD99)
    ).clickable(onClickLabel = "Open screen navigator", onClick = onClick),
        contentAlignment = Alignment.Center) {
        NativeIcon(R.drawable.ic_pingpaisa_menu, 18.dp,
            if (dark) Color(0xFFDCE9E3) else Color(0xFF4F7165))
    }
}

@Composable
internal fun RoundAction(icon: Int, description: String, size: Dp = 40.dp, onClick: () -> Unit) {
    Box(Modifier.size(size).claySurface(CircleShape, Color(0xFFF5F1EC))
        .clickable(onClickLabel = description, onClick = onClick), contentAlignment = Alignment.Center) {
        NativeIcon(icon, if (size < 40.dp) 18.dp else 20.dp)
    }
}

@Composable
internal fun NativeHeader(
    title: String, eyebrow: String? = null, menu: () -> Unit, back: (() -> Unit)? = null,
    modifier: Modifier = Modifier, titleSize: TextUnit = 23.sp,
    action: @Composable () -> Unit = {}
) {
    val pin = LocalPinnedHeader.current
    if (pin != null) SideEffect { pin.menu = menu; pin.back = back; pin.action = action }
    // When pinned, the buttons here only reserve layout; the host draws the live ones on top.
    val buttons = if (pin != null) Modifier.alpha(0f) else Modifier
    Row(modifier.fillMaxWidth().heightIn(min = 44.dp).then(if (pin != null) Modifier.onGloballyPositioned {
        val p = it.positionInRoot()
        pin.headerRoot = Offset(p.x, p.y + pin.scrollOffset())
        pin.headerSize = it.size
    } else Modifier), verticalAlignment = Alignment.CenterVertically) {
        Box(buttons) { MenuButton(menu) }
        Spacer(Modifier.width(9.dp))
        if (back != null) {
            Box(buttons) { BackButton(back) }
            Spacer(Modifier.width(9.dp))
        }
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) {
                // Inline CSS labels occupy the inherited 24px line box.
                Box(Modifier.height(21.dp), contentAlignment = Alignment.CenterStart) {
                    NativeText(eyebrow, 9.sp, Color(0xFF849089), FontWeight.ExtraBold,
                        modifier = Modifier.offset(y = 3.dp), tracking = 1.44.sp)
                }
            }
            NativeText(title, titleSize, modifier = Modifier.padding(top = 3.dp),
                tracking = titleSize * -.045f, lineHeight = titleSize * 1.15f, maxLines = 2)
        }
        Spacer(Modifier.width(10.dp))
        Box(buttons) { action() }
    }
}

@Composable
private fun BackButton(back: () -> Unit) {
    Box(Modifier.size(32.dp).background(Clay.Cream.copy(alpha = .58f), CircleShape)
        .clickable(onClickLabel = "Go back", onClick = back), contentAlignment = Alignment.Center) {
        NativeIcon(R.drawable.ic_pingpaisa_back, 18.dp, Color(0xFF5F7F73))
    }
}

internal class PinnedHeaderState(val scrollOffset: () -> Int) {
    var menu by mutableStateOf<(() -> Unit)?>(null)
    var back by mutableStateOf<(() -> Unit)?>(null)
    var action by mutableStateOf<(@Composable () -> Unit)?>(null)
    // Header position in root coordinates at scroll 0, so it does not change while scrolling.
    var headerRoot by mutableStateOf(Offset.Zero)
    var headerSize by mutableStateOf(IntSize.Zero)
    var hostRoot by mutableStateOf(Offset.Zero)
}

internal val LocalPinnedHeader = staticCompositionLocalOf<PinnedHeaderState?> { null }

/** Keeps the menu, back and action buttons of any NativeHeader inside [content] fixed on screen. */
@Composable
internal fun PinnedHeaderHost(
    modifier: Modifier = Modifier, scrollOffset: () -> Int,
    content: @Composable BoxScope.() -> Unit
) {
    val pin = remember { PinnedHeaderState(scrollOffset) }
    Box(modifier.onGloballyPositioned { pin.hostRoot = it.positionInRoot() }) {
        CompositionLocalProvider(LocalPinnedHeader provides pin) { content() }
        val menu = pin.menu
        if (menu != null && pin.headerSize != IntSize.Zero) {
            val density = LocalDensity.current
            Row(Modifier.offset {
                IntOffset((pin.headerRoot.x - pin.hostRoot.x).roundToInt(), (pin.headerRoot.y - pin.hostRoot.y).roundToInt())
            }.then(with(density) { Modifier.size(pin.headerSize.width.toDp(), pin.headerSize.height.toDp()) }),
                verticalAlignment = Alignment.CenterVertically) {
                MenuButton(menu)
                pin.back?.let { Spacer(Modifier.width(9.dp)); BackButton(it) }
                Spacer(Modifier.weight(1f))
                pin.action?.invoke()
            }
        }
    }
}

@Composable
internal fun ScreenScroll(
    modifier: Modifier = Modifier, padded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val scroll = rememberScrollState()
    // The whole page, pinned buttons included, rises softly into place on entry.
    PinnedHeaderHost(modifier.fillMaxSize().riseIn(60), scrollOffset = { scroll.value }) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)
            .padding(start = if (padded) Clay.Gutter else 0.dp, end = if (padded) Clay.Gutter else 0.dp,
                top = if (padded) 10.dp else 0.dp, bottom = 74.dp), content = content)
    }
}

@Composable
internal fun SectionTitle(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 1.dp, end = 1.dp, top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        NativeText(title, 15.sp, tracking = (-.375).sp, modifier = Modifier.weight(1f))
        if (action != null) NativeText(action, 10.sp, Color(0xFF719487), FontWeight.ExtraBold,
            Modifier.clickable(onClick = onClick))
    }
}

@Composable
internal fun PrimaryAction(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, pressed: Boolean = false, onClick: () -> Unit) {
    Box(modifier.fillMaxWidth().heightIn(min = 49.dp).claySurface(
        CircleShape, Color(0xFFD9D5CF), if (!enabled) ClayShadow.None else if (pressed) ClayShadow.Pressed else ClayShadow.Primary,
        if (enabled) Color(0xFF91B6AA) to Color(0xFF6F998B) else null, inset = enabled
    ).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        NativeText(text, 12.sp, if (enabled) Color.White else Color(0xFFAAA6A0), FontWeight.ExtraBold,
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
    }
}

internal data class NativeCategory(val label: String, val icon: Int, val tone: Tone)
internal val nativeCategories = listOf(
    NativeCategory("Food", R.drawable.ic_pingpaisa_food, Tone.Peach),
    NativeCategory("Travel", R.drawable.ic_pingpaisa_car, Tone.Sage),
    NativeCategory("Shopping", R.drawable.ic_pingpaisa_bag, Tone.Pink),
    NativeCategory("Bills", R.drawable.ic_pingpaisa_bill, Tone.Lavender)
)

@Composable
internal fun NativeTransaction(
    name: String = "Third Wave Coffee", note: String = "Food • Today, 9:42 AM",
    amount: String = "−₹320", icon: Int = R.drawable.ic_pingpaisa_coffee,
    tone: Tone = Tone.Peach, onClick: () -> Unit = {}
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 61.dp)
        .claySurface(RoundedCornerShape(20.dp), Color(0xFFFBF9F6))
        .clickable(onClick = onClick).padding(9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ClayBadge(icon, tone)
        Column(Modifier.weight(1f)) {
            NativeText(name, 10.sp, weight = FontWeight.Bold, maxLines = 1)
            NativeText(note, 7.5.sp, Color(0xFF969996), modifier = Modifier.padding(top = 3.dp))
        }
        NativeText(amount, 10.sp, if (amount.startsWith("+")) Color(0xFF5C8B78) else Clay.Ink, FontWeight.Bold)
    }
}

@Composable
internal fun <T> NativeGrid(items: List<T>, columns: Int = 2, gap: Dp = 10.dp,
    content: @Composable RowScope.(T, Modifier) -> Unit) {
    val scale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val count = if (scale > 1.3f || maxWidth < 280.dp) 1 else columns
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            items.chunked(count).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { content(it, Modifier.weight(1f)) }
                    repeat(count - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
