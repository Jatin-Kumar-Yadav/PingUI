package com.pingpaisa.app

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Badge fills/tints are distinct from the six brand swatches in the Style Guide.
private enum class HomeTone(val fill: Color, val ink: Color) {
    Sage(Color(0xFFD8E5D4), Color(0xFF64806C)),
    Teal(Color(0xFFC4DBD3), Color(0xFF527B6D)),
    Peach(Color(0xFFF7DFC7), Color(0xFFA37452)),
    Pink(Color(0xFFF6D8D8), Color(0xFF9E6868)),
    Lavender(Color(0xFFDED8F1), Color(0xFF75699C)),
    Cream(Color(0xFFF4F0EA), Color(0xFF77817C))
}

private data class HomeShortcut(
    val title: String,
    val destination: Screen,
    val icon: Int,
    val tone: HomeTone
)

private val homeShortcuts = listOf(
    HomeShortcut("Budgets", Screen.Budgets, R.drawable.ic_pingpaisa_target, HomeTone.Sage),
    HomeShortcut("Cash flow", Screen.Flow, R.drawable.ic_pingpaisa_chart, HomeTone.Teal),
    HomeShortcut("Health score", Screen.Health, R.drawable.ic_pingpaisa_shield, HomeTone.Lavender),
    HomeShortcut("Loan planner", Screen.Loan, R.drawable.ic_pingpaisa_wallet, HomeTone.Peach),
    HomeShortcut("Money map", Screen.Heatmap, R.drawable.ic_pingpaisa_calendar, HomeTone.Pink),
    HomeShortcut("Recurring", Screen.Recurring, R.drawable.ic_pingpaisa_repeat, HomeTone.Sage),
    HomeShortcut("AI forecast", Screen.Forecast, R.drawable.ic_pingpaisa_sparkle, HomeTone.Lavender)
)

@Composable
internal fun HomeDashboard(navigate: (Screen) -> Unit, openDrawer: () -> Unit) {
    // The shell has already consumed device status-bar insets.
    val topInset = 0.dp
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    var heroPx by remember { mutableIntStateOf(0) }
    var cardPx by remember { mutableIntStateOf(0) }
    PinnedHeaderHost(Modifier.fillMaxSize(), scrollOffset = { 0 }) {
        val heroHeight = 154.dp
        // The hero stays fixed behind the page; its greeting fades as the card slides over it.
        HomeHero(heroHeight, topInset, openDrawer,
            modifier = Modifier.onSizeChanged { heroPx = it.height }.graphicsLayer {
                // Once the card's bottom edge passes the hero's bottom edge, the hero
                // moves up with the page and quickly fades out over a short distance.
                val start = cardPx - 18.dp.toPx()
                val past = (scroll.value - start).coerceAtLeast(0f)
                translationY = -past
                alpha = if (cardPx == 0) 1f else (1f - past / 56.dp.toPx()).coerceIn(0f, 1f)
            },
            textAlpha = { if (heroPx == 0) 1f else (1f - scroll.value / (heroPx * .6f)).coerceIn(0f, 1f) }
        ) { navigate(Screen.Settings) }
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom = 74.dp)
        ) {
            Spacer(Modifier.height((with(density) { heroPx.toDp() } - 18.dp).coerceAtLeast(0.dp)))
            MonthCard(Modifier.riseIn(80, 24f).padding(horizontal = Clay.Gutter).onSizeChanged { cardPx = it.height })
            Column(Modifier.fillMaxWidth().riseIn(200, 20f).padding(horizontal = Clay.Gutter)) {
                HomeSection("Categories", "See all") { navigate(Screen.Transactions) }
                HomeCategories { navigate(Screen.Transactions) }
                HomeSection("Money tools", "Explore", openDrawer)
                HomeTools(navigate)
                HomeSection("Recent activity", "View all") { navigate(Screen.Transactions) }
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    HomeTransaction(
                        "Third Wave Coffee", "Food • Today, 9:42 AM", "−₹320",
                        R.drawable.ic_pingpaisa_coffee, HomeTone.Peach
                    ) { navigate(Screen.Detail) }
                    HomeTransaction(
                        "Uber India", "Travel • Yesterday", "−₹486",
                        R.drawable.ic_pingpaisa_car, HomeTone.Sage
                    ) { navigate(Screen.Detail) }
                }
            }
        }
    }
}

