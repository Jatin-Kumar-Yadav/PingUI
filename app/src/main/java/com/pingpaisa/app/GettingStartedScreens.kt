@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pingpaisa.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.animation.core.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
internal fun OnboardingScreen(menu: () -> Unit, done: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf("Transactions, on autopilot","Know where every rupee goes","Private by design")
    val descriptions = listOf(
        "PingPaisa quietly finds your UPI spends and keeps everything beautifully organised.",
        "Smart AI categories turn payment noise into simple, useful money patterns.",
        "Your financial data stays encrypted and 100% on your device.")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.TopEnd).offset(x = 100.dp, y = (-90).dp).size(310.dp, 280.dp).rotate(22f)
            .background(Brush.linearGradient(listOf(Color(0xFFE1D9F0), Clay.Lavender)), ClayBlobShape))
        Box(Modifier.align(Alignment.BottomStart).offset(x = (-120).dp, y = 120.dp).size(240.dp, 250.dp)
            .background(Brush.linearGradient(listOf(Color(0xFFE2EADB), Clay.Sage)), ClayBlobShape))
        // Fixed, non-scrolling page: the illustration shrinks on short screens so everything fits.
        val art = (maxHeight - 430.dp).coerceIn(150.dp, 274.dp)
        Column(Modifier.fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 34.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MenuButton(menu); Brand()
                Spacer(Modifier.weight(1f))
                NativeText("Skip", 12.sp, Color(0xFF719487), FontWeight.ExtraBold,
                    Modifier.claySurface(CircleShape, Color(0xFFF5F1EC), ClayShadow.Soft)
                        .clickable(onClickLabel = "Skip onboarding", onClick = done)
                        .padding(horizontal = 16.dp, vertical = 9.dp))
            }
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = if (art < 274.dp) 16.dp else 34.dp, bottom = 20.dp)
                .size(art * (272f / 274f), art),
                contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawOval(Color(0xFF4E695F).copy(alpha = .2f), Offset(16.dp.toPx(),16.dp.toPx()),
                        androidx.compose.ui.geometry.Size(size.width-32.dp.toPx(),size.height-32.dp.toPx()), style = Stroke(2.dp.toPx()))
                    drawOval(Color(0xFF4E695F).copy(alpha = .2f), Offset(1.dp.toPx(),43.dp.toPx()),
                        androidx.compose.ui.geometry.Size(size.width-2.dp.toPx(),size.height-86.dp.toPx()), style = Stroke(2.dp.toPx()))
                }
                val drift by rememberInfiniteTransition(label = "Illustration float").animateFloat(0f, 1f,
                    infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "Float")
                Box(Modifier.graphicsLayer { translationY = -6.dp.toPx() * drift; rotationZ = 3f * drift }
                    .size(142.dp).rotate(-7f).claySurface(RoundedCornerShape(42.dp), shadow = ClayShadow.Raised,
                    gradient = Color(0xFFC7D9C3) to Color(0xFF93B78C), inset = true), contentAlignment = Alignment.Center) {
                    NativeIcon(listOf(R.drawable.ic_pingpaisa_wallet,R.drawable.ic_pingpaisa_brain,R.drawable.ic_pingpaisa_shield)[step],56.dp,Color(0xFF55756A))
                }
                Box(Modifier.align(Alignment.TopStart).offset(x = 5.dp, y = 42.dp).size(58.dp)
                    .claySurface(CircleShape,Color(0xFFF3D4B8),ClayShadow.Raised), contentAlignment = Alignment.Center) { NativeText("₹",24.sp,Color(0xFF9C7254),FontWeight.ExtraBold) }
                Box(Modifier.align(Alignment.BottomEnd).offset(x = (-8).dp, y = (-37).dp).size(45.dp)
                    .claySurface(CircleShape,Color(0xFFD5CFEE),ClayShadow.Raised), contentAlignment = Alignment.Center) { NativeIcon(R.drawable.ic_pingpaisa_sparkle,17.dp,Color(0xFF75699D)) }
            }
            Column(Modifier.heightIn(min = 174.dp)) {
                NativeText("0${step + 1} / 03",10.sp,Clay.Teal,FontWeight.ExtraBold,tracking = 1.8.sp)
                NativeText(titles[step],29.sp,tracking = (-1.595).sp,lineHeight = 32.48.sp,
                    modifier = Modifier.widthIn(max = 310.dp).padding(vertical = 10.dp))
                NativeText(descriptions[step],12.sp,Color(0xFF78807D),lineHeight = 20.4.sp)
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.padding(bottom = 18.dp),horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { index -> Box(Modifier.width(if (index == step) 24.dp else 7.dp).height(7.dp)
                    .background(if (index == step) Clay.Teal else Color(0xFFD8D5D0),CircleShape).clickable { step = index }) }
            }
            PrimaryAction(if (step == 2) "Get started   →" else "Next   →",Modifier.heightIn(min = 57.dp)) { if (step == 2) done() else step++ }
        }
    }
}

