@file:OptIn(ExperimentalLayoutApi::class)

package com.pingpaisa.app.ui.scan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingpaisa.app.data.Product
import com.pingpaisa.app.finance.Advice
import com.pingpaisa.app.finance.EmiCalculator
import com.pingpaisa.app.finance.EmiOption
import com.pingpaisa.app.finance.EmiPlan
import com.pingpaisa.app.finance.EmiSource
import com.pingpaisa.app.finance.IssueLevel
import com.pingpaisa.app.finance.Suitability
import com.pingpaisa.app.ui.theme.PP
import com.pingpaisa.app.ui.theme.fieldColors
import com.pingpaisa.app.util.Money
import java.math.BigDecimal

internal val Amber = Color(0xFFFFD27A)

internal fun suitabilityLabel(s: Suitability) = when (s) {
    Suitability.SUITABLE -> "Suitable"
    Suitability.CAUTION -> "Caution"
    Suitability.NOT_RECOMMENDED -> "Not recommended"
    Suitability.INCOMPLETE -> "Needs details"
}

internal fun suitabilityColor(s: Suitability) = when (s) {
    Suitability.SUITABLE -> PP.Accent
    Suitability.CAUTION -> Amber
    Suitability.NOT_RECOMMENDED -> PP.Error
    Suitability.INCOMPLETE -> PP.TextLo
}

/**
 * The EMI part of the Edit Product sheet. Nothing EMI-specific is shown until the user answers Yes.
 * Saving an option applies immediately (options may also arrive asynchronously from a screenshot).
 */
@Composable
fun EmiSection(
    product: Product, advice: Advice?, busy: Boolean, notes: List<String>,
    onAvailable: (Boolean?) -> Unit, onUpload: () -> Unit, onCapture: () -> Unit = {},
    onSave: (EmiOption) -> Unit, onRemove: (String) -> Unit
) {
    var editingId by remember(product.id) { mutableStateOf<String?>(null) }
    val price = product.price?.let { BigDecimal.valueOf(it) }

    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = PP.Divider)
        Text("Are EMI options available for this product?", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PP.TextHi)
        Spacer(Modifier.height(4.dp))
        ChoiceRow(product.emiAvailable == true, "Yes, EMI options are available.") { onAvailable(true) }
        ChoiceRow(product.emiAvailable == false, "No, EMI options are not available.") { onAvailable(false); editingId = null }

        if (product.emiAvailable == true) {
            Spacer(Modifier.height(8.dp))
            if (price == null) {
                Text("Add the product price above first - EMI offers are compared with it.", fontSize = 12.sp, color = Amber)
                Spacer(Modifier.height(6.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onCapture, enabled = !busy, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                    Text("Take a photo", fontSize = 12.sp, color = PP.TextHi, maxLines = 1, softWrap = false)
                }
                OutlinedButton(onClick = onUpload, enabled = !busy, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                    Text("Upload screenshot", fontSize = 12.sp, color = PP.TextHi, maxLines = 1, softWrap = false)
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { editingId = NEW }, modifier = Modifier.fillMaxWidth()) {
                Text("Enter manually", fontSize = 12.sp, color = PP.TextHi, maxLines = 1)
            }
            if (busy) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = PP.Accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Reading the EMI options…", fontSize = 12.sp, color = PP.TextLo)
                }
            }
            notes.forEach { Text(it, fontSize = 11.sp, color = Amber, modifier = Modifier.padding(top = 4.dp)) }
            Spacer(Modifier.height(8.dp))
            product.emiOptions.forEach { o ->
                val plan = advice?.emis?.firstOrNull { it.option.id == o.id }
                if (editingId == o.id) {
                    EmiEditor(o, price, isNew = false, onSave = { onSave(it); editingId = null },
                        onRemove = { onRemove(o.id); editingId = null }, onCancel = { editingId = null })
                } else {
                    EmiOptionRow(o, plan, onEdit = { editingId = o.id }, onRemove = { onRemove(o.id) })
                }
            }
            if (editingId == NEW) {
                EmiEditor(EmiOption(quotedForPrice = price), price, isNew = true, onSave = { onSave(it); editingId = null },
                    onRemove = {}, onCancel = { editingId = null })
            }
            if (product.emiOptions.isEmpty() && editingId == null && !busy) {
                Text("Upload a screenshot of the EMI plans or enter one manually. Results are checked with you before they are used.", fontSize = 11.sp, color = PP.TextLo)
            }
        } else if (product.emiAvailable == false) {
            Text("Treated as a cash purchase.", fontSize = 11.sp, color = PP.TextLo, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private const val NEW = "__new__"

@Composable
private fun ChoiceRow(selected: Boolean, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = PP.Accent, unselectedColor = PP.TextLo))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, color = PP.TextHi)
    }
}

