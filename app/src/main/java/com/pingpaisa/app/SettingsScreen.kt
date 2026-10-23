package com.pingpaisa.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SettingsScreen(menu: () -> Unit, navigate: (Screen) -> Unit) {
    var locked by rememberSaveable { mutableStateOf(true) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    ScreenScroll {
        NativeHeader("Settings", menu = menu)
        Spacer(Modifier.height(13.dp))
        Row(Modifier.fillMaxWidth().claySurface(RoundedCornerShape(24.dp), shadow = ClayShadow.Raised,
            gradient = Color(0xFFDBE8D7) to Color(0xFFC3D7BE)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(48.dp).claySurface(RoundedCornerShape(17.dp), Color(0xFF78998D), ClayShadow.Avatar),
                contentAlignment = Alignment.Center) { NativeText("AM", 12.sp, Color.White, FontWeight.ExtraBold) }
            Column(Modifier.weight(1f)) {
                NativeText("Aanya Mehta", 11.sp)
                NativeText("aanya@pingpaisa.app", 7.sp, Color(0xFF718078), modifier = Modifier.padding(vertical = 3.dp))
            }
            Box(Modifier.size(31.dp).background(Color.White.copy(alpha = .5f), CircleShape)
                .clickable { dialog = "Your profile" }, contentAlignment = Alignment.Center) { NativeIcon(R.drawable.ic_pingpaisa_edit, 17.dp) }
        }
        SettingsGroup("ORGANISE", 18.dp) {
            SettingsRow("Categories","12 custom categories", R.drawable.ic_pingpaisa_bag, Tone.Peach) { dialog = "Categories" }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFECE8E2)))
            SettingsRow("Merchants","48 remembered merchants", R.drawable.ic_pingpaisa_wallet, Tone.Sage) { dialog = "Merchants" }
        }
        SettingsGroup("SECURITY & DATA", 14.dp) {
            SettingsRow("App lock","PIN & biometric", R.drawable.ic_pingpaisa_lock, Tone.Lavender,
                toggle = locked, toggleChanged = { locked = !locked }) { navigate(Screen.Lock) }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFECE8E2)))
            SettingsRow("Export your data","CSV or PDF", R.drawable.ic_pingpaisa_download, Tone.Pink) { dialog = "Export your data" }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().heightIn(min = 110.5.dp).background(Color(0xFFE5EEE2), RoundedCornerShape(23.dp)).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ClayBadge(R.drawable.ic_pingpaisa_shield)
            Column(Modifier.weight(1f)) {
                Box(Modifier.height(24.dp), contentAlignment = Alignment.CenterStart) {
                    NativeText("PRIVACY PROMISE", 6.sp, tracking = .6.sp, modifier = Modifier.offset(y = 3.dp))
                }
                NativeText("100% on-device", 12.sp, modifier = Modifier.padding(vertical = 3.dp))
                NativeText("Your transaction data never leaves this phone. Not even we can see it.", 7.sp, Color(0xFF718078))
                NativeText("Read our privacy story →", 7.sp, Color(0xFF608275), FontWeight.ExtraBold,
                    Modifier.clickable { dialog = "Privacy promise" }.padding(top = 7.dp))
            }
        }
        NativeText("PingPaisa v1.0 • Made with care in India", 6.sp, Color(0xFFAAAAAA),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp))
    }
    dialog?.let { title ->
        AlertDialog(onDismissRequest = { dialog = null }, title = { NativeText(title, 18.sp) },
            text = { NativeText("This is a design prototype using sample data. No account or banking service is connected.", 12.sp) },
            confirmButton = { TextButton({ dialog = null }) { NativeText("Got it", 12.sp) } })
    }
}

@Composable
private fun ColumnScope.SettingsGroup(label: String, top: androidx.compose.ui.unit.Dp, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(top))
    Box(Modifier.height(24.dp), contentAlignment = Alignment.CenterStart) {
        NativeText(label, 9.sp, Color(0xFF849089), FontWeight.ExtraBold, tracking = 1.44.sp)
    }
    Spacer(Modifier.height(7.dp))
    Column(Modifier.fillMaxWidth().claySurface(RoundedCornerShape(22.dp)), content = content)
}

@Composable
private fun SettingsRow(
    title: String, subtitle: String, icon: Int, tone: Tone,
    toggle: Boolean? = null, toggleChanged: () -> Unit = {}, onClick: () -> Unit
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        ClayBadge(icon, tone, small = true, modifier = Modifier.weight(1f))
        Column(Modifier.weight(1f)) {
            NativeText(title, 9.sp, weight = FontWeight.Bold)
            NativeText(subtitle, 7.sp, Color(0xFF999999), modifier = Modifier.padding(top = 2.dp))
        }
        if (toggle == null) NativeIcon(R.drawable.ic_pingpaisa_chevron, 16.dp)
        else Box(Modifier.width(34.dp).height(20.dp).background(if (toggle) Color(0xFF88AA9D) else Color(0xFFDDD9D3), CircleShape)
            .clickable(onClickLabel = "Toggle app lock preview", onClick = toggleChanged).padding(3.dp)) {
            val knob by androidx.compose.animation.core.animateFloatAsState(if (toggle) 1f else -1f,
                androidx.compose.animation.core.spring(dampingRatio = .55f, stiffness = 380f), label = "Toggle knob")
            Box(Modifier.size(14.dp).align(BiasAlignment(knob, 0f)).background(Color.White, CircleShape))
        }
    }
}
