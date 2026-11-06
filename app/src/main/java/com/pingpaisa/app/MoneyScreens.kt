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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.pingpaisa.app.healthscore.Money
import java.time.LocalDate
import kotlin.math.roundToInt

/** Donut segment and legend colours, in order of size (biggest category first). */
private val donutColors = listOf(Clay.Sage, Clay.Lavender, Clay.Peach, Clay.Pink)

/**
 * [ui] is the real month (see TransactionsUi.from); the default is the design's sample content,
 * used by previews and the screenshot tests. [onMonth] is called with -1 (earlier) or +1 (later).
 */
@Composable
internal fun TransactionsScreen(
    menu: () -> Unit, navigate: (Screen) -> Unit,
    ui: TransactionsUi = TransactionsUi.Sample,
    onMonth: (Int) -> Unit = {},
    openTransaction: (Int?) -> Unit = { navigate(Screen.Detail) }
) {
    ScreenScroll {
        NativeHeader("Transactions", ui.eyebrow, menu, action = {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                RoundAction(R.drawable.ic_pingpaisa_plus, "Add an expense", 36.dp) { navigate(Screen.Entry) }
                RoundAction(R.drawable.ic_pingpaisa_calendar, "Money map", 36.dp) { navigate(Screen.Heatmap) }
            }
        })
        if (ui.showMonthSwitcher) MonthSwitcher(ui.canGoBack, ui.canGoForward, onMonth)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().claySurface(RoundedCornerShape(26.dp), shadow = ClayShadow.Raised).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(17.dp)) {
            Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
                val reveal = rememberEntrance(150, 1100)
                Canvas(Modifier.fillMaxSize()) {
                    if (ui.slices.isEmpty()) drawCircle(Color(0xFFEBE7E1))   // nothing spent: a plain ring
                    else {
                        // Segments sweep in clockwise as the screen settles.
                        var start = -90f
                        var budget = 360f * reveal.value
                        ui.slices.forEachIndexed { i, slice ->
                            val sweep = 360f * slice.fraction
                            val visible = sweep.coerceAtMost(budget)
                            if (visible > 0f) drawArc(donutColors[i % donutColors.size], start, visible, true)
                            budget -= visible
                            start += sweep
                        }
                    }
                    drawCircle(Color(0xFFFAF8F4), radius = 41.dp.toPx())
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NativeText("Spent", 8.sp, Color(0xFF979B98))
                    NativeText(ui.spent, 16.sp, weight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp), maxLines = 1)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (ui.slices.isEmpty()) NativeText("No spending in ${ui.monthName} yet", 8.sp, Color(0xFF858B87))
                ui.slices.forEachIndexed { i, slice ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(donutColors[i % donutColors.size], CircleShape))
                        Spacer(Modifier.width(6.dp))
                        NativeText(slice.label, 8.sp, Color(0xFF858B87), modifier = Modifier.weight(1f), maxLines = 1)
                        NativeText(slice.amount, 9.sp, weight = FontWeight.Bold)
                    }
                }
            }
        }
        if (ui.isEmpty) EmptyMonthCard(ui.monthName) { navigate(Screen.Entry) }
        else ui.days.forEach { day ->
            DateDivider(day.label, day.net)
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                day.rows.forEach { row ->
                    NativeTransaction(row.name, row.note, row.amount, row.icon, row.tone) { openTransaction(row.id) }
                }
            }
        }
    }
}

/** "‹ Earlier" and "Later ›": browse months. A pill is dimmed when there is nowhere to go. */
@Composable
private fun MonthSwitcher(canGoBack: Boolean, canGoForward: Boolean, onMonth: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        SwitchPill("Earlier", R.drawable.ic_pingpaisa_back, iconFirst = true, enabled = canGoBack) { onMonth(-1) }
        SwitchPill("Later", R.drawable.ic_pingpaisa_chevron, iconFirst = false, enabled = canGoForward) { onMonth(1) }
    }
}