@Composable
private fun HomeHero(
    height: Dp, topInset: Dp, openDrawer: () -> Unit, modifier: Modifier = Modifier,
    textAlpha: () -> Float = { 1f }, openProfile: () -> Unit
) {
    Column(
        modifier.fillMaxWidth().heightIn(min = height + topInset)
            .claySurface(RoundedCornerShape(bottomStart = 45.dp, bottomEnd = 45.dp),
                shadow = ClayShadow.None, gradient = Color(0xFFD8E5D4) to Color(0xFFA9C5A3), angle = 140f)
            .drawWithCache {
                val w = 100.dp.toPx()
                val h = 90.dp.toPx()
                val x = size.width - w + 24.dp.toPx()
                val y = -7.dp.toPx()
                val blob = Path().apply {
                    moveTo(x + w * .46f, y)
                    cubicTo(x + w * .76f, y, x + w, y + h * .16f, x + w, y + h * .54f)
                    cubicTo(x + w, y + h * .87f, x + w * .77f, y + h, x + w * .60f, y + h)
                    cubicTo(x + w * .20f, y + h, x, y + h * .78f, x, y + h * .46f)
                    cubicTo(x, y + h * .18f, x + w * .20f, y, x + w * .46f, y)
                    close()
                }
                onDrawBehind {
                    drawPath(blob, Clay.Cream.copy(alpha = .25f))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF4A6345).copy(alpha = .06f)),
                        startY = size.height - 28.dp.toPx(), endY = size.height))
                }
            }
            .padding(start = Clay.Gutter, end = Clay.Gutter, top = topInset + 10.dp, bottom = 15.dp)
    ) {
        NativeHeader("Namaste, Aanya", "GOOD MORNING", openDrawer, titleSize = 22.sp,
            modifier = Modifier.graphicsLayer { alpha = textAlpha() },
            action = {
                Box(Modifier.clickable(onClickLabel = "Your profile", onClick = openProfile)) {
                    ClayBadge(R.drawable.ic_pingpaisa_user, Tone.Cream)
                }
            })
        Spacer(Modifier.height(4.dp))
        HomeText("Your money is looking healthy today.", 10.sp, Color(0xFF61746C),
            modifier = Modifier.graphicsLayer { alpha = textAlpha() })
    }
}

