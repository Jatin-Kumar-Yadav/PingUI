@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.pingpaisa.app.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingpaisa.app.catalog.CategoryRepository
import com.pingpaisa.app.data.PriceSource
import com.pingpaisa.app.data.Product
import com.pingpaisa.app.finance.Assessment
import com.pingpaisa.app.finance.Decision
import com.pingpaisa.app.finance.LineAssessment
import com.pingpaisa.app.finance.Priority
import com.pingpaisa.app.ui.theme.DropdownPicker
import com.pingpaisa.app.ui.theme.PP
import com.pingpaisa.app.ui.theme.fieldColors
import com.pingpaisa.app.util.Money

private fun priceToText(p: Double?): String = when {
    p == null -> ""
    p % 1.0 == 0.0 -> p.toLong().toString()
    else -> "%.2f".format(p)
}

private fun sourceText(p: Product): String = when (p.priceSource) {
    PriceSource.OCR -> "Read from the image" + (p.priceConfidence?.let { " · ${(it * 100).toInt()}% confident" } ?: "")
    PriceSource.PAGE -> "Listed on the product page - may differ at checkout"
    PriceSource.SHARED_TEXT -> "Taken from the shared message"
    PriceSource.MANUAL -> "Entered by you"
    PriceSource.NONE -> "No price found - please enter it"
}

@Composable
fun EditProductSheet(
    product: Product, repo: CategoryRepository, line: LineAssessment?,
    onSave: (name: String, price: Double?, category: String, subcategory: String, priority: Priority, variant: String?) -> Unit,
    onRemove: () -> Unit, onDismiss: () -> Unit,
    review: Boolean = false,
    advice: com.pingpaisa.app.finance.Advice? = null, emiBusy: Boolean = false, emiNotes: List<String> = emptyList(),
    onEmiAvailable: (Boolean?) -> Unit = {}, onEmiUpload: () -> Unit = {}, onEmiCapture: () -> Unit = {},
    onEmiSave: (com.pingpaisa.app.finance.EmiOption) -> Unit = {}, onEmiRemove: (String) -> Unit = {}
) {
    var name by remember(product.id) { mutableStateOf(product.name) }
    var priceText by remember(product.id) { mutableStateOf(priceToText(product.price)) }
    var category by remember(product.id) { mutableStateOf(product.category) }
    var sub by remember(product.id) { mutableStateOf(product.subcategory) }
    var priority by remember(product.id) { mutableStateOf(product.priority) }
    var variant by remember(product.id) { mutableStateOf(product.variant.orEmpty()) }

    val parsed = Money.parse(priceText)
    val priceError = priceText.isNotBlank() && (parsed == null || parsed <= 0.0 || parsed > 100_000_000.0)
    val subs = repo.subcategoriesOf(category)

    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = PP.Surface, contentColor = PP.TextHi,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProductThumb(product.imagePath, 52.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(if (review) "Confirm this scan" else "Edit product", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    if (review) Text("Check the name and the selling price. Nothing is judged until you confirm.", fontSize = 12.sp, color = Amber)
                    product.message?.let { Text(it, fontSize = 12.sp, color = PP.TextLo) }
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                name, { name = it }, label = { Text(if (product.nameIsGuess) "Product name (please confirm)" else "Product name") },
                singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                variant, { variant = it.take(80) }, label = { Text("Selected variant (optional)") },
                singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                priceText, { v -> priceText = v.filter { it.isDigit() || it == '.' }.take(12) },
                label = { Text("Price") }, prefix = { Text("₹ ") }, singleLine = true, isError = priceError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = fieldColors(),
                supportingText = {
                    Text(
                        if (priceError) "Enter a valid amount"
                        else (parsed?.let { Money.format(it) + " · " } ?: "") + sourceText(product)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            product.priceNote?.let { Text(it, fontSize = 12.sp, color = PP.Accent, modifier = Modifier.padding(top = 2.dp)) }
            val others = product.priceCandidates.distinct()
            if (others.size > 1 || (product.price == null && others.isNotEmpty()) || (review && others.isNotEmpty())) {
                Spacer(Modifier.height(6.dp))
                Text(if (product.price == null) "Prices found in the image - tap the selling price" else "Selling-price candidates - tap to use", fontSize = 12.sp, color = PP.TextLo)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    others.take(6).forEach { c ->
                        AssistChip(onClick = { priceText = priceToText(c) }, label = { Text(Money.format(c), color = PP.TextHi) })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            DropdownPicker("Category", category, repo.categoryNames, { c ->
                category = c; sub = repo.subcategoriesOf(c).firstOrNull() ?: CategoryRepository.UNCATEGORIZED
            })
            Spacer(Modifier.height(10.dp))
            DropdownPicker("Subcategory", sub, subs, { sub = it })
            Text(product.classEvidence, fontSize = 11.sp, color = PP.TextLo, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(12.dp))
            Text("How much do you need it?", fontSize = 12.sp, color = PP.TextLo)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Priority.values().forEach { p ->
                    FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(p.label) })
                }
            }
            if (line != null && line.reason.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(line.reason, fontSize = 12.sp, color = PP.TextHi)
            }
            // EMI offers are compared with the price typed above (once Saved, it becomes the product's price)
            val draftPrice = if (priceError) null else parsed?.takeIf { it > 0.0 }
            EmiSection(product.copy(price = draftPrice ?: product.price), advice, emiBusy, emiNotes, onEmiAvailable, onEmiUpload, onEmiCapture, onEmiSave, onEmiRemove)
            if (draftPrice != null && draftPrice != product.price) {
                Text("Press Save to use the new price in the comparison.", fontSize = 11.sp, color = Amber, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRemove) { Text("Remove", color = PP.Error) }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onSave(name, if (priceText.isBlank()) null else parsed, category, sub, priority, variant) },
                    enabled = !priceError && name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PP.Accent, contentColor = PP.Ink)
                ) { Text(if (review) "Confirm" else "Save", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
internal fun Section(title: String) {
    Text(title.uppercase(), fontSize = 10.sp, letterSpacing = 1.2.sp, color = PP.TextLo, fontWeight = FontWeight.Medium)
    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = PP.Divider)
}

@Composable
internal fun Line(label: String, value: String, bold: Boolean = false, valueColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f).padding(end = 8.dp), fontSize = 13.sp, color = if (bold) PP.TextHi else PP.TextLo)
        Text(value, fontSize = 13.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = valueColor)
    }
}

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = PP.Surface, titleContentColor = PP.TextHi, textContentColor = PP.TextLo,
        title = { Text("How to scan a price") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "1. Hold the camera so the price tag sits inside the white corners. The price shows up in the dark box when it's readable.\n\n" +
                        "2. Tap the round button to capture. PingPaisa reads the price and name, then matches a category from your dataset.\n\n" +
                        "3. Use the photo button (bottom-left) to add one or many pictures or screenshots from your gallery. Tap + in the list to add more.\n\n" +
                        "4. Tap any product to fix its name, price, category or priority. Totals and the verdict update instantly.\n\n" +
                        "5. Shopping apps: use Share → PingPaisa on an Amazon or Flipkart product to send the link straight here.\n\n" +
                        "Tip: MRP and selling price are told apart when both are visible; check the price if the box says \"check this price\".",
                    fontSize = 13.sp, lineHeight = 18.sp
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it", color = PP.Accent) } }
    )
}
