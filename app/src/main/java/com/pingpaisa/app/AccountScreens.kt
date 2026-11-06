package com.pingpaisa.app

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pingpaisa.app.data.PaymentNotificationListener
import com.pingpaisa.app.security.SignUpErrors
import com.pingpaisa.app.security.SignUpValidation

private val ErrorInk = Color(0xFFB0675C)

/**
 * A text box in the app's inset-clay style (same look as the Note box on the
 * Transaction detail screen), with a small label, optional ₹-style [prefix],
 * an optional [trailing] control (used for the show/hide-password eye) and an
 * [error] line that appears under the box.
 */
@Composable
internal fun ClayTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    prefix: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    error: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 58.dp)
                .claySurface(RoundedCornerShape(17.dp), Color(0xFFF7F4EF), ClayShadow.None, inset = true)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                NativeText(label, 8.sp, if (error != null) ErrorInk else Color(0xFF8C918F), FontWeight.Bold)
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (prefix != null) NativeText(prefix, 14.sp, Color(0xFF859089), modifier = Modifier.padding(end = 5.dp))
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = PingPaisaFont, fontSize = 14.sp, color = Clay.Ink),
                        cursorBrush = SolidColor(Clay.Teal),
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        visualTransformation = visualTransformation,
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (value.isEmpty() && placeholder.isNotEmpty())
                                    NativeText(placeholder, 14.sp, Color(0xFFB4B7B4))
                                inner()
                            }
                        }
                    )
                }
            }
            if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
        }
        if (error != null) NativeText(error, 8.sp, ErrorInk, modifier = Modifier.padding(start = 6.dp, top = 5.dp))
    }
}

/**
 * First-run account creation: name, monthly salary, password and confirmation.
 * Errors stay hidden until the first tap on "Create account", then update as the
 * user fixes each field. [onSubmit] is called only when every rule in
 * [SignUpValidation] passes. It must call `done(true)` once the account is saved
 * or `done(false)` if it could not be (an account already exists).
 */
@Composable
internal fun SignUpScreen(
    menu: () -> Unit,
    onSubmit: (name: String, salary: Double, password: String, done: (created: Boolean) -> Unit) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var salary by rememberSaveable { mutableStateOf("") }
    // Passwords use plain remember on purpose: rememberSaveable would copy them into the saved-state bundle.
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }       // blocks a second tap while hashing/saving
    var alreadyExists by remember { mutableStateOf(false) }
    val errors = if (submitted) SignUpValidation.validate(name, salary, password, confirm) else SignUpErrors()
    val focus = LocalFocusManager.current
    val next = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) })

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.offset(x = (-120).dp, y = (-185).dp).size(480.dp, 330.dp)
            .background(Brush.linearGradient(listOf(Color(0xFFDCE8D8), Color(0xFFABC5A5))), ClayBlobShape))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 34.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MenuButton(menu); Brand()
            }
            Box(Modifier.padding(top = 20.dp, bottom = 18.dp).size(64.dp)
                .claySurface(RoundedCornerShape(22.dp), Color(0xFFD9E6D5), ClayShadow.Raised, inset = true),
                contentAlignment = Alignment.Center) {
                NativeIcon(R.drawable.ic_pingpaisa_user, 28.dp, Color(0xFF5F8176))
            }
            NativeText("CREATE ACCOUNT", 9.sp, Color(0xFF849089), FontWeight.ExtraBold, tracking = 1.44.sp)
            NativeText("Let's set you up", 27.sp, tracking = (-1.35).sp, modifier = Modifier.padding(vertical = 7.dp))
            NativeText("Your account stays on this phone. Your salary helps PingPaisa work out your health score and how much you can safely borrow.",
                11.sp, Color(0xFF8C918F), lineHeight = 17.sp)

            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ClayTextField("Your name", name, { name = it }, placeholder = "e.g. Aanya Mehta", error = errors.name,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    keyboardActions = next)
                ClayTextField("Monthly salary", salary, { if (it.length <= 9 && it.all(Char::isDigit)) salary = it },
                    placeholder = "e.g. 40000", prefix = "₹", error = errors.salary,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = next)
                ClayTextField("Password", password, { password = it }, placeholder = "At least 6 characters",
                    error = errors.password,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    keyboardActions = next,
                    trailing = {
                        Box(Modifier.size(32.dp).clickable { showPassword = !showPassword },
                            contentAlignment = Alignment.Center) {
                            NativeIcon(R.drawable.ic_pingpaisa_eye, 20.dp,
                                if (showPassword) Clay.Teal else Color(0xFFAFB1AE),
                                if (showPassword) "Hide password" else "Show password")
                        }
                    })
                ClayTextField("Confirm password", confirm, { confirm = it }, placeholder = "Type it again",
                    error = errors.confirm,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
            }

            Spacer(Modifier.height(24.dp))
            PrimaryAction(if (saving) "Creating…" else "Create account   →", Modifier.heightIn(min = 57.dp),
                enabled = !saving) {
                focus.clearFocus()
                submitted = true
                if (SignUpValidation.validate(name, salary, password, confirm).isValid) {
                    saving = true
                    alreadyExists = false
                    onSubmit(name.trim(), salary.toDouble(), password) { created ->
                        saving = false
                        alreadyExists = !created
                    }
                }
            }
            if (alreadyExists) NativeText("An account already exists on this phone, so a new one wasn't created.",
                9.sp, ErrorInk, lineHeight = 13.sp, modifier = Modifier.padding(top = 12.dp).align(Alignment.CenterHorizontally))
            NativeText("Your password is never stored, only a scrambled version of it. It can't be recovered if you forget it.",
                8.sp, Color(0xFFA0A4A1), lineHeight = 12.sp,
                modifier = Modifier.padding(top = 16.dp).align(Alignment.CenterHorizontally))
        }
    }
}