@Composable
private fun EmiOptionRow(o: EmiOption, plan: EmiPlan?, onEdit: () -> Unit, onRemove: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).background(PP.Field, RoundedCornerShape(14.dp)).clickable(onClick = onEdit).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(o.lender.ifBlank { "EMI offer" }, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PP.TextHi, maxLines = 1)
            val tag = if (!o.confirmed) "Check & confirm" else plan?.let { suitabilityLabel(it.suitability) }
            val tagColor = if (!o.confirmed) Amber else plan?.let { suitabilityColor(it.suitability) } ?: PP.TextLo
            if (tag != null) Text(tag, fontSize = 11.sp, color = tagColor, fontWeight = FontWeight.Medium)
        }
        val terms = buildString {
            append(o.monthlyEmi?.let { Money.format(it) + "/month" } ?: "monthly EMI needed")
            append(" · ")
            append(o.tenureMonths?.let { "$it months" } ?: "tenure needed")
        }
        Text(terms, fontSize = 12.sp, color = PP.TextLo, modifier = Modifier.padding(top = 2.dp))
        val bits = listOfNotNull(
            if (o.noCostAdvertised) "Advertised as No Cost EMI" else null,
            if (o.source == EmiSource.SCREENSHOT) "From screenshot" else null,
            o.fees.takeIf { it.signum() > 0 }?.let { "Fees ${Money.format(it)}" },
            o.downPayment.takeIf { it.signum() > 0 }?.let { "Down payment ${Money.format(it)}" }
        )
        if (bits.isNotEmpty()) Text(bits.joinToString(" · "), fontSize = 11.sp, color = PP.TextLo, modifier = Modifier.padding(top = 2.dp))
        plan?.cost?.let { c ->
            val extra = c.extraVsCash
            Text(
                when {
                    extra.signum() > 0 -> "Costs ${Money.format(extra)} more than cash"
                    extra.signum() < 0 -> "Costs ${Money.format(-extra)} less than cash"
                    else -> "Same total as cash"
                } + " · total ${Money.format(c.totalCost)}",
                fontSize = 11.sp, color = PP.TextHi, modifier = Modifier.padding(top = 2.dp)
            )
        }
        plan?.issues?.filter { it.level == IssueLevel.WARNING }?.forEach { Text("! ${it.text}", fontSize = 11.sp, color = Amber, modifier = Modifier.padding(top = 2.dp)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onEdit) { Text(if (o.confirmed) "Edit" else "Review", color = PP.Accent, fontSize = 12.sp) }
            TextButton(onClick = onRemove) { Text("Remove", color = PP.Error, fontSize = 12.sp) }
        }
    }
}

private fun plain(v: BigDecimal?): String = v?.stripTrailingZeros()?.takeIf { it.signum() != 0 }?.toPlainString().orEmpty()

