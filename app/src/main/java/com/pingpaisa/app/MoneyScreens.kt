package com.pingpaisa.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import com.pingpaisa.app.healthscore.Money
import kotlin.math.roundToInt

@Composable
internal fun TransactionsScreen(menu: () -> Unit, navigate: (Screen) -> Unit) {
    ScreenScroll {
        NativeHeader("Transactions", "JUNE 2025", menu, action = {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                RoundAction(R.drawable.ic_pingpaisa_plus, "Add an expense", 36.dp) { navigate(Screen.Entry) }
                RoundAction(R.drawable.ic_pingpaisa_calendar, "Money map", 36.dp) { navigate(Screen.Heatmap) }
            }
        })
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().claySurface(RoundedCornerShape(26.dp), shadow = ClayShadow.Raised).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(17.dp)) {
            Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
                val reveal = rememberEntrance(150, 1100)
                Canvas(Modifier.fillMaxSize()) {
                    // Segments sweep in clockwise as the screen settles.
                    var start = -90f
                    var budget = 360f * reveal.value
                    listOf(Clay.Sage to 104.4f, Clay.Lavender to 90f, Clay.Peach to 75.6f, Clay.Pink to 90f).forEach { (color, sweep) ->
                        val visible = sweep.coerceAtMost(budget)
                        if (visible > 0f) drawArc(color, start, visible, true)
                        budget -= visible
                        start += sweep
                    }
                    drawCircle(Color(0xFFFAF8F4), radius = 41.dp.toPx())
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NativeText("Spent", 8.sp, Color(0xFF979B98))
                    NativeText("₹42.6K", 16.sp, weight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Triple("Food", "₹12.4K", Tone.Peach), Triple("Bills", "₹10.8K", Tone.Lavender),
                    Triple("Travel", "₹8.6K", Tone.Sage), Triple("Other", "₹10.8K", Tone.Pink)
                ).forEach { (label, value, tone) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(tone.fill, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        NativeText(label, 8.sp, Color(0xFF858B87), modifier = Modifier.weight(1f))
                        NativeText(value, 9.sp, weight = FontWeight.Bold)
                    }
                }
            }
        }
        DateDivider("TODAY", "−₹806")
        NativeTransaction(onClick = { navigate(Screen.Detail) })
        Spacer(Modifier.height(9.dp))
        NativeTransaction("Uber India", "Travel • Yesterday", "−₹486", R.drawable.ic_pingpaisa_car, Tone.Sage) { navigate(Screen.Detail) }
        DateDivider("YESTERDAY", "+₹84,514")
        NativeTransaction("Salary credit", "Income • 01 Jun", "+₹85,000", R.drawable.ic_pingpaisa_wallet, Tone.Teal) { navigate(Screen.Detail) }
    }
}

@Composable
private fun DateDivider(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(start = 3.dp, end = 3.dp, top = 16.dp, bottom = 8.dp)) {
        NativeText(label, 8.sp, Color(0xFF919693), modifier = Modifier.weight(1f), tracking = .8.sp)
        NativeText(value, 8.sp, Color(0xFF6E7772), FontWeight.Bold)
    }
}