@Composable
private fun SwitchPill(label: String, icon: Int, iconFirst: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val ink = if (enabled) Color(0xFF719487) else Color(0xFFC3C6C3)
    Row(
        Modifier.claySurface(CircleShape, Color(0xFFF5F1EC), if (enabled) ClayShadow.Soft else ClayShadow.None)
            .clickable(enabled = enabled, onClickLabel = "$label month", onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (iconFirst) NativeIcon(icon, 14.dp, ink)
        NativeText(label, 9.sp, ink, FontWeight.ExtraBold)
        if (!iconFirst) NativeIcon(icon, 14.dp, ink)
    }
}

/** Shown instead of the list when the chosen month has no transactions. */
@Composable
private fun EmptyMonthCard(monthName: String, onAdd: () -> Unit) {
    Column(
        Modifier.padding(top = 16.dp).fillMaxWidth().claySurface(RoundedCornerShape(20.dp), Color(0xFFFBF9F6)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ClayBadge(R.drawable.ic_pingpaisa_wallet, Tone.Sage)
        NativeText("No transactions in $monthName", 11.sp, weight = FontWeight.Bold)
        NativeText("Payments are added automatically once notification access is on, or you can add one yourself.",
            8.sp, Color(0xFF8C918F), align = androidx.compose.ui.text.style.TextAlign.Center)
        NativeText("Add an expense", 9.sp, Color(0xFF719487), FontWeight.ExtraBold,
            Modifier.clip(CircleShape).clickable(onClick = onAdd).padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
private fun DateDivider(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(start = 3.dp, end = 3.dp, top = 16.dp, bottom = 8.dp)) {
        NativeText(label, 8.sp, Color(0xFF919693), modifier = Modifier.weight(1f), tracking = .8.sp)
        NativeText(value, 8.sp, Color(0xFF6E7772), FontWeight.Bold)
    }
}

/**
 * Transaction detail. [load] is the tapped transaction (see DetailLoad); null means a preview or
 * screenshot test, which shows the design's sample. [categories] fills the full category list;
 * [onSave] receives the transaction, the newly chosen category (null if unchanged) and the note.
 */
@Composable
internal fun DetailScreen(
    menu: () -> Unit, back: () -> Unit, load: DetailLoad? = null,
    categories: List<String> = emptyList(),
    onSave: (transaction: com.pingpaisa.app.data.Transaction, category: String?, note: String) -> Unit = { _, _, _ -> }
) {
    when (load) {
        null -> DetailSample(menu, back)
        DetailLoad.Loading -> Box(Modifier.fillMaxSize())
        DetailLoad.Missing -> DetailMissing(menu, back)
        is DetailLoad.Found -> DetailReal(menu, back, remember(load) { DetailUi.from(load.transaction, LocalDate.now()) },
            categories) { category, note -> onSave(load.transaction, category, note) }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DetailReal(
    menu: () -> Unit, back: () -> Unit, d: DetailUi,
    categories: List<String>, onSave: (category: String?, note: String) -> Unit
) {
    var chosen by rememberSaveable(d.id) { mutableStateOf(d.category) }
    var note by rememberSaveable(d.id) { mutableStateOf(d.note) }
    var showPicker by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val categoryChanged = d.canRecategorize && chosen != d.category
    val changed = categoryChanged || note.trim() != d.note
    ScreenScroll {
        NativeHeader("Transaction detail", menu = menu, back = back)
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().claySurface(RoundedCornerShape(28.dp), shadow = ClayShadow.Raised)) {
            NativeText(d.status, 7.sp, Color(0xFF5D806C), FontWeight.ExtraBold,
                Modifier.align(Alignment.TopEnd).padding(14.dp).background(Color(0xFFE3EDE0), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 5.dp))
            Column(Modifier.fillMaxWidth().padding(23.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ClayBadge(d.icon, d.tone, size = 58.dp)
                NativeText(d.amount, 30.sp, if (d.isIncome) Color(0xFF5C8B78) else Clay.Ink,
                    modifier = Modifier.padding(top = 13.dp, bottom = 3.dp), tracking = (-1.5).sp, maxLines = 1)
                Box(Modifier.heightIn(min = 24.dp), contentAlignment = Alignment.Center) {
                    NativeText(d.name, 12.sp, weight = FontWeight.Bold, maxLines = 2,
                        align = androidx.compose.ui.text.style.TextAlign.Center)
                }
                NativeText(d.subline, 8.sp, Color(0xFF979996), modifier = Modifier.padding(top = 4.dp, bottom = 15.dp))
                Canvas(Modifier.fillMaxWidth().height(1.dp)) {
                    drawLine(Color(0xFFDED9D2), Offset.Zero, Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
                }
                d.rows.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        NativeText(label, 8.sp, Color(0xFF959894), modifier = Modifier.weight(1f))
                        NativeText(value, 8.sp, maxLines = 1)
                    }
                }
            }
        }

        if (d.canRecategorize) {
            SectionTitle("Choose category", "All categories") { showPicker = true }
            NativeGrid(detailCategoryTiles(chosen), columns = 3) { tile, cell ->
                val active = tile.category != null && tile.category == chosen
                Box(cell.offset(y = if (active) (-2).dp else 0.dp).heightIn(min = 91.dp).claySurface(RoundedCornerShape(21.dp),
                    if (active) Color(0xFFF1F6EE) else Color(0xFFF8F5F0))
                    .then(if (active) Modifier.border(2.dp, Color(0xFF8EAE91), RoundedCornerShape(21.dp)) else Modifier)
                    .clickable(onClickLabel = tile.category?.let { "Choose $it" } ?: "Show all categories") {
                        if (tile.category == null) showPicker = true else chosen = tile.category
                    }) {
                    Column(Modifier.align(Alignment.Center).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ClayBadge(tile.icon, tile.tone)
                        NativeText(tile.label, 8.sp, weight = FontWeight.Bold, maxLines = 2,
                            align = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    if (active) Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(18.dp)
                        .background(Color(0xFF789F8E), CircleShape), contentAlignment = Alignment.Center) {
                        NativeIcon(R.drawable.ic_pingpaisa_check, 12.dp, Color.White)
                    }
                }
            }
            if (categoryChanged) NativeText(
                "Saving moves every payment to ${d.recipient} to $chosen and remembers it for next time.",
                8.sp, Color(0xFF6E7772), lineHeight = 12.sp, modifier = Modifier.padding(top = 10.dp, start = 3.dp, end = 3.dp))
        }

        Spacer(Modifier.height(18.dp))
        ClayTextField("Note", note, { note = it.take(120) }, placeholder = "Add a note, e.g. Coffee with Nisha")
        Spacer(Modifier.height(14.dp))
        PrimaryAction("Save changes", enabled = changed && !saved) {
            saved = true
            onSave(if (categoryChanged) chosen else null, note.trim())
            back()
        }
    }

    if (showPicker) ModalBottomSheet(onDismissRequest = { showPicker = false }, containerColor = Clay.Cream) {
        CategoryPicker(d.recipient, chosen, categories) { picked -> chosen = picked; showPicker = false }
    }
}

/**
 * The full list (the tracker's category sheet in the PingUI style): type a new category or
 * pick any existing one. Choosing only selects it; nothing is saved until "Save changes".
 */
@Composable
private fun CategoryPicker(recipient: String, current: String, categories: List<String>, onPick: (String) -> Unit) {
    var custom by remember { mutableStateOf("") }
    var tried by remember { mutableStateOf(false) }
    val error = if (tried) customCategoryError(custom) else null
    fun useCustom() { tried = true; if (customCategoryError(custom) == null) onPick(custom.trim()) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
        NativeText("Choose a category", 16.sp, tracking = (-.4).sp)
        NativeText(recipient, 9.sp, Color(0xFF8C918F), modifier = Modifier.padding(top = 3.dp, bottom = 14.dp))
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClayTextField("New category", custom, { custom = it.take(40) }, Modifier.weight(1f),
                placeholder = "e.g. Friends & Family", error = error,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { useCustom() }))
            PrimaryAction("Use", Modifier.width(76.dp).heightIn(min = 58.dp)) { useCustom() }
        }
        NativeText("ALL CATEGORIES", 8.sp, Color(0xFF849089), FontWeight.ExtraBold, tracking = 1.2.sp,
            modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
        categories.forEach { category ->
            val style = categoryStyle(category)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onPick(category) }
                .padding(vertical = 7.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ClayBadge(style.icon, style.tone, small = true)
                NativeText(category, 10.sp, modifier = Modifier.weight(1f), maxLines = 1)
                if (category == current) NativeText("✓", 12.sp, Color(0xFF5F8176), FontWeight.Bold)
            }
        }
    }
}

/** The tapped transaction no longer exists (or the app was restarted since it was tapped). */
@Composable
private fun DetailMissing(menu: () -> Unit, back: () -> Unit) {
    ScreenScroll {
        NativeHeader("Transaction detail", menu = menu, back = back)
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth().claySurface(RoundedCornerShape(24.dp)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ClayBadge(R.drawable.ic_pingpaisa_wallet, Tone.Cream)
            NativeText("This transaction isn't available", 11.sp, weight = FontWeight.Bold)
            NativeText("Open it again from Home or Stats.", 8.sp, Color(0xFF8C918F))
        }
        Spacer(Modifier.height(16.dp))
        PrimaryAction("Go back", onClick = back)
    }
}

@Composable
private fun DetailSample(menu: () -> Unit, back: () -> Unit) {
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

/**
 * Add an expense or an income. [onSave] receives a checked amount, the name, whether it is
 * income, and the chosen category (null = pick it automatically from the name, as the old
 * tracker did). When [onSave] is null (previews and the screenshot tests) the screen shows the
 * design's sample values and the button only goes back.
 */
@Composable
internal fun EntryScreen(
    menu: () -> Unit, back: () -> Unit,
    onSave: ((amount: Double, name: String, isIncome: Boolean, category: String?) -> Unit)? = null
) {
    val demo = onSave == null
    var amount by rememberSaveable { mutableStateOf(if (demo) "1250" else "") }
    var type by rememberSaveable { mutableStateOf("Expense") }
    var category by rememberSaveable { mutableStateOf<String?>(if (demo) "Food" else null) }
    var name by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }   // the page stays tappable while it slides away
    val isIncome = type == "Income"
    val errors = if (submitted) EntryValidation.validate(amount, name, isIncome) else EntryErrors()
    val focus = LocalFocusManager.current
    ScreenScroll {
        NativeHeader(if (isIncome) "Add income" else "Add an expense", "NEW TRANSACTION", menu, back,
            action = { RoundAction(R.drawable.ic_pingpaisa_close, "Close", onClick = back) })
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().heightIn(min = 112.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            NativeText("₹", 22.sp, Color(0xFF859089), modifier = Modifier.padding(end = 7.dp))
            NativeText(AmountInput.display(amount), 46.sp, weight = FontWeight.Bold, tracking = (-2.76).sp, maxLines = 1)
            Box(Modifier.padding(start = 4.dp).width(2.dp).height(40.dp).background(Color(0xFF769A8E)))
        }
        errors.amount?.let {
            NativeText(it, 8.sp, Color(0xFFB0675C), modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp))
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
        Spacer(Modifier.height(16.dp))
        ClayTextField(if (isIncome) "Received from" else "Paid to", name, { name = it },
            placeholder = if (isIncome) "e.g. Freelance client" else "e.g. Vegetable vendor", error = errors.name,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
        if (!isIncome) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                nativeCategories.forEach { item ->
                    val chosen = category == item.label
                    // Tap to choose, tap again to go back to automatic.
                    Column(Modifier.alpha(if (category != null && !chosen) .45f else 1f)
                        .clickable(onClickLabel = if (chosen) "Pick category automatically" else "Choose ${item.label}") {
                            category = if (chosen) null else item.label
                        }, horizontalAlignment = Alignment.CenterHorizontally) {
                        ClayBadge(item.icon, item.tone, small = true)
                        NativeText(item.label, 7.sp, if (chosen && !demo) Color(0xFF5F8176) else Clay.Ink,
                            if (chosen && !demo) FontWeight.ExtraBold else FontWeight.Bold, Modifier.padding(top = 5.dp))
                    }
                }
            }
            if (!demo) NativeText(
                if (category == null) "Category is picked from the name. Tap one to choose it yourself."
                else "Tap $category again to pick it automatically.",
                7.sp, Color(0xFF999999), modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
        }
        Spacer(Modifier.height(17.dp))
        Column(Modifier.padding(horizontal = 7.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            listOf("1","2","3","4","5","6","7","8","9",".","0","⌫").chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    row.forEach { key ->
                        Box(Modifier.weight(1f).heightIn(min = 55.dp)
                            .claySurface(RoundedCornerShape(19.dp), Color(0xFFF7F3EE))
                            .clickable { amount = AmountInput.applyKey(amount, key) }, contentAlignment = Alignment.Center) {
                            NativeText(key, 17.sp, weight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(17.dp))
        PrimaryAction("Add ${type.lowercase()}") {
            if (onSave == null) { back(); return@PrimaryAction }
            if (saved) return@PrimaryAction
            focus.clearFocus()
            submitted = true
            if (EntryValidation.validate(amount, name, isIncome).isValid) {
                saved = true
                onSave(amount.toDouble(), name.trim(), isIncome, if (isIncome) null else category?.let(::datasetCategoryFor))
                back()
            }
        }
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

/**
 * [ui] is the real list (see RecurringUi.from); the default is the design's sample, used by
 * previews and the screenshot tests. With [onAdd] set (the real app) the header gets a + button,
 * an auto-detected row opens a prefilled "confirm" form, and a saved row opens its details with Delete.
 */
@Composable
internal fun RecurringScreen(
    menu: () -> Unit, back: () -> Unit, ui: RecurringUi = RecurringUi.Sample,
    onAdd: ((name: String, amount: Double, day: Int, type: String) -> Unit)? = null,
    onDelete: (itemId: Int) -> Unit = {}
) {
    var filter by rememberSaveable { mutableStateOf("All") }
    var form by remember { mutableStateOf<RecurringDraft?>(null) }
    var managing by remember { mutableStateOf<RecurringRow?>(null) }
    ScreenScroll {
        NativeHeader("Recurring payments", "UPCOMING", menu, back, action = {
            if (onAdd != null) RoundAction(R.drawable.ic_pingpaisa_plus, "Add recurring payment") { form = RecurringDraft() }
        })
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().height(104.dp).claySurface(RoundedCornerShape(26.dp), shadow = ClayShadow.Raised,
            gradient = Color(0xFFF5DDC5) to Color(0xFFEDC9A7)).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { NativeText("Due this month", 8.sp, Color(0xFF8B796B)); NativeText(ui.dueThisMonth, 25.sp, modifier = Modifier.padding(vertical = 4.dp), maxLines = 1) }
            Column(Modifier.width(56.dp).height(62.dp).claySurface(RoundedCornerShape(15.dp), Color(0xFFFAF5EF)), horizontalAlignment = Alignment.CenterHorizontally) {
                NativeText(ui.calendarMonth, 7.sp, Color.White, FontWeight.ExtraBold, Modifier.fillMaxWidth().background(Color(0xFFCD8B7F)).padding(5.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
                NativeText(ui.calendarDay, 20.sp, weight = FontWeight.Bold, modifier = Modifier.padding(7.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        SegmentedTabs(listOf("All","EMI","SIP","Other"), filter, { filter = it })
        Spacer(Modifier.height(17.dp))
        val rows = ui.filtered(filter)
        if (ui.isEmpty) RecurringEmptyCard()
        else if (rows.isEmpty()) NativeText("No ${filter} payments yet.", 9.sp, Color(0xFF999999),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 14.dp))
        rows.forEach { item ->
            Row(Modifier.padding(bottom = 10.dp).fillMaxWidth().claySurface(RoundedCornerShape(22.dp), Color(0xFFFAF8F5))
                .then(if (onAdd == null) Modifier else Modifier.clickable(
                    onClickLabel = if (item.detected) "Confirm ${item.name}" else "Show ${item.name}") {
                    if (item.detected) form = RecurringDraft.fromDetected(item) else if (item.itemId != null) managing = item
                }).padding(11.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProgressRing(item.progress, item.tone.ink, 45.dp) { NativeIcon(item.icon, 18.dp, item.tone.ink) }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NativeText(item.name, 9.sp, weight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        if (item.detected) NativeText("AUTO-DETECTED", 5.sp, Tone.Lavender.ink,
                            modifier = Modifier.padding(start = 5.dp).background(Color(0xFFE3DEF2), CircleShape).padding(horizontal = 4.dp, vertical = 3.dp))
                    }
                    NativeText(item.dueText, 7.sp, Color(0xFF999999))
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

    form?.let { draft ->
        RecurringFormSheet(draft, onDismiss = { form = null }) { name, amount, day, type ->
            onAdd?.invoke(name, amount, day, type)
            form = null
        }
    }
    managing?.let { row ->
        RecurringManageSheet(row, onDismiss = { managing = null }) {
            row.itemId?.let(onDelete)
            managing = null
        }
    }
}

private val DeleteInk = Color(0xFFB0675C)

/** Add a recurring payment, or confirm one PingPaisa detected (the old AddRecurringDialog). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RecurringFormSheet(
    draft: RecurringDraft, onDismiss: () -> Unit,
    onSave: (name: String, amount: Double, day: Int, type: String) -> Unit
) {
    var name by remember(draft) { mutableStateOf(draft.name) }
    var amount by remember(draft) { mutableStateOf(draft.amount) }
    var day by remember(draft) { mutableStateOf(draft.day) }
    var type by remember(draft) { mutableStateOf(draft.type) }
    var tried by remember(draft) { mutableStateOf(false) }
    val errors = if (tried) RecurringValidation.validate(name, amount, day) else RecurringErrors()
    val focus = LocalFocusManager.current
    val next = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) })
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Clay.Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NativeText(if (draft.confirming) "Confirm recurring payment" else "Add recurring payment", 16.sp, tracking = (-.4).sp)
            if (draft.confirming) NativeText("PingPaisa noticed this payment going out every month. Check the details and choose its type.",
                9.sp, Color(0xFF8C918F), lineHeight = 13.sp)
            ClayTextField("Name", name, { name = it.take(60) }, placeholder = "e.g. Bajaj Finserv EMI", error = errors.name,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                keyboardActions = next)
            ClayTextField("Amount every month", amount, { if (RecurringValidation.isAmountText(it)) amount = it },
                placeholder = "e.g. 24500", prefix = "₹", error = errors.amount,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                keyboardActions = next)
            ClayTextField("Day of the month it's due", day, { if (it.length <= 2 && it.all(Char::isDigit)) day = it },
                placeholder = "1 to 31", error = errors.day,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
            NativeText("TYPE", 8.sp, Color(0xFF849089), FontWeight.ExtraBold, tracking = 1.2.sp)
            SegmentedTabs(listOf("EMI", "SIP", "Other"), type) { type = it }
            NativeText(when (type) {
                "EMI" -> "A loan repayment. It counts towards the EMI share of your income in the health score and loan planner."
                "SIP" -> "A monthly investment, such as a mutual fund SIP."
                else -> "A subscription, rent, a bill or anything else that repeats."
            }, 8.sp, Color(0xFF8C918F), lineHeight = 12.sp)
            PrimaryAction(if (draft.confirming) "Confirm" else "Save", Modifier.heightIn(min = 52.dp)) {
                tried = true
                focus.clearFocus()
                if (RecurringValidation.validate(name, amount, day).isValid) onSave(name.trim(), amount.toDouble(), day.toInt(), type)
            }
        }
    }
}

/** Details of a saved recurring payment, with Delete (after a confirmation). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RecurringManageSheet(row: RecurringRow, onDismiss: () -> Unit, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Clay.Cream) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ClayBadge(row.icon, row.tone)
                Column(Modifier.weight(1f)) {
                    NativeText(row.name, 16.sp, tracking = (-.4).sp, maxLines = 2)
                    NativeText("${row.type ?: "Recurring"} • ${row.amount} every month", 9.sp, Color(0xFF8C918F),
                        modifier = Modifier.padding(top = 3.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            listOfNotNull(
                row.dayOfMonth?.let { "Due" to "Day $it of every month" },
                "Next payment" to row.dueText
            ).forEach { (label, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    NativeText(label, 9.sp, Color(0xFF959894), modifier = Modifier.weight(1f))
                    NativeText(value, 9.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            NativeText("Delete this payment", 11.sp, DeleteInk, FontWeight.ExtraBold,
                Modifier.align(Alignment.CenterHorizontally).clip(CircleShape).clickable { confirm = true }
                    .padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { NativeText("Delete ${row.name}?", 18.sp) },
        text = {
            NativeText("It's removed from your upcoming payments" +
                (if (row.type == "EMI") " and from the EMI total in your health score" else "") +
                ". Past transactions aren't changed.", 12.sp, lineHeight = 18.sp)
        },
        confirmButton = { TextButton({ confirm = false; onDelete() }) { NativeText("Delete", 12.sp, DeleteInk, FontWeight.Bold) } },
        dismissButton = { TextButton({ confirm = false }) { NativeText("Cancel", 12.sp) } }
    )
}

/** No recurring payments at all yet. */
@Composable
private fun RecurringEmptyCard() {
    Column(
        Modifier.padding(bottom = 12.dp).fillMaxWidth().claySurface(RoundedCornerShape(22.dp), Color(0xFFFAF8F5)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ClayBadge(R.drawable.ic_pingpaisa_repeat, Tone.Lavender)
        NativeText("No recurring payments yet", 11.sp, weight = FontWeight.Bold)
        NativeText("Payments you make every month, like an EMI or a subscription, show up here once PingPaisa has seen them a few times.",
            8.sp, Color(0xFF8C918F), align = androidx.compose.ui.text.style.TextAlign.Center)
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

/**
 * [baseline] is this month's real numbers (from the health score); null means a preview or
 * screenshot test, which shows the design's sample. Bank quotes, their comparison and the
 * Safe / Tight / Risky verdict come in step 40.
 */
@Composable
internal fun LoanScreen(menu: () -> Unit, back: () -> Unit, baseline: com.pingpaisa.app.healthscore.ScoreCalculator.RawFactors? = null) {
    if (baseline == null) LoanSample(menu, back) else LoanReal(menu, back, baseline)
}

private val RiskInk = mapOf("Low risk" to Color(0xFFDCE9D8), "Medium risk" to Color(0xFFF7E8D9), "High risk" to Color(0xFFF6D8D8))

@Composable
private fun LoanReal(menu: () -> Unit, back: () -> Unit, baseline: com.pingpaisa.app.healthscore.ScoreCalculator.RawFactors) {
    var requested by rememberSaveable { mutableFloatStateOf(300000f) }      // the tracker's starting amount
    var tenureMonths by rememberSaveable { mutableIntStateOf(36) }
    var custom by rememberSaveable { mutableStateOf(false) }
    var customText by rememberSaveable { mutableStateOf("") }
    var showPlan by rememberSaveable { mutableStateOf(false) }
    // Bank quotes are kept on the phone with the tracker's QuoteStore (they survive restarts).
    val context = androidx.compose.ui.platform.LocalContext.current
    var quotes by remember { mutableStateOf(com.pingpaisa.app.healthscore.QuoteStore.load(context)) }
    var selectedBank by rememberSaveable { mutableStateOf<String?>(null) }
    fun saveQuotes(list: List<com.pingpaisa.app.healthscore.LoanQuote>) {
        quotes = list
        com.pingpaisa.app.healthscore.QuoteStore.save(context, list)
    }
    val ui = remember(baseline, requested, tenureMonths, quotes, selectedBank) {
        LoanUi.from(baseline, requested, tenureMonths, quotes, selectedBank)
    }
    ScreenScroll {
        NativeHeader("Loan simulator", "PLAN SAFELY", menu, back, action = { ClayBadge(R.drawable.ic_pingpaisa_sparkle, Tone.Lavender) })
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth().claySurface(RoundedCornerShape(27.dp), shadow = ClayShadow.Raised).padding(18.dp)) {
            NativeText("LOAN AMOUNT", 7.sp, Color(0xFF999999), modifier = Modifier.align(Alignment.CenterHorizontally), tracking = .91.sp)
            if (ui.canBorrow) {
                NativeText(ui.amountText, 29.sp, tracking = (-1.45).sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 7.dp, bottom = 14.dp))
                LoanAmountSlider(ui.amount, ui.minAmount, ui.maxAmount, LoanUi.STEP) { requested = it }
                Row { NativeText(ui.minLabel, 6.sp, Color(0xFFAAAAAA), modifier = Modifier.weight(1f)); NativeText("Max ${ui.maxLabel}", 6.sp, Color(0xFFAAAAAA)) }
                NativeText(ui.limitNote, 7.sp, Color(0xFF8C918F), lineHeight = 10.5.sp, modifier = Modifier.padding(top = 10.dp))
            } else {
                NativeText(if (!ui.hasIncome) "Add your monthly income to see how much you can borrow."
                    else "Your spending and EMIs already use most of your income, so there's no room for a new EMI right now. A longer tenure may help.",
                    9.sp, Color(0xFF8B796B), lineHeight = 13.sp, align = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 17.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NativeText("Tenure", 8.sp, modifier = Modifier.weight(1f))
                listOf(1, 3, 5).forEach { year ->
                    NativeText(if (year == 1) "1 yr" else "$year yrs", 7.sp,
                        modifier = Modifier.background(if (!custom && tenureMonths == year * 12) Color(0xFFC8D9C3) else Color(0xFFEFEDE8), CircleShape)
                            .clickable { custom = false; tenureMonths = year * 12 }.padding(horizontal = 10.dp, vertical = 7.dp))
                }
                // The tracker's custom tenure (1 to 30 years).
                NativeText(if (custom) "${tenureMonths / 12} yrs" else "+", 7.sp,
                    modifier = Modifier.background(if (custom) Color(0xFFC8D9C3) else Color(0xFFEFEDE8), CircleShape)
                        .clickable(onClickLabel = "Custom tenure") {
                            custom = !custom
                            if (custom) customText = (tenureMonths / 12).toString()
                        }.padding(horizontal = 10.dp, vertical = 7.dp))
            }
            if (custom) {
                Spacer(Modifier.height(10.dp))
                ClayTextField("Years (1 to ${LoanUi.MAX_TENURE_YEARS})", customText, { text ->
                    if (text.length <= 2 && text.all(Char::isDigit)) {
                        customText = text
                        LoanUi.customYears(text)?.let { tenureMonths = it * 12 }
                    }
                }, placeholder = "e.g. 7",
                    error = if (customText.isNotEmpty() && LoanUi.customYears(customText) == null) "Choose 1 to ${LoanUi.MAX_TENURE_YEARS} years" else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done))
            }
            NativeText("A longer tenure lowers the EMI but adds more interest.", 7.sp, Color(0xFF999999), modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(14.dp))
        QuotesCard(ui, hasQuotes = quotes.isNotEmpty(),
            onAdd = { quote -> saveQuotes(LoanUi.withQuote(quotes, quote)); selectedBank = quote.bankName },
            onSelect = { selectedBank = it },
            onDelete = { bank ->
                saveQuotes(quotes.filter { it.bankName != bank })
                if (selectedBank == bank) selectedBank = null
            })
        if (ui.canBorrow) {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().claySurface(RoundedCornerShape(23.dp),
                gradient = Color(0xFFD9E7D5) to Color(0xFFBDD3B8)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    NativeText("ESTIMATED EMI", 6.sp, tracking = .6.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) { NativeText(ui.emiText, 20.sp, modifier = Modifier.padding(vertical = 4.dp)); NativeText("/mo", 8.sp, weight = FontWeight.Medium) }
                    ui.selectedBank?.let { NativeText("with $it", 7.sp, Color(0xFF5E7A6C)) }
                }
                Column(horizontalAlignment = Alignment.End) {
                    NativeText("INTEREST", 6.sp, tracking = .6.sp); NativeText(ui.rateText, 12.sp, weight = FontWeight.Bold)
                    if (ui.rateAssumed) NativeText("assumed", 6.sp, Color(0xFF5E7A6C))
                }
            }
            Spacer(Modifier.height(13.dp))
            Column(Modifier.fillMaxWidth().claySurface(RoundedCornerShape(25.dp)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { NativeText("HEALTH SCORE IMPACT", 6.sp, tracking = .6.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NativeText("${ui.scoreAfter}", 22.sp, modifier = Modifier.padding(vertical = 3.dp))
                            NativeText(" from ${ui.scoreBefore}", 7.sp, Color(0xFF999999))
                        } }
                    NativeText(ui.riskAfter, 7.sp,
                        modifier = Modifier.background(RiskInk[ui.riskAfter] ?: Color(0xFFDCE9D8), CircleShape).padding(horizontal = 9.dp, vertical = 6.dp), weight = FontWeight.Bold)
                }
                Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp).height(18.dp), contentAlignment = Alignment.CenterStart) {
                    Box(Modifier.fillMaxWidth().height(10.dp).background(Brush.horizontalGradient(listOf(Color(0xFFE5A7A4),Color(0xFFEFD39F),Color(0xFF95B691))), CircleShape))
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        Box(Modifier.offset(x = (maxWidth - 18.dp) * (ui.scoreAfter / 100f).coerceIn(0f, 1f)).size(18.dp)
                            .background(Color.White, CircleShape).border(4.dp, Color(0xFF5C7A6D), CircleShape))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf("Risky","Balanced","Healthy").forEach { NativeText(it, 6.sp, Color(0xFFAAAAAA)) } }
                Row(Modifier.padding(top = 12.dp).fillMaxWidth().background(Color(0xFFEEE9F5), RoundedCornerShape(13.dp)).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    NativeIcon(R.drawable.ic_pingpaisa_sparkle, 16.dp, Color(0xFF716A7D))
                    NativeText(ui.incomeShare, 7.sp, Color(0xFF716A7D))
                }
            }
            if (ui.verdict.isNotEmpty()) {
                Spacer(Modifier.height(13.dp))
                VerdictCard(ui)
            }
            Spacer(Modifier.height(15.dp))
            PrimaryAction(if (showPlan) "Hide repayment plan" else "View repayment plan") { showPlan = !showPlan }
            if (showPlan) NativeText(ui.planText, 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 15.dp))
        }
    }
}

/** Fill and text colour of the SAFE / TIGHT / RISKY pills and the verdict card. */
private fun verdictColors(verdict: String): Pair<Color, Color> = when (verdict) {
    "SAFE" -> Color(0xFFDCE9D8) to Color(0xFF5D806C)
    "TIGHT" -> Color(0xFFF7E8D9) to Color(0xFFA37452)
    else -> Color(0xFFF6D8D8) to Color(0xFF9E6868)
}

@Composable
private fun VerdictPill(verdict: String) {
    val (fill, ink) = verdictColors(verdict)
    NativeText(verdict, 6.sp, ink, FontWeight.ExtraBold, tracking = .6.sp,
        modifier = Modifier.background(fill, CircleShape).padding(horizontal = 7.dp, vertical = 4.dp))
}

/** Add bank quotes and compare them (the tracker's quote and compare cards, in the clay style). */
@Composable
private fun QuotesCard(
    ui: LoanUi, hasQuotes: Boolean,
    onAdd: (com.pingpaisa.app.healthscore.LoanQuote) -> Unit, onSelect: (String) -> Unit, onDelete: (String) -> Unit
) {
    var bank by rememberSaveable { mutableStateOf("") }
    var rate by rememberSaveable { mutableStateOf("") }
    var tried by remember { mutableStateOf(false) }
    val (bankError, rateError) = if (tried) LoanUi.quoteErrors(bank, rate) else (null to null)
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxWidth().claySurface(RoundedCornerShape(25.dp)).padding(16.dp)) {
        NativeText("BANK QUOTES", 6.sp, tracking = .6.sp)
        NativeText("Ask banks for their rate, add each one here, then compare.", 7.sp, Color(0xFF999999), modifier = Modifier.padding(top = 3.dp, bottom = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            ClayTextField("Bank", bank, { if (it.length <= LoanUi.MAX_BANK_NAME) bank = it }, Modifier.weight(1.4f),
                placeholder = "e.g. HDFC", error = bankError,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }))
            ClayTextField("Rate a year", rate, { if (Regex("""\d{0,2}(\.\d{0,2})?""").matches(it)) rate = it }, Modifier.weight(1f),
                placeholder = "10.5", error = rateError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                trailing = { NativeText("%", 12.sp, Color(0xFF859089)) })
        }
        Spacer(Modifier.height(10.dp))
        PrimaryAction("Add quote", Modifier.heightIn(min = 46.dp)) {
            tried = true
            val quote = LoanUi.quoteFrom(bank, rate)
            if (quote != null) {
                onAdd(quote)
                bank = ""; rate = ""; tried = false
                focus.clearFocus()
            }
        }
        when {
            !hasQuotes -> NativeText("No quotes yet. Until you add one, an assumed 11% is used.", 7.sp, Color(0xFF999999),
                modifier = Modifier.padding(top = 10.dp))
            ui.quotes.isEmpty() -> NativeText("The comparison appears once there's room to borrow.", 7.sp, Color(0xFF999999),
                modifier = Modifier.padding(top = 10.dp))
            else -> {
                NativeText("Safest first, then the lowest total interest. Tap one to see its numbers.", 7.sp, Color(0xFF999999),
                    modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                ui.quotes.forEach { q ->
                    Row(Modifier.padding(top = 6.dp).fillMaxWidth()
                        .background(if (q.selected) Color(0xFFEEF4EB) else Color(0xFFF7F4EF), RoundedCornerShape(16.dp))
                        .then(if (q.selected) Modifier.border(1.5.dp, Color(0xFF8EAE91), RoundedCornerShape(16.dp)) else Modifier)
                        .clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = "Show ${q.bank}") { onSelect(q.bank) }
                        .padding(start = 10.dp, top = 9.dp, bottom = 9.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VerdictPill(q.verdict)
                        Column(Modifier.weight(1f)) {
                            NativeText(q.bank, 9.sp, weight = FontWeight.Bold, maxLines = 1)
                            NativeText("${q.rateText} • ${q.interestText}", 7.sp, Color(0xFF999999))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            NativeText(q.emiText, 9.sp, weight = FontWeight.Bold)
                            NativeText("/mo", 6.sp, Color(0xFF999999))
                        }
                        Box(Modifier.size(30.dp).clip(CircleShape).clickable(onClickLabel = "Remove ${q.bank}") { onDelete(q.bank) },
                            contentAlignment = Alignment.Center) { NativeIcon(R.drawable.ic_pingpaisa_close, 14.dp, Color(0xFFAFB1AE)) }
                    }
                }
            }
        }
    }
}

/** Safe / Tight / Risky with the reasons, from the tracker's VerdictEngine. */
@Composable
private fun VerdictCard(ui: LoanUi) {
    val (fill, ink) = verdictColors(ui.verdict)
    Column(Modifier.fillMaxWidth().claySurface(RoundedCornerShape(25.dp), fill.copy(alpha = .55f)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VerdictPill(ui.verdict)
            NativeText(ui.verdictTitle, 11.sp, ink, FontWeight.Bold, maxLines = 2)
        }
        ui.verdictReasons.forEach { reason ->
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.padding(top = 4.dp).size(4.dp).background(ink, CircleShape))
                NativeText(reason, 8.sp, Color(0xFF55605B), lineHeight = 12.sp)
            }
        }
        if (ui.rateAssumed) NativeText("Based on an assumed 11% rate. Add a bank quote for real numbers.", 7.sp, Color(0xFF8C918F),
            modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun LoanSample(menu: () -> Unit, back: () -> Unit) {
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

/** The design's slider. Defaults are the sample's ₹1L–₹20L in ₹50K steps; the real screen passes the loan limit. */
@Composable
private fun LoanAmountSlider(
    value: Float, min: Float = 100000f, max: Float = 2000000f, step: Float = 50000f, onChange: (Float) -> Unit
) {
    val change by rememberUpdatedState(onChange)
    val span = (max - min).coerceAtLeast(step)
    fun stepped(fraction: Float) = (min + (fraction.coerceIn(0f, 1f) * span / step).roundToInt() * step).coerceIn(min, max)
    Canvas(Modifier.fillMaxWidth().height(24.dp)
        .semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(value, min..max, ((span / step).toInt() - 1).coerceAtLeast(0))
            setProgress { change(stepped((it - min) / span)); true }
        }
        .pointerInput(min, max, step) {
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
        val x = 8.dp.toPx() + ((value - min) / span).coerceIn(0f, 1f) * (size.width - 16.dp.toPx())
        drawLine(Color(0xFFDDDDDD), Offset(8.dp.toPx(), center.y), Offset(size.width - 8.dp.toPx(), center.y), 5.dp.toPx(), StrokeCap.Round)
        drawLine(Clay.Teal, Offset(8.dp.toPx(), center.y), Offset(x, center.y), 5.dp.toPx(), StrokeCap.Round)
        drawCircle(Clay.Teal, 8.dp.toPx(), Offset(x, center.y))
    }
}