@Composable
private fun MonthCard(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().heightIn(min = 203.dp)
            .homeSurface(RoundedCornerShape(27.dp), Clay.White, soft = false)
            .padding(17.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HomeText("This month", 11.sp, Color(0xFF727A76), FontWeight.Bold, Modifier.weight(1f))
            HomeText(
                "June⌄", 9.sp, weight = FontWeight.Bold,
                modifier = Modifier.background(Color(0xFFEEF1EB), CircleShape).height(26.dp)
                    .wrapContentHeight(Alignment.CenterVertically).padding(horizontal = 10.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        HomeText("₹42,680", 33.sp, tracking = (-1.98).sp)
        Row(Modifier.padding(top = 2.dp)) {
            HomeText("Total spent ", 9.sp, Color(0xFF949895))
            HomeText("8% less", 9.sp, Color(0xFF668B7D), FontWeight.Bold)
            HomeText(" than May", 9.sp, Color(0xFF949895))
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            HomeStat("Income", "₹85K", Color(0xFFEDF2E9), Modifier.weight(1f))
            HomeStat("Savings", "₹32K", Color(0xFFEEEAF6), Modifier.weight(1f))
            HomeStat("Budget left", "₹18K", Color(0xFFF7E8D9), Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeStat(label: String, value: String, fill: Color, modifier: Modifier) {
    Column(modifier.background(fill, RoundedCornerShape(15.dp)).padding(horizontal = 9.dp, vertical = 10.dp)) {
        HomeText(label, 7.sp, Color(0xFF7E8581))
        Spacer(Modifier.height(3.dp))
        HomeText(value, 11.sp, weight = FontWeight.Bold)
    }
}

@Composable
private fun HomeSection(title: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 1.dp, end = 1.dp, top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HomeText(title, 15.sp, modifier = Modifier.weight(1f), tracking = (-.375).sp)
        HomeText(
            action, 10.sp, Color(0xFF719487), FontWeight.ExtraBold,
            Modifier.clip(CircleShape).clickable(onClick = onClick).padding(vertical = 3.dp)
        )
    }
}

@Composable
private fun HomeCategories(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 2.dp, top = 2.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        listOf(
            Triple("Food", R.drawable.ic_pingpaisa_food, HomeTone.Peach),
            Triple("Travel", R.drawable.ic_pingpaisa_car, HomeTone.Sage),
            Triple("Shopping", R.drawable.ic_pingpaisa_bag, HomeTone.Pink),
            Triple("Bills", R.drawable.ic_pingpaisa_bill, HomeTone.Lavender)
        ).forEach { (label, icon, tone) ->
            Row(
                Modifier.homeSurface(CircleShape, Color(0xFFF8F5F0)).clickable(onClick = onClick)
                    .padding(start = 7.dp, end = 13.dp, top = 7.dp, bottom = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeBadge(icon, tone)
                HomeText(label, 9.sp, weight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HomeTools(navigate: (Screen) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 280.dp || fontScale > 1.3f) 1 else 2
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            homeShortcuts.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    row.forEach { shortcut ->
                        Row(
                            Modifier.weight(1f).heightIn(min = 49.dp)
                                .homeSurface(RoundedCornerShape(17.dp), Color(0xFFFAF8F4))
                                .clickable { navigate(shortcut.destination) }
                                .padding(start = 7.dp, end = 9.dp, top = 7.dp, bottom = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            // The reference uses a wide pastel badge, including on the full-width forecast tile.
                            HomeBadge(shortcut.icon, shortcut.tone, modifier = Modifier.weight(1f))
                            HomeText(shortcut.title, 8.sp, weight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                            HomeIcon(R.drawable.ic_pingpaisa_chevron, 12.dp, Clay.Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTransaction(
    name: String, note: String, amount: String, icon: Int, tone: HomeTone, onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 61.dp)
            .homeSurface(RoundedCornerShape(20.dp), Color(0xFFFBF9F6))
            .clickable(onClick = onClick).padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HomeBadge(icon, tone, small = false)
        Column(Modifier.weight(1f)) {
            HomeText(name, 10.sp, weight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            HomeText(note, 7.5.sp, Color(0xFF969996))
        }
        HomeText(amount, 10.sp, weight = FontWeight.Bold)
    }
}

@Composable
private fun HomeBadge(
    icon: Int, tone: HomeTone, small: Boolean = true, modifier: Modifier = Modifier, description: String? = null
) {
    ClayBadge(icon, Tone.valueOf(tone.name), small, modifier)
}

@Composable
private fun HomeIcon(icon: Int, size: Dp, tint: Color, description: String? = null) {
    Icon(painterResource(icon), description, Modifier.size(size), tint = tint)
}

@Composable
private fun HomeText(
    text: String,
    size: TextUnit,
    color: Color = Clay.Ink,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    tracking: TextUnit = 0.sp,
    maxLines: Int = Int.MAX_VALUE
) {
    NativeText(text, size, color, weight, modifier, tracking, maxLines = maxLines)
}

private fun Modifier.homeSurface(
    shape: androidx.compose.ui.graphics.Shape,
    fill: Color,
    soft: Boolean = true
): Modifier = claySurface(shape, fill, if (soft) ClayShadow.Soft else ClayShadow.Raised)

@Preview(name = "Home · compact", widthDp = 320, heightDp = 568, showBackground = true)
@Preview(name = "Home · reference", widthDp = 390, heightDp = 764, showBackground = true)
@Preview(name = "Home · tall", widthDp = 430, heightDp = 852, showBackground = true)
@Preview(name = "Home · landscape", widthDp = 844, heightDp = 310, showBackground = true)
@Preview(name = "Home · large text", widthDp = 360, heightDp = 720, fontScale = 1.5f, showBackground = true)
@Composable
private fun HomeDashboardPreview() {
    androidx.compose.material3.MaterialTheme(typography = PingPaisaTypography) {
        Box(Modifier.fillMaxSize().background(Clay.Cream)) { HomeDashboard({}, {}) }
    }
}