@Composable
internal fun DetailScreen(menu: () -> Unit, back: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf("Food") }
    var note by rememberSaveable { mutableStateOf("Coffee with Nisha") }
    ScreenScroll {
        NativeHeader("Transaction detail", menu = menu, back = back,
            action = { RoundAction(R.drawable.ic_pingpaisa_edit, "Edit note") {} })
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().claySurface(RoundedCornerShape(28.dp), shadow = ClayShadow.Raised)) {
            NativeText("Successful", 7.sp, Color(0xFF5D806C), FontWeight.ExtraBold,
                Modifier.align(Alignment.TopEnd).padding(14.dp).background(Color(0xFFE3EDE0), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 5.dp))
            Column(Modifier.fillMaxWidth().padding(23.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ClayBadge(R.drawable.ic_pingpaisa_coffee, Tone.Peach, size = 58.dp)
                NativeText("−₹320", 30.sp, modifier = Modifier.padding(top = 13.dp, bottom = 3.dp), tracking = (-1.5).sp)
                Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                    NativeText("Third Wave Coffee", 12.sp, weight = FontWeight.Bold)
                }
                NativeText("Today at 9:42 AM • UPI", 8.sp, Color(0xFF979996), modifier = Modifier.padding(top = 4.dp, bottom = 15.dp))
                Canvas(Modifier.fillMaxWidth().height(1.dp)) {
                    drawLine(Color(0xFFDED9D2), Offset.Zero, Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    NativeText("UPI reference", 8.sp, Color(0xFF959894), modifier = Modifier.weight(1f))
                    NativeText("531641982214", 8.sp)
                }
            }
        }
        SectionTitle("Choose category", "Manage") {}
        NativeGrid(nativeCategories + NativeCategory("Other", R.drawable.ic_pingpaisa_plus, Tone.Cream), columns = 3) { category, cell ->
            val active = selected == category.label
            Box(cell.offset(y = if (active) (-2).dp else 0.dp).heightIn(min = 91.dp).claySurface(RoundedCornerShape(21.dp),
                if (active) Color(0xFFF1F6EE) else Color(0xFFF8F5F0))
                .then(if (active) Modifier.border(2.dp, Color(0xFF8EAE91), RoundedCornerShape(21.dp)) else Modifier)
                .clickable { selected = category.label }) {
                Column(Modifier.align(Alignment.Center).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ClayBadge(category.icon, category.tone)
                    NativeText(category.label, 8.sp, weight = FontWeight.Bold)
                }
                if (active) Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(18.dp)
                    .background(Color(0xFF789F8E), CircleShape), contentAlignment = Alignment.Center) {
                    NativeIcon(R.drawable.ic_pingpaisa_check, 12.dp, Color.White)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth().claySurface(RoundedCornerShape(17.dp), Color(0xFFF7F4EF), ClayShadow.None, inset = true)
            .padding(horizontal = 13.dp, vertical = 9.dp)) {
            NativeText("Note", 7.sp, Color(0xFF999999))
            BasicTextField(note, { note = it }, Modifier.fillMaxWidth().padding(top = 3.dp),
                textStyle = TextStyle(fontFamily = PingPaisaFont, fontSize = 10.sp, color = Color(0xFF41534C), lineHeight = 24.sp))
        }
        Spacer(Modifier.height(14.dp))
        PrimaryAction("Save changes", onClick = back)
    }
}