@Composable
private fun EmiEditor(
    option: EmiOption, price: BigDecimal?, isNew: Boolean,
    onSave: (EmiOption) -> Unit, onRemove: () -> Unit, onCancel: () -> Unit
) {
    var lender by remember(option.id) { mutableStateOf(option.lender) }
    var m by remember(option.id) { mutableStateOf(plain(option.monthlyEmi)) }
    var n by remember(option.id) { mutableStateOf(option.tenureMonths?.toString().orEmpty()) }
    var rate by remember(option.id) { mutableStateOf(plain(option.annualRatePct)) }
    var down by remember(option.id) { mutableStateOf(plain(option.downPayment)) }
    var fee by remember(option.id) { mutableStateOf(plain(option.processingFee)) }
    var other by remember(option.id) { mutableStateOf(plain(option.otherCharges)) }
    var total by remember(option.id) { mutableStateOf(plain(option.totalPayable)) }
    var cash by remember(option.id) { mutableStateOf(plain(option.cashback)) }
    var lost by remember(option.id) { mutableStateOf(plain(option.cashDiscountLost)) }
    var noCost by remember(option.id) { mutableStateOf(option.noCostAdvertised) }

    fun bad(s: String) = s.isNotBlank() && Money.parseDecimal(s) == null
    fun dec(s: String) = Money.parseDecimal(s)
    val anyBad = listOf(m, rate, down, fee, other, total, cash, lost).any { bad(it) } || (n.isNotBlank() && n.toIntOrNull() == null)
    val built = option.copy(
        lender = lender.trim(), monthlyEmi = dec(m), tenureMonths = n.toIntOrNull(), annualRatePct = dec(rate),
        downPayment = dec(down) ?: BigDecimal.ZERO, processingFee = dec(fee) ?: BigDecimal.ZERO, otherCharges = dec(other) ?: BigDecimal.ZERO,
        totalPayable = dec(total), cashback = dec(cash) ?: BigDecimal.ZERO, cashDiscountLost = dec(lost) ?: BigDecimal.ZERO,
        noCostAdvertised = noCost
    )
    val issues = EmiCalculator.validate(built, price)
    val canSave = !anyBad && issues.none { it.level == IssueLevel.MISSING || it.level == IssueLevel.ERROR }

    @Composable
    fun num(label: String, value: String, set: (String) -> Unit, modifier: Modifier, prefix: Boolean = true, digitsOnly: Boolean = false, hint: String? = null) {
        OutlinedTextField(
            value = value,
            onValueChange = { v -> set(v.filter { it.isDigit() || (!digitsOnly && it == '.') }.take(12)) },
            label = { Text(label, fontSize = 11.sp, maxLines = 1) }, singleLine = true, isError = bad(value),
            prefix = if (prefix) ({ Text("₹ ") }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = if (digitsOnly) KeyboardType.Number else KeyboardType.Decimal),
            colors = fieldColors(), shape = RoundedCornerShape(12.dp), modifier = modifier,
            supportingText = hint?.let { { Text(it, fontSize = 10.sp, maxLines = 2) } }
        )
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).background(PP.Field, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(if (isNew) "New EMI option" else if (!option.confirmed) "Check this EMI option" else "Edit EMI option",
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PP.TextHi)
        if (option.source == EmiSource.SCREENSHOT) {
            Text("Read from a screenshot - these are candidates, not verified terms. Fix anything that's wrong, then confirm.", fontSize = 11.sp, color = Amber, modifier = Modifier.padding(top = 2.dp))
            if (option.evidence.isNotBlank()) Text("Seen: ${option.evidence}", fontSize = 10.sp, color = PP.TextLo, maxLines = 3, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            lender, { lender = it.take(60) }, label = { Text("Bank / lender (optional)", fontSize = 11.sp) }, singleLine = true,
            colors = fieldColors(), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            num("Monthly EMI", m, { m = it }, Modifier.weight(1f))
            num("Tenure (months)", n, { n = it.take(3) }, Modifier.weight(1f), prefix = false, digitsOnly = true)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            num("Interest % a year", rate, { rate = it.take(6) }, Modifier.weight(1f), prefix = false)
            num("Down payment", down, { down = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            num("Processing fee", fee, { fee = it }, Modifier.weight(1f))
            num("Other charges", other, { other = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            num("Cashback / discount", cash, { cash = it }, Modifier.weight(1f), hint = "only with this EMI")
            num("Total payable (quoted)", total, { total = it }, Modifier.weight(1f), hint = "optional; checked, not added")
        }
        Spacer(Modifier.height(6.dp))
        num("Cash discount lost with EMI", lost, { lost = it }, Modifier.fillMaxWidth(), hint = "only if NOT already inside the EMI amounts")
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Advertised as No Cost EMI", Modifier.weight(1f), fontSize = 13.sp, color = PP.TextHi)
            Switch(noCost, { noCost = it }, colors = SwitchDefaults.colors(checkedTrackColor = PP.Accent, checkedThumbColor = PP.Ink))
        }
        if (anyBad) Text("Some amounts aren't valid numbers.", fontSize = 11.sp, color = PP.Error, modifier = Modifier.padding(top = 4.dp))
        issues.forEach {
            val color = when (it.level) { IssueLevel.ERROR -> PP.Error; IssueLevel.MISSING -> Amber; IssueLevel.WARNING -> Amber; IssueLevel.INFO -> PP.TextLo }
            Text((if (it.level == IssueLevel.INFO) "· " else "! ") + it.text, fontSize = 11.sp, color = color, modifier = Modifier.padding(top = 3.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!isNew) TextButton(onClick = onRemove) { Text("Remove", color = PP.Error) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("Cancel", color = PP.TextLo) }
            Button(
                onClick = { onSave(built) }, enabled = canSave,
                colors = ButtonDefaults.buttonColors(containerColor = PP.Accent, contentColor = PP.Ink)
            ) { Text(if (option.confirmed) "Save option" else "Confirm", fontWeight = FontWeight.SemiBold) }
        }
    }
}
