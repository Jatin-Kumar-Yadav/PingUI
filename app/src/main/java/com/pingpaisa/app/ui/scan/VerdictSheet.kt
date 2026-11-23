package com.pingpaisa.app.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingpaisa.app.data.Product
import com.pingpaisa.app.finance.Advice
import com.pingpaisa.app.finance.Assessment
import com.pingpaisa.app.ui.theme.PP
import com.pingpaisa.app.util.Money

/**
 * "Why this verdict" - the plain-language explanation. It reuses the verdict card's look (same gradient, same label style)
 * and the app's dark sheet cards; the working-out (formulas, symbols) is deliberately not shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerdictDetailsSheet(
    a: Assessment, products: List<Product>, verdict: VerdictUi, onDismiss: () -> Unit,
    advices: Map<String, Advice> = emptyMap(), combinedWarning: String? = null
) {
    val e = VerdictExplainer.explain(a, products, advices, combinedWarning)
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = PP.Surface, contentColor = PP.TextHi,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 28.dp)) {
            // headline: same card language as the verdict on the scanner screen
            val (c1, c2) = gradientFor(verdict.kind)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(c1, c2), Offset.Zero, Offset(900f, 700f)))
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Text("WHY THIS VERDICT", color = PP.VerdictLabel, fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(verdict.title, color = PP.Ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(verdict.body, color = PP.InkSoft, fontSize = 13.sp, lineHeight = 18.sp)
            }

            Heading("What this is based on")
            Card {
                e.points.forEachIndexed { i, p ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    PointRow(p)
                }
            }

            if (e.items.isNotEmpty()) {
                Heading(if (e.items.size == 1) "Your item" else "Your items")
                e.items.forEach { it ->
                    Card(Modifier.padding(bottom = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(it.name, Modifier.weight(1f).padding(end = 8.dp), maxLines = 1, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(it.price?.let { p -> Money.format(p) } ?: "-", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        Pill(it.label, it.kind)
                        Spacer(Modifier.height(6.dp))
                        Text(it.sentence, fontSize = 12.sp, lineHeight = 17.sp, color = PP.TextLo)
                    }
                }
            }

            if (e.payments.isNotEmpty()) {
                Heading("How to pay")
                e.payments.forEach { pay ->
                    Card(Modifier.padding(bottom = 8.dp)) {
                        Text(pay.name.take(44), fontSize = 12.sp, color = PP.TextLo, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        Text(pay.headline, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = markColor(pay.kind))
                        pay.lines.forEach { Text(it, fontSize = 12.sp, lineHeight = 17.sp, color = PP.TextHi, modifier = Modifier.padding(top = 5.dp)) }
                        if (pay.missing.isNotEmpty()) {
                            Text("Still to check", fontSize = 11.sp, color = Amber, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
                            pay.missing.forEach { Text(it, fontSize = 11.sp, color = Amber, modifier = Modifier.padding(top = 2.dp)) }
                        }
                    }
                }
                e.warning?.let { Text(it, fontSize = 12.sp, color = Amber, lineHeight = 17.sp, modifier = Modifier.padding(bottom = 8.dp)) }
            }

            Spacer(Modifier.height(10.dp))
            Text(e.footnote, fontSize = 11.sp, lineHeight = 15.sp, color = PP.TextLo)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PP.Accent, contentColor = PP.Ink)
            ) { Text("Got it", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Spacer(Modifier.height(18.dp))
    Text(text.uppercase(), fontSize = 10.sp, letterSpacing = 1.4.sp, color = PP.TextLo, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PP.Field).padding(14.dp)) { content() }
}

@Composable
private fun PointRow(p: VerdictBullet) {
    val color = when (p.mark) { Mark.OK -> PP.Accent; Mark.WARN -> Amber; Mark.BAD -> PP.Error }
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 1.dp).size(20.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text(p.mark.glyph, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(p.text, fontSize = 13.sp, lineHeight = 18.sp, color = PP.TextHi)
    }
}

@Composable
private fun Pill(text: String, kind: VerdictKind) {
    val c = markColor(kind)
    Text(
        text, color = c, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

private fun markColor(kind: VerdictKind): Color = when (kind) {
    VerdictKind.GREEN -> PP.Accent
    VerdictKind.AMBER -> Amber
    VerdictKind.RED -> PP.Error
    VerdictKind.NEUTRAL -> PP.TextLo
}