private const val MAX_TRIES_BEFORE_WAIT = 5
private const val BASE_WAIT_MS = 30_000L

/**
 * Login. Same look as the original PIN screen (green blob, lock badge, "WELCOME
 * BACK"), but the keypad is replaced by a password box, because the account
 * password can contain letters. [onLogin] checks the password and reports a
 * [LoginResult]; [done] runs after a correct password.
 *
 * Guessing protection: after every 5 wrong tries the Unlock button waits
 * 30 s, then 60 s, 90 s… The counter lives in saved UI state, so it survives
 * rotation but resets if the app is fully closed.
 */
@Composable
internal fun LockScreen(
    menu: () -> Unit,
    name: String? = null,
    onLogin: (password: String, result: (LoginResult) -> Unit) -> Unit = { _, result -> result(LoginResult.Success) },
    done: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showForgot by remember { mutableStateOf(false) }
    var wrongTries by rememberSaveable { mutableIntStateOf(0) }
    var waitUntil by rememberSaveable { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(waitUntil) {
        while (System.currentTimeMillis() < waitUntil) { now = System.currentTimeMillis(); delay(250) }
        now = System.currentTimeMillis()
    }
    val secondsLeft = ((waitUntil - now + 999) / 1000).coerceAtLeast(0)
    val focus = LocalFocusManager.current

    fun submit() {
        if (checking || secondsLeft > 0) return
        if (password.isEmpty()) { error = "Enter your password"; return }
        focus.clearFocus()
        checking = true
        error = null
        onLogin(password) { result ->
            checking = false
            password = ""
            when (result) {
                LoginResult.Success -> { wrongTries = 0; done() }
                LoginResult.NoAccount -> error = "There's no account on this phone yet."
                LoginResult.WrongPassword -> {
                    wrongTries++
                    val left = MAX_TRIES_BEFORE_WAIT - wrongTries % MAX_TRIES_BEFORE_WAIT
                    if (wrongTries % MAX_TRIES_BEFORE_WAIT == 0) {
                        waitUntil = System.currentTimeMillis() + BASE_WAIT_MS * (wrongTries / MAX_TRIES_BEFORE_WAIT)
                        now = System.currentTimeMillis()
                        error = "Too many wrong tries. Please wait before trying again."
                    } else error = "Wrong password. $left ${if (left == 1) "try" else "tries"} left before a short wait."
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.offset(x = (-120).dp, y = (-165).dp).size(480.dp,330.dp)
            .background(Brush.linearGradient(listOf(Color(0xFFDCE8D8),Color(0xFFABC5A5))),ClayBlobShape))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp,end = 20.dp,top = 10.dp,bottom = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            // Keeps its height even when the menu button is hidden, so the layout doesn't jump.
            Box(Modifier.align(Alignment.Start).heightIn(min = 40.dp)) { MenuButton(menu) }
            Box(Modifier.padding(top = 4.dp,bottom = 22.dp).size(69.dp)
                .claySurface(RoundedCornerShape(24.dp),Color(0xFFD9E6D5),ClayShadow.Raised,inset = true),
                contentAlignment = Alignment.Center) { NativeIcon(R.drawable.ic_pingpaisa_lock,30.dp,Color(0xFF5F8176)) }
            NativeText("WELCOME BACK",9.sp,Color(0xFF849089),FontWeight.ExtraBold,tracking = 1.44.sp)
            NativeText(if (name != null) "Hi, $name" else "Enter your password",27.sp,tracking = (-1.35).sp,
                modifier = Modifier.padding(vertical = 7.dp), maxLines = 1)
            NativeText("Enter your password to unlock PingPaisa",12.sp,Color(0xFF8C918F))

            Spacer(Modifier.height(30.dp))
            ClayTextField("Password", password, { password = it; error = null },
                modifier = Modifier.widthIn(max = 420.dp), placeholder = "Your password", error = error,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                trailing = {
                    Box(Modifier.size(32.dp).clickable { showPassword = !showPassword }, contentAlignment = Alignment.Center) {
                        NativeIcon(R.drawable.ic_pingpaisa_eye, 20.dp,
                            if (showPassword) Clay.Teal else Color(0xFFAFB1AE),
                            if (showPassword) "Hide password" else "Show password")
                    }
                })
            Spacer(Modifier.height(20.dp))
            PrimaryAction(
                when { checking -> "Checking…"; secondsLeft > 0 -> "Try again in ${secondsLeft}s"; else -> "Unlock   →" },
                Modifier.widthIn(max = 420.dp).heightIn(min = 57.dp),
                enabled = !checking && secondsLeft == 0L
            ) { submit() }
            NativeText("Forgot password?",11.sp,Color(0xFF719487),FontWeight.ExtraBold,
                Modifier.padding(top = 24.dp).clickable { showForgot = true }.padding(8.dp))
        }
    }

    if (showForgot) AlertDialog(
        onDismissRequest = { showForgot = false },
        title = { NativeText("Forgot your password?", 18.sp) },
        text = {
            NativeText("PingPaisa never stores your password, only a scrambled version of it, so it can't be recovered. " +
                "To start over, clear PingPaisa's data in Android settings (App info → Storage → Clear data). " +
                "This deletes your account and everything saved in the app on this phone.", 12.sp, lineHeight = 18.sp)
        },
        confirmButton = { TextButton({ showForgot = false }) { NativeText("Got it", 12.sp) } }
    )
}

@Composable
internal fun Brand(large: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(Modifier.size(if (large) 42.dp else 29.dp).claySurface(RoundedCornerShape(if (large) 14.dp else 10.dp),
            Clay.Teal,ClayShadow.Badge,inset = true),contentAlignment = Alignment.Center) { NativeText("P",if (large) 22.sp else 16.sp,Color.White,FontWeight.ExtraBold) }
        NativeText("PingPaisa",if (large) 22.sp else 16.sp,weight = FontWeight.ExtraBold,tracking = (-.48).sp)
    }
}

@Composable
internal fun StyleScreen(menu: () -> Unit) {
    ScreenScroll {
        MenuButton(menu)
        Spacer(Modifier.height(10.dp))
        StyleLabel("PINGPAISA DESIGN SYSTEM")
        NativeText("Soft money,\nclear thinking.",39.sp,lineHeight = 39.sp,tracking = (-2.535).sp,modifier = Modifier.padding(vertical = 18.dp))
        NativeText("A tactile visual language that makes personal finance feel calm, private and encouraging.",11.sp,Color(0xFF7A827E),lineHeight = 18.7.sp)
        Spacer(Modifier.height(40.dp)); Brand(large = true)
        Spacer(Modifier.height(34.dp)); StyleLabel("01 • COLOUR PALETTE")
        Spacer(Modifier.height(18.dp))
        val swatches = listOf(Triple("Sage","#A8C4A2",Clay.Sage),Triple("Dusty teal","#7FA99B",Clay.Teal),
            Triple("Blush","#F4C6C6",Clay.Pink),Triple("Peach","#F2D3B3",Clay.Peach),
            Triple("AI lavender","#C8BFE7",Clay.Lavender),Triple("Warm cream","#FAF7F2",Clay.Cream))
        NativeGrid(swatches) { (name,hex,color),cell ->
            Column(cell.height(96.dp).claySurface(RoundedCornerShape(24.dp),color).padding(13.dp),verticalArrangement = Arrangement.Bottom) {
                NativeText(name,10.sp,weight = FontWeight.Bold); NativeText(hex,8.sp,Clay.Ink.copy(alpha = .65f),modifier = Modifier.padding(top = 3.dp))
            }
        }
        Spacer(Modifier.height(34.dp)); StyleLabel("02 • TYPOGRAPHY"); Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NativeText("₹42,680",39.sp,tracking = (-2.34).sp)
            NativeText("Money made mindful",26.sp,tracking = (-1.04).sp)
            NativeText("Card heading / 18px",16.sp)
            NativeText("Body text is calm, warm and easy to scan.",12.sp,Color(0xFF8B918E))
            NativeText("LABELS • 11PX MEDIUM",9.sp,Color(0xFF8B918E),tracking = 1.26.sp)
        }
        Spacer(Modifier.height(34.dp)); StyleLabel("03 • TACTILE COMPONENTS"); Spacer(Modifier.height(18.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp),verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PrimaryAction("Default",Modifier.width(104.dp)) {}
            PrimaryAction("Pressed",Modifier.width(104.dp).offset(y = 2.dp),pressed = true) {}
            PrimaryAction("Disabled",Modifier.width(104.dp),enabled = false) {}
            Row(Modifier.width(220.dp).claySurface(RoundedCornerShape(22.dp),shadow = ClayShadow.Raised).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ClayBadge(R.drawable.ic_pingpaisa_sparkle,Tone.Lavender)
                Column { NativeText("Soft card",10.sp,weight = FontWeight.Bold); NativeText("Raised, rounded, calm",7.sp,Color(0xFF999999),modifier = Modifier.padding(top = 2.dp)) }
            }
        }
        Spacer(Modifier.height(34.dp)); StyleLabel("04 • ICON LANGUAGE"); Spacer(Modifier.height(18.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp),verticalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(R.drawable.ic_pingpaisa_wallet to Tone.Sage,R.drawable.ic_pingpaisa_sparkle to Tone.Lavender,
                R.drawable.ic_pingpaisa_food to Tone.Peach,R.drawable.ic_pingpaisa_bag to Tone.Pink,
                R.drawable.ic_pingpaisa_shield to Tone.Teal,R.drawable.ic_pingpaisa_chart to Tone.Cream).forEach { (icon,tone) -> ClayBadge(icon,tone,size = 58.dp) }
        }
        NativeText("Rounded, 1.9px line icons sit inside soft coloured clay badges.",11.sp,Color(0xFF8B918E),modifier = Modifier.padding(top = 18.dp))
    }
}

@Composable
private fun StyleLabel(text: String) { NativeText(text,9.sp,Color(0xFF849089),FontWeight.ExtraBold,tracking = 1.44.sp) }