/** True when the user has switched on notification access for PingPaisa in Android settings. */
internal fun isNotificationAccessEnabled(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

/**
 * Opens Android's notification-access settings. On Android 11+ it jumps straight
 * to PingPaisa's own switch; on older versions, or if that page is missing on the
 * phone's brand of Android, it opens the general list instead.
 */
internal fun openNotificationAccessSettings(context: Context) {
    val general = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val listener = ComponentName(context, PaymentNotificationListener::class.java)
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener.flattenToString())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } else context.startActivity(general)
    } catch (_: ActivityNotFoundException) {
        try { context.startActivity(general) } catch (_: ActivityNotFoundException) { /* nothing more we can open */ }
    }
}

/**
 * Asks the user to switch on notification access so bank payment alerts can be
 * added to their transactions automatically. Skipping is allowed: the app works
 * without it (manual entry), and Settings gets a row to come back to this.
 * The state is re-read every time the screen comes back to the foreground, so it
 * updates by itself when the user returns from Android's settings page.
 * [onContinue] is used by both "Skip for now" and, once access is on, "Continue".
 */
@Composable
internal fun NotificationAccessScreen(menu: () -> Unit, onContinue: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(isNotificationAccessEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { enabled = isNotificationAccessEnabled(context) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.offset(x = (-120).dp, y = (-185).dp).size(480.dp, 330.dp)
            .background(Brush.linearGradient(listOf(Color(0xFFDCE8D8), Color(0xFFABC5A5))), ClayBlobShape))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 34.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MenuButton(menu); Brand()
            }
            Box(Modifier.padding(top = 20.dp, bottom = 18.dp).size(64.dp)
                .claySurface(RoundedCornerShape(22.dp), Color(0xFFD9E6D5), ClayShadow.Raised, inset = true),
                contentAlignment = Alignment.Center) {
                NativeIcon(R.drawable.ic_pingpaisa_chat, 28.dp, Color(0xFF5F8176))
            }
            NativeText("AUTO-CAPTURE", 9.sp, Color(0xFF849089), FontWeight.ExtraBold, tracking = 1.44.sp)
            NativeText("Track payments automatically", 27.sp, tracking = (-1.35).sp, lineHeight = 30.sp,
                modifier = Modifier.padding(vertical = 7.dp))
            NativeText("Allow notification access and PingPaisa adds your UPI payments the moment your bank's message arrives, with no typing.",
                11.sp, Color(0xFF8C918F), lineHeight = 17.sp)

            Spacer(Modifier.height(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AccessPoint(R.drawable.ic_pingpaisa_scan, Tone.Sage, "Only payment messages",
                    "Android gives apps access to all notifications, but PingPaisa only looks at debit messages from Google Messages and ignores everything else.")
                AccessPoint(R.drawable.ic_pingpaisa_shield, Tone.Lavender, "Stays on your phone",
                    "Messages are read on this device. Nothing is uploaded or shared.")
                AccessPoint(R.drawable.ic_pingpaisa_check, Tone.Peach, "You stay in control",
                    "Turn it off any time in Android settings. You can also add expenses by hand.")
            }

            Spacer(Modifier.height(24.dp))
            if (enabled) {
                NativeText("✓  Notification access is on", 10.sp, Color(0xFF5D806C), FontWeight.ExtraBold,
                    Modifier.align(Alignment.CenterHorizontally).padding(bottom = 14.dp)
                        .background(Color(0xFFE3EDE0), CircleShape).padding(horizontal = 14.dp, vertical = 8.dp))
                PrimaryAction("Continue   →", Modifier.heightIn(min = 57.dp), onClick = onContinue)
            } else {
                PrimaryAction("Grant access   →", Modifier.heightIn(min = 57.dp)) { openNotificationAccessSettings(context) }
                NativeText("Skip for now", 11.sp, Color(0xFF719487), FontWeight.ExtraBold,
                    Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onContinue).padding(top = 20.dp, bottom = 8.dp, start = 16.dp, end = 16.dp))
            }
        }
    }
}

@Composable
private fun AccessPoint(icon: Int, tone: Tone, title: String, text: String) {
    Row(Modifier.fillMaxWidth().claySurface(RoundedCornerShape(20.dp), Color(0xFFFBF9F6)).padding(12.dp),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        ClayBadge(icon, tone)
        Column(Modifier.weight(1f)) {
            NativeText(title, 10.sp, weight = FontWeight.Bold)
            NativeText(text, 8.sp, Color(0xFF7E8581), lineHeight = 12.5.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}