@Composable
internal fun EntryScreen(menu: () -> Unit, back: () -> Unit) {
    var amount by rememberSaveable { mutableStateOf("1,250") }
    var type by rememberSaveable { mutableStateOf("Expense") }
    var category by rememberSaveable { mutableStateOf("Food") }
    ScreenScroll {
        NativeHeader("Add an expense", "NEW TRANSACTION", menu, back,
            action = { RoundAction(R.drawable.ic_pingpaisa_close, "Close", onClick = back) })
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().heightIn(min = 112.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            NativeText("₹", 22.sp, Color(0xFF859089), modifier = Modifier.padding(end = 7.dp))
            NativeText(amount.ifEmpty { "0" }, 46.sp, weight = FontWeight.Bold, tracking = (-2.76).sp)
            Box(Modifier.padding(start = 4.dp).width(2.dp).height(40.dp).background(Color(0xFF769A8E)))
        }
        Row(Modifier.align(Alignment.CenterHorizontally).widthIn(max = 210.dp).fillMaxWidth()
            .background(Color(0xFFE9E8E2), CircleShape).padding(4.dp)) {
            listOf("Expense", "Income").forEach { label ->
                Box(Modifier.weight(1f).claySurface(CircleShape,
                    if (type == label) Color(0xFFF9F7F3) else Color.Transparent,
                    if (type == label) ClayShadow.Soft else ClayShadow.None)
                    .clickable { type = label }.padding(8.dp), contentAlignment = Alignment.Center) {
                    NativeText(label, 9.sp, weight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            nativeCategories.forEach { item ->
                Column(Modifier.clickable { category = item.label }, horizontalAlignment = Alignment.CenterHorizontally) {
                    ClayBadge(item.icon, item.tone, small = true)
                    NativeText(item.label, 7.sp, weight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
        Spacer(Modifier.height(17.dp))
        Column(Modifier.padding(horizontal = 7.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            listOf("1","2","3","4","5","6","7","8","9",".","0","⌫").chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    row.forEach { key ->
                        Box(Modifier.weight(1f).heightIn(min = 55.dp)
                            .claySurface(RoundedCornerShape(19.dp), Color(0xFFF7F3EE))
                            .clickable {
                                amount = when {
                                    key == "⌫" -> amount.dropLast(1)
                                    key == "." && amount.contains('.') -> amount
                                    else -> if (amount == "0") key else amount + key
                                }
                            }, contentAlignment = Alignment.Center) {
                            NativeText(key, 17.sp, weight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(17.dp))
        PrimaryAction("Add ${type.lowercase()}", onClick = back)
    }
}

@Composable
private fun ProgressRing(value: Float, color: Color, size: androidx.compose.ui.unit.Dp = 78.dp, content: @Composable () -> Unit) {
    val reveal = rememberEntrance(200, 1100)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xFFEBE7E1))
            drawArc(color, -90f, 360 * value * reveal.value, true)
            drawCircle(Color(0xFFFAF8F4), radius = this.size.minDimension / 2 - (if (size == 78.dp) 9.dp else 5.dp).toPx())
        }
        content()
    }
}

@Composable
internal fun BudgetsScreen(menu: () -> Unit, back: () -> Unit) {
    ScreenScroll {
        NativeHeader("Budgets & goals", "JUNE 2025", menu, back,
            action = { RoundAction(R.drawable.ic_pingpaisa_plus, "Add budget") {} })
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).claySurface(RoundedCornerShape(22.dp), shadow = ClayShadow.Raised,
            gradient = Color(0xFFBDD1B8) to Color(0xFF9ABB94)).padding(17.dp)) {
            Column(Modifier.weight(1f)) {
                NativeText("Monthly budget", 8.sp, Color(0xFF395148).copy(alpha = .7f))
                NativeText("₹60,000", 16.sp, Color(0xFF395148), FontWeight.Bold, Modifier.padding(top = 4.dp))
            }
            // CSS: span+span { border-left: 1px solid rgba(65,82,73,.15); padding-left: 17px }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0xFF415249).copy(alpha = .15f)))
            Column(Modifier.weight(1f).padding(start = 17.dp)) {
                NativeText("Remaining", 8.sp, Color(0xFF395148).copy(alpha = .7f))
                NativeText("₹17,320", 16.sp, Color(0xFF395148), FontWeight.Bold, Modifier.padding(top = 4.dp))
            }
        }
        Spacer(Modifier.height(15.dp))
        val percentages = listOf(62, 43, 86, 72)
        val ringColors = listOf(Color(0xFFE5AF81), Color(0xFF8BAE86), Color(0xFFE99A98), Color(0xFF9E92CA))
        NativeGrid(nativeCategories.withIndex().toList()) { (i, item), cell ->
            Column(cell.heightIn(min = 156.dp).claySurface(RoundedCornerShape(24.dp)).padding(13.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                ProgressRing(percentages[i] / 100f, ringColors[i]) {
                    NativeText("${percentages[i]}%", 11.sp, ringColors[i], FontWeight.Bold)
                }
                NativeText(item.label, 10.sp, weight = FontWeight.Bold, modifier = Modifier.padding(top = 7.dp))
                NativeText(listOf("₹12.4K / ₹20K","₹8.6K / ₹20K","₹8.6K / ₹10K","₹10.8K / ₹15K")[i],
                    7.sp, Color(0xFF999999), modifier = Modifier.padding(top = 3.dp))
            }
        }
        Spacer(Modifier.height(13.dp))
        Column(Modifier.fillMaxWidth().claySurface(RoundedCornerShape(25.dp),
            gradient = Color(0xFFEEEAF8) to Color(0xFFDDD6F1)).padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(43.dp).background(Color(0xFFF8F5FC), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center) { NativeIcon(R.drawable.ic_pingpaisa_target, 26.dp, Color(0xFF776BA0)) }
                Column(Modifier.weight(1f)) {
                    NativeText("SAVINGS GOAL", 9.sp, Color(0xFF849089), FontWeight.ExtraBold, tracking = 1.44.sp)
                    NativeText("Dream trip to Japan", 10.sp, modifier = Modifier.padding(vertical = 3.dp))
                }
                NativeText("68%", 12.sp, Color(0xFF6F6495), FontWeight.Bold)
            }
            MiniProgress(.68f, Color(0xFF9C90C8), Modifier.padding(top = 11.dp, bottom = 5.dp), 10.dp)
            Row {
                NativeText("₹1,36,000 saved of ₹2,00,000", 7.sp, Color(0xFF85808E), modifier = Modifier.weight(1f))
                NativeText("₹64,000 to go", 7.sp, weight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun MiniProgress(value: Float, color: Color, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 8.dp) {
    Box(modifier.fillMaxWidth().height(height).background(Color(0xFFEDEAE5), CircleShape)) {
        val reveal = rememberEntrance(300, 1000)
        Box(Modifier.fillMaxWidth((value * reveal.value).coerceIn(0f, 1f)).fillMaxHeight().background(color, CircleShape))
    }
}

@Composable
internal fun FlowScreen(menu: () -> Unit, back: () -> Unit) {
    ScreenScroll {
        NativeHeader("Cash flow", "LAST 6 MONTHS", menu, back)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().claySurface(RoundedCornerShape(25.dp), shadow = ClayShadow.Raised,
            gradient = Color(0xFFDCE9D8) to Color(0xFFC2D7BD)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            ClayBadge(R.drawable.ic_pingpaisa_chart)
            Column(Modifier.weight(1f)) {
                NativeText("Net savings this month", 7.sp)
                NativeText("+₹32,320", 20.sp, tracking = (-.8).sp, modifier = Modifier.padding(vertical = 2.dp))
                NativeText("Best month this quarter", 7.sp, Color(0xFF748078))
            }
            NativeText("↑ 18%", 8.sp, Color(0xFF58806F), FontWeight.ExtraBold,
                Modifier.background(Color.White.copy(alpha = .55f), CircleShape).padding(horizontal = 8.dp, vertical = 6.dp))
        }
        Spacer(Modifier.height(17.dp))
        Column(Modifier.fillMaxWidth().claySurface(RoundedCornerShape(26.dp), shadow = ClayShadow.Raised).padding(15.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                listOf("Income" to Tone.Teal.fill, "Expenses" to Tone.Peach.fill).forEach { (label, color) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(7.dp).background(color, CircleShape)); NativeText(label, 8.sp, Color(0xFF818783))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(232.dp).padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
                val grow = rememberEntrance(200, 900)
                val fromBottom = Modifier.graphicsLayer { scaleY = grow.value; transformOrigin = TransformOrigin(.5f, 1f) }
                listOf(72 to 48,90 to 55,64 to 68,84 to 44,76 to 60,94 to 51).forEachIndexed { i, bars ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.height(180.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(fromBottom.width(11.dp).fillMaxHeight(bars.first / 100f).background(Color(0xFF91B4A8), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)))
                            Box(fromBottom.width(11.dp).fillMaxHeight(bars.second / 100f).background(Color(0xFFEFC8A6), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)))
                        }
                        NativeText(listOf("Jan","Feb","Mar","Apr","May","Jun")[i], 7.sp, Color(0xFF969895), modifier = Modifier.padding(vertical = 7.dp))
                    }
                }
            }
        }
        NativeText("Month summary", 12.sp, modifier = Modifier.padding(top = 18.dp, bottom = 12.dp))
        listOf(Triple("Money in","₹85,000","+6.2%"),Triple("Money out","₹42,680","−8.0%")).forEachIndexed { i, item ->
            Row(Modifier.padding(bottom = 9.dp).fillMaxWidth().claySurface(RoundedCornerShape(20.dp), Color(0xFFFAF8F5)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ClayBadge(if (i == 0) R.drawable.ic_pingpaisa_wallet else R.drawable.ic_pingpaisa_bag, if (i == 0) Tone.Sage else Tone.Peach)
                Column(Modifier.weight(1f)) { NativeText(item.first, 7.sp, Color(0xFF999999)); NativeText(item.second, 10.sp, weight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp)) }
                NativeText(item.third, 8.sp, if (i == 0) Color(0xFF5E8A77) else Color(0xFFA56D58),
                    modifier = Modifier.background(if (i == 0) Color(0xFFE3EDE0) else Color(0xFFF7E0D6), CircleShape).padding(horizontal = 7.dp, vertical = 5.dp))
            }
        }
    }
}

@Composable
internal fun RecurringScreen(menu: () -> Unit, back: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf("All") }
    ScreenScroll {
        NativeHeader("Recurring payments", "UPCOMING", menu, back)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().height(104.dp).claySurface(RoundedCornerShape(26.dp), shadow = ClayShadow.Raised,
            gradient = Color(0xFFF5DDC5) to Color(0xFFEDC9A7)).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { NativeText("Due this month", 8.sp, Color(0xFF8B796B)); NativeText("₹35,368", 25.sp, modifier = Modifier.padding(vertical = 4.dp)) }
            Column(Modifier.width(56.dp).height(62.dp).claySurface(RoundedCornerShape(15.dp), Color(0xFFFAF5EF)), horizontalAlignment = Alignment.CenterHorizontally) {
                NativeText("JUN", 7.sp, Color.White, FontWeight.ExtraBold, Modifier.fillMaxWidth().background(Color(0xFFCD8B7F)).padding(5.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
                NativeText("30", 20.sp, weight = FontWeight.Bold, modifier = Modifier.padding(7.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        SegmentedTabs(listOf("All","EMI","SIP","Other"), filter, { filter = it })
        Spacer(Modifier.height(17.dp))
        data class RecurringItem(val name: String, val amount: String, val due: String, val progress: Float, val icon: Int, val tone: Tone, val type: String)
        listOf(
            RecurringItem("Home loan EMI","₹24,500","3 days",.72f,R.drawable.ic_pingpaisa_bill,Tone.Peach,"EMI"),
            RecurringItem("Index fund SIP","₹10,000","8 days",.48f,R.drawable.ic_pingpaisa_chart,Tone.Sage,"SIP"),
            RecurringItem("Netflix","₹649","Tomorrow",.9f,R.drawable.ic_pingpaisa_repeat,Tone.Pink,"Other"),
            RecurringItem("iCloud+","₹219","12 days",.64f,R.drawable.ic_pingpaisa_repeat,Tone.Lavender,"Other")
        ).filter { filter == "All" || it.type == filter }.forEach { item ->
            Row(Modifier.padding(bottom = 10.dp).fillMaxWidth().claySurface(RoundedCornerShape(22.dp), Color(0xFFFAF8F5)).padding(11.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProgressRing(item.progress, item.tone.ink, 45.dp) { NativeIcon(item.icon, 18.dp, item.tone.ink) }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NativeText(item.name, 9.sp, weight = FontWeight.Bold)
                        if (item.type == "Other") NativeText("AUTO-DETECTED", 5.sp, Tone.Lavender.ink,
                            modifier = Modifier.padding(start = 5.dp).background(Color(0xFFE3DEF2), CircleShape).padding(horizontal = 4.dp, vertical = 3.dp))
                    }
                    NativeText("Due in ${item.due}", 7.sp, Color(0xFF999999))
                }
                NativeText(item.amount, 10.sp, weight = FontWeight.Bold)
            }
        }
        Row(Modifier.padding(top = 5.dp).fillMaxWidth().background(Color(0xFFE7E2F2), RoundedCornerShape(20.dp)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            NativeIcon(R.drawable.ic_pingpaisa_brain, tint = Color(0xFF71688A))
            Column { NativeText("Smart detection is on", 9.sp, Color(0xFF71688A), FontWeight.Bold)
                NativeText("We scan patterns, not messages.", 7.sp, Color(0xFF71688A), modifier = Modifier.padding(top = 2.dp)) }
        }
    }
}

@Composable
internal fun SegmentedTabs(labels: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFFECE9E4), CircleShape).padding(4.dp)) {
        labels.forEach { label ->
            Box(Modifier.weight(1f).claySurface(CircleShape,
                if (selected == label) Color(0xFFFAF8F4) else Color.Transparent,
                if (selected == label) ClayShadow.Soft else ClayShadow.None)
                .clickable { onSelect(label) }.padding(8.dp), contentAlignment = Alignment.Center) {
                NativeText(label, 8.sp, if (selected == label) Color(0xFF53665E) else Color(0xFF969895), FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun LoanScreen(menu: () -> Unit, back: () -> Unit) {
    var loan by rememberSaveable { mutableFloatStateOf(600000f) }
    var years by rememberSaveable { mutableIntStateOf(3) }
    var showPlan by rememberSaveable { mutableStateOf(false) }
    val emi = (loan / (years * 12) * 1.12f).roundToInt()
    val score = (820 - (loan / 18000).roundToInt()).coerceAtLeast(580)
    ScreenScroll {
        NativeHeader("Loan simulator", "PLAN SAFELY", menu, back, action = { ClayBadge(R.drawable.ic_pingpaisa_sparkle, Tone.Lavender) })
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth().claySurface(RoundedCornerShape(27.dp), shadow = ClayShadow.Raised).padding(18.dp)) {
            NativeText("LOAN AMOUNT", 7.sp, Color(0xFF999999), modifier = Modifier.align(Alignment.CenterHorizontally), tracking = .91.sp)
            NativeText(Money.inr(loan.toInt()), 29.sp, tracking = (-1.45).sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 7.dp, bottom = 14.dp))
            LoanAmountSlider(loan) { loan = it }
            Row { NativeText("₹1L", 6.sp, Color(0xFFAAAAAA), modifier = Modifier.weight(1f)); NativeText("₹20L", 6.sp, Color(0xFFAAAAAA)) }
            Row(Modifier.fillMaxWidth().padding(top = 17.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NativeText("Tenure", 8.sp, modifier = Modifier.weight(1f))
                listOf(1,3,5).forEach { year ->
                    NativeText(if (year == 1) "1 yr" else "$year yrs", 7.sp,
                        modifier = Modifier.background(if (years == year) Color(0xFFC8D9C3) else Color(0xFFEFEDE8), CircleShape)
                            .clickable { years = year }.padding(horizontal = 10.dp, vertical = 7.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().claySurface(RoundedCornerShape(23.dp),
            gradient = Color(0xFFD9E7D5) to Color(0xFFBDD3B8)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                NativeText("ESTIMATED EMI", 6.sp, tracking = .6.sp)
                Row(verticalAlignment = Alignment.CenterVertically) { NativeText(Money.inr(emi), 20.sp, modifier = Modifier.padding(vertical = 4.dp)); NativeText("/mo", 8.sp, weight = FontWeight.Medium) }
            }
            Column(horizontalAlignment = Alignment.End) { NativeText("INTEREST", 6.sp, tracking = .6.sp); NativeText("10.4%", 12.sp, weight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(13.dp))
        Column(Modifier.fillMaxWidth().claySurface(RoundedCornerShape(25.dp)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { NativeText("HEALTH SCORE IMPACT", 6.sp, tracking = .6.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) { NativeText("$score", 22.sp, modifier = Modifier.padding(vertical = 3.dp)); NativeText(" from 782", 7.sp, Color(0xFF999999)) } }
                NativeText(if (score > 700) "Low risk" else "Medium risk", 7.sp,
                    modifier = Modifier.background(Color(0xFFDCE9D8), CircleShape).padding(horizontal = 9.dp, vertical = 6.dp), weight = FontWeight.Bold)
            }
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp).height(18.dp), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth().height(10.dp).background(Brush.horizontalGradient(listOf(Color(0xFFE5A7A4),Color(0xFFEFD39F),Color(0xFF95B691))), CircleShape))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    Box(Modifier.offset(x = (maxWidth - 18.dp) * ((score - 500) / 350f)).size(18.dp)
                        .background(Color.White, CircleShape).border(4.dp, Color(0xFF5C7A6D), CircleShape))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf("Risky","Balanced","Healthy").forEach { NativeText(it, 6.sp, Color(0xFFAAAAAA)) } }
            Row(Modifier.padding(top = 12.dp).fillMaxWidth().background(Color(0xFFEEE9F5), RoundedCornerShape(13.dp)).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                NativeIcon(R.drawable.ic_pingpaisa_sparkle, 16.dp, Color(0xFF716A7D))
                NativeText("EMI would use ${(emi / 850f).roundToInt()}% of your monthly income.", 7.sp, Color(0xFF716A7D))
            }
        }
        Spacer(Modifier.height(15.dp))
        PrimaryAction(if (showPlan) "Hide repayment plan" else "View repayment plan") { showPlan = !showPlan }
        if (showPlan) NativeText("${years * 12} payments of ${Money.inr(emi)} · illustrative demo", 10.sp, modifier = Modifier.padding(top = 15.dp))
    }
}

@Composable
private fun LoanAmountSlider(value: Float, onChange: (Float) -> Unit) {
    val change by rememberUpdatedState(onChange)
    fun stepped(fraction: Float) = (100000f + (fraction.coerceIn(0f, 1f) * 1900000f / 50000f).roundToInt() * 50000f)
    Canvas(Modifier.fillMaxWidth().height(24.dp)
        .semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(value, 100000f..2000000f, 37)
            setProgress { change(stepped((it - 100000f) / 1900000f)); true }
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                change(stepped(down.position.x / size.width))
                down.consume()
                drag(down.id) { pointer ->
                    change(stepped(pointer.position.x / size.width))
                    pointer.consume()
                }
            }
        }) {
        val x = 8.dp.toPx() + ((value - 100000f) / 1900000f) * (size.width - 16.dp.toPx())
        drawLine(Color(0xFFDDDDDD), Offset(8.dp.toPx(), center.y), Offset(size.width - 8.dp.toPx(), center.y), 5.dp.toPx(), StrokeCap.Round)
        drawLine(Clay.Teal, Offset(8.dp.toPx(), center.y), Offset(x, center.y), 5.dp.toPx(), StrokeCap.Round)
        drawCircle(Clay.Teal, 8.dp.toPx(), Offset(x, center.y))
    }
}
