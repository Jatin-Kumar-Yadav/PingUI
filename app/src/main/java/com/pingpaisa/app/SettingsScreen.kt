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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.pingpaisa.app.security.PasswordChangeErrors
import com.pingpaisa.app.security.SignUpErrors
import com.pingpaisa.app.security.SignUpValidation

/**
 * [account] is the signed-in user; null shows the design sample (previews and screenshot tests).
 * With an account the profile card is real, ✏️ edits the name and salary, and "Change password" appears.
 */
@Composable
internal fun SettingsScreen(
    menu: () -> Unit, navigate: (Screen) -> Unit,
    account: SettingsAccount? = null,
    onSaveProfile: (name: String, salary: Double) -> Unit = { _, _ -> },
    onChangePassword: (current: String, new: String, result: (ChangePasswordResult) -> Unit) -> Unit = { _, _, _ -> },
    captureEnabled: Boolean? = null,   // notification access; null in the design sample (no row)
    onOpenCapture: () -> Unit = {},
    lockEnabled: Boolean? = null,      // the account's app lock; null in the design sample (switch is only a preview)
    onEnableLock: () -> Unit = {},
    onDisableLock: (password: String, result: (ok: Boolean) -> Unit) -> Unit = { _, _ -> },
    merchants: List<MerchantRow>? = null,      // null in the design sample
    categories: List<CategoryRow>? = null,
    categoryChoices: List<String> = emptyList(),
    onRecategorizeMerchant: (key: String, category: String) -> Unit = { _, _ -> },
    onForgetMerchant: (key: String) -> Unit = {},
    onRenameCategory: (old: String, new: String) -> Unit = { _, _ -> },
    onRemoveCategory: (name: String) -> Unit = {},
    transactionCount: Int? = null,             // null in the design sample
    onExport: () -> Unit = {},
    onLogOut: (() -> Unit)? = null,            // only while the app lock is on
    onCheckPassword: ((password: String, result: (ok: Boolean) -> Unit) -> Unit)? = null,   // null: no Account group (sample)
    onReset: () -> Unit = {}
) {
    var locked by rememberSaveable { mutableStateOf(true) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var editingProfile by rememberSaveable { mutableStateOf(false) }
    var changingPassword by rememberSaveable { mutableStateOf(false) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmingLockOff by rememberSaveable { mutableStateOf(false) }
    var managingMerchants by rememberSaveable { mutableStateOf(false) }
    var managingCategories by rememberSaveable { mutableStateOf(false) }
    var resetting by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    // Real app: the switch shows the saved setting; off needs the password. Sample: the design's preview switch.
    val lockToggle: () -> Unit = {
        when (lockEnabled) {
            null -> locked = !locked
            true -> confirmingLockOff = true
            false -> { onEnableLock(); notice = "App lock is on. PingPaisa will ask for your password when it opens." }
        }
    }
    ScreenScroll {
        NativeHeader("Settings", menu = menu)
        Spacer(Modifier.height(13.dp))
        Row(Modifier.fillMaxWidth().claySurface(RoundedCornerShape(24.dp), shadow = ClayShadow.Raised,
            gradient = Color(0xFFDBE8D7) to Color(0xFFC3D7BE)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(48.dp).claySurface(RoundedCornerShape(17.dp), Color(0xFF78998D), ClayShadow.Avatar),
                contentAlignment = Alignment.Center) { NativeText(account?.initials ?: "AM", 12.sp, Color.White, FontWeight.ExtraBold) }
            Column(Modifier.weight(1f)) {
                NativeText(account?.name ?: "Aanya Mehta", 11.sp, maxLines = 1)
                NativeText(account?.salaryText ?: "aanya@pingpaisa.app", 7.sp, Color(0xFF718078), modifier = Modifier.padding(vertical = 3.dp))
            }
            Box(Modifier.size(31.dp).background(Color.White.copy(alpha = .5f), CircleShape)
                .clickable(onClickLabel = "Edit profile") { if (account != null) editingProfile = true else navigate(Screen.FinancialProfile) },
                contentAlignment = Alignment.Center) { NativeIcon(R.drawable.ic_pingpaisa_edit, 17.dp) }
        }
        SettingsGroup("SCANNER & FINANCES", 18.dp) {
            SettingsRow("Financial profile", "Income, savings, expenses & purchase policy", R.drawable.ic_pingpaisa_wallet, Tone.Sage) {
                navigate(Screen.FinancialProfile)
            }
        }
        SettingsGroup("ORGANISE", 18.dp) {
            SettingsRow("Categories", categories?.let(ManageUi::categoriesSubtitle) ?: "12 custom categories", R.drawable.ic_pingpaisa_bag, Tone.Peach) {
                if (categories != null) managingCategories = true else dialog = "Categories"
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFECE8E2)))
            SettingsRow("Merchants", merchants?.let { ManageUi.merchantsSubtitle(it.size) } ?: "48 remembered merchants", R.drawable.ic_pingpaisa_wallet, Tone.Sage) {
                if (merchants != null) managingMerchants = true else dialog = "Merchants"
            }
        }
        SettingsGroup("SECURITY & DATA", 14.dp) {
            if (captureEnabled != null) {
                SettingsRow("Payment capture", paymentCaptureSubtitle(captureEnabled), R.drawable.ic_pingpaisa_chat,
                    if (captureEnabled) Tone.Sage else Tone.Peach, onClick = onOpenCapture)
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFECE8E2)))
            }
            SettingsRow("App lock", lockEnabled?.let(::appLockSubtitle) ?: "PIN & biometric", R.drawable.ic_pingpaisa_lock, Tone.Lavender,
                toggle = lockEnabled ?: locked, toggleChanged = lockToggle) { if (lockEnabled == null) navigate(Screen.Lock) else lockToggle() }
            if (account != null) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFECE8E2)))
                SettingsRow("Change password", "Used to unlock PingPaisa", R.drawable.ic_pingpaisa_shield, Tone.Teal) { changingPassword = true }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFECE8E2)))
            SettingsRow("Export your data", transactionCount?.let { "CSV of your $it ${if (it == 1) "transaction" else "transactions"}" } ?: "CSV or PDF",
                R.drawable.ic_pingpaisa_download, Tone.Pink) {
                when {
                    transactionCount == null -> dialog = "Export your data"
                    transactionCount == 0 -> notice = "There are no transactions to export yet."
                    else -> onExport()
                }
            }
        }
        if (onCheckPassword != null) SettingsGroup("ACCOUNT", 14.dp) {
            if (onLogOut != null) {
                SettingsRow("Log out", "Lock PingPaisa now; your password opens it again", R.drawable.ic_pingpaisa_lock, Tone.Lavender, onClick = onLogOut)
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFECE8E2)))
            }
            SettingsRow("Reset all data", "Delete everything PingPaisa keeps on this phone", R.drawable.ic_pingpaisa_close, Tone.Pink) { resetting = true }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().heightIn(min = 110.5.dp).background(Color(0xFFE5EEE2), RoundedCornerShape(23.dp)).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ClayBadge(R.drawable.ic_pingpaisa_shield)
            Column(Modifier.weight(1f)) {
                Box(Modifier.height(24.dp), contentAlignment = Alignment.CenterStart) {
                    NativeText("PRIVACY PROMISE", 6.sp, tracking = .6.sp, modifier = Modifier.offset(y = 3.dp))
                }
                NativeText("Your money data stays on your phone", 12.sp, modifier = Modifier.padding(vertical = 3.dp))
                NativeText("Your transaction data never leaves this phone. Not even we can see it.", 7.sp, Color(0xFF718078))
            }
        }
        NativeText("PingPaisa v1.0 • Made with care in India", 6.sp, Color(0xFFAAAAAA),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp))
    }
    if (editingProfile && account != null) EditProfileSheet(account, onDismiss = { editingProfile = false }) { name, salary ->
        onSaveProfile(name, salary)
        editingProfile = false
        notice = "Profile saved"
    }
    if (changingPassword) ChangePasswordSheet(onDismiss = { changingPassword = false }, onChange = onChangePassword) {
        changingPassword = false
        notice = "Password changed. Use the new one next time you unlock PingPaisa."
    }
    if (managingMerchants && merchants != null) MerchantsSheet(merchants, categoryChoices, onDismiss = { managingMerchants = false },
        onRecategorize = { key, category -> onRecategorizeMerchant(key, category); managingMerchants = false; notice = "Saved. Past and future payments to this merchant now use $category." },
        onForget = { key -> onForgetMerchant(key); managingMerchants = false; notice = "Forgotten. Future payments to this merchant will be categorized automatically." })
    if (managingCategories && categories != null) CategoriesSheet(categories, onDismiss = { managingCategories = false },
        onRename = { old, new -> onRenameCategory(old, new); managingCategories = false; notice = "Renamed \"$old\" to \"${new.trim()}\"." },
        onRemove = { name -> onRemoveCategory(name); managingCategories = false; notice = "Removed \"$name\". Its payments are now Uncategorized." })
    if (resetting && onCheckPassword != null) ResetSheet(onDismiss = { resetting = false }, onCheckPassword = onCheckPassword,
        onExportFirst = { resetting = false; if ((transactionCount ?: 0) > 0) onExport() else notice = "There are no transactions to export yet." }) {
        resetting = false
        confirmReset = true
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { NativeText("Delete everything?", 18.sp) },
        text = { NativeText("This can't be undone. PingPaisa closes, and opens fresh at sign-up next time.", 12.sp, lineHeight = 18.sp) },
        confirmButton = { TextButton({ confirmReset = false; onReset() }) { NativeText("Delete everything", 12.sp, Color(0xFFB0675C), FontWeight.Bold) } },
        dismissButton = { TextButton({ confirmReset = false }) { NativeText("Cancel", 12.sp) } }
    )
    if (confirmingLockOff) LockOffSheet(onDismiss = { confirmingLockOff = false }, onConfirm = onDisableLock) {
        confirmingLockOff = false
        notice = "App lock is off. PingPaisa will open without a password."
    }
    notice?.let { text ->
        AlertDialog(onDismissRequest = { notice = null }, text = { NativeText(text, 12.sp, lineHeight = 18.sp) },
            confirmButton = { TextButton({ notice = null }) { NativeText("OK", 12.sp) } })
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

/** ✏️ on the profile card: name and monthly salary, with the sign-up rules. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileSheet(account: SettingsAccount, onDismiss: () -> Unit, onSave: (String, Double) -> Unit) {
    var name by remember { mutableStateOf(account.name) }
    var salary by remember { mutableStateOf(account.salaryInput) }
    var tried by remember { mutableStateOf(false) }
    val errors = if (tried) SignUpValidation.validateProfile(name, salary) else SignUpErrors()
    val focus = LocalFocusManager.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Clay.Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NativeText("Edit profile", 16.sp, tracking = (-.4).sp)
            ClayTextField("Your name", name, { name = it.take(60) }, error = errors.name,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }))
            ClayTextField("Monthly salary", salary, { if (it.length <= 9 && it.all(Char::isDigit)) salary = it },
                prefix = "₹", error = errors.salary,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
            NativeText("Home, Financial health, the forecast and Paisa AI use this salary. The scanner's Financial profile keeps the income you saved there.",
                8.sp, Color(0xFF8C918F), lineHeight = 12.sp)
            PrimaryAction("Save", Modifier.heightIn(min = 52.dp)) {
                tried = true
                focus.clearFocus()
                if (SignUpValidation.validateProfile(name, salary).isValid) onSave(name.trim(), salary.toDouble())
            }
        }
    }
}

/** Current, new and confirm. The current password is checked against the stored hash before anything changes. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ChangePasswordSheet(
    onDismiss: () -> Unit,
    onChange: (current: String, new: String, result: (ChangePasswordResult) -> Unit) -> Unit,
    onChanged: () -> Unit
) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var tried by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var wrongCurrent by remember { mutableStateOf(false) }
    val errors = if (tried) SignUpValidation.validatePasswordChange(current, new, confirm) else PasswordChangeErrors()
    val focus = LocalFocusManager.current
    val next = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) })
    val hidden = PasswordVisualTransformation()
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }, containerColor = Clay.Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NativeText("Change password", 16.sp, tracking = (-.4).sp)
            ClayTextField("Current password", current, { current = it; wrongCurrent = false },
                error = errors.current ?: if (wrongCurrent) "That isn't your current password" else null,
                visualTransformation = hidden,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next), keyboardActions = next)
            ClayTextField("New password", new, { new = it }, placeholder = "At least ${SignUpValidation.MIN_PASSWORD_LENGTH} characters",
                error = errors.new, visualTransformation = hidden,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next), keyboardActions = next)
            ClayTextField("Confirm new password", confirm, { confirm = it }, placeholder = "Type it again",
                error = errors.confirm, visualTransformation = hidden,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
            NativeText("There is no way to recover a forgotten password, so pick one you will remember.", 8.sp, Color(0xFF8C918F), lineHeight = 12.sp)
            PrimaryAction(if (saving) "Checking…" else "Change password", Modifier.heightIn(min = 52.dp), enabled = !saving) {
                tried = true
                focus.clearFocus()
                if (SignUpValidation.validatePasswordChange(current, new, confirm).isValid) {
                    saving = true
                    onChange(current, new) { result ->
                        saving = false
                        when (result) {
                            ChangePasswordResult.Changed -> onChanged()
                            ChangePasswordResult.WrongCurrent -> wrongCurrent = true
                            ChangePasswordResult.NoAccount -> onDismiss()
                        }
                    }
                }
            }
        }
    }
}

/** Turning the app lock off: asks for the password first. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LockOffSheet(onDismiss: () -> Unit, onConfirm: (password: String, result: (ok: Boolean) -> Unit) -> Unit, onDone: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    ModalBottomSheet(onDismissRequest = { if (!checking) onDismiss() }, containerColor = Clay.Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NativeText("Turn off app lock?", 16.sp, tracking = (-.4).sp)
            NativeText("Anyone who picks up your unlocked phone could then open PingPaisa and see your money. Enter your password to confirm.",
                9.sp, Color(0xFF8C918F), lineHeight = 13.sp)
            ClayTextField("Password", password, { password = it; error = null }, error = error,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
            PrimaryAction(if (checking) "Checking…" else "Turn off app lock", Modifier.heightIn(min = 52.dp), enabled = !checking) {
                focus.clearFocus()
                if (password.isEmpty()) { error = "Enter your password"; return@PrimaryAction }
                checking = true
                onConfirm(password) { ok ->
                    checking = false
                    if (ok) onDone() else error = "That isn't your password"
                }
            }
        }
    }
}

/** Settings → Merchants: the remembered merchant → category choices. Tap one to change or forget it. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MerchantsSheet(
    rows: List<MerchantRow>, choices: List<String>, onDismiss: () -> Unit,
    onRecategorize: (key: String, category: String) -> Unit, onForget: (key: String) -> Unit
) {
    var selected by remember { mutableStateOf<MerchantRow?>(null) }
    var confirmForget by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Clay.Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val m = selected
            if (m == null) {
                NativeText("Remembered merchants", 16.sp, tracking = (-.4).sp)
                NativeText("When you change a payment's category, PingPaisa remembers it for that merchant and uses it for every payment to them.",
                    8.sp, Color(0xFF8C918F), lineHeight = 12.sp)
                if (rows.isEmpty()) NativeText("None yet. Open a payment, choose a category, and the merchant appears here.", 9.sp, Color(0xFF8C918F))
                rows.forEach { row ->
                    val style = categoryStyle(row.category)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { selected = row }.padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ClayBadge(style.icon, style.tone, small = true)
                        Column(Modifier.weight(1f)) {
                            NativeText(row.name, 10.sp, weight = FontWeight.Bold, maxLines = 1)
                            NativeText("${row.category} · ${row.payments} ${if (row.payments == 1) "payment" else "payments"}", 7.sp, Color(0xFF999999), maxLines = 1)
                        }
                        NativeIcon(R.drawable.ic_pingpaisa_chevron, 14.dp)
                    }
                }
            } else {
                NativeText(m.name, 16.sp, tracking = (-.4).sp, maxLines = 2)
                NativeText("Now: ${m.category}. Choosing another category changes its past payments too.", 8.sp, Color(0xFF8C918F), lineHeight = 12.sp)
                NativeText("CHOOSE A CATEGORY", 8.sp, Color(0xFF849089), FontWeight.ExtraBold, tracking = 1.2.sp)
                choices.forEach { c ->
                    val style = categoryStyle(c)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { if (c != m.category) onRecategorize(m.key, c) }
                        .padding(vertical = 5.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ClayBadge(style.icon, style.tone, small = true)
                        NativeText(c, 10.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        if (c == m.category) NativeText("✓", 12.sp, Color(0xFF5F8176), FontWeight.Bold)
                    }
                }
                NativeText("Forget this merchant", 11.sp, Color(0xFFB0675C), FontWeight.ExtraBold,
                    Modifier.align(Alignment.CenterHorizontally).clip(CircleShape).clickable { confirmForget = true }.padding(horizontal = 16.dp, vertical = 8.dp))
                NativeText("← All merchants", 9.sp, Color(0xFF608275), FontWeight.ExtraBold,
                    Modifier.align(Alignment.CenterHorizontally).clip(CircleShape).clickable { selected = null }.padding(horizontal = 16.dp, vertical = 6.dp))
            }
        }
    }
    val m = selected
    if (confirmForget && m != null) AlertDialog(
        onDismissRequest = { confirmForget = false },
        title = { NativeText("Forget ${m.name}?", 18.sp) },
        text = { NativeText("Future payments to ${m.name} will be categorized automatically again. Past payments keep their category.", 12.sp, lineHeight = 18.sp) },
        confirmButton = { TextButton({ confirmForget = false; onForget(m.key) }) { NativeText("Forget", 12.sp, Color(0xFFB0675C), FontWeight.Bold) } },
        dismissButton = { TextButton({ confirmForget = false }) { NativeText("Cancel", 12.sp) } }
    )
}

/** Settings → Categories: every category in use; custom ones can be renamed or removed. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CategoriesSheet(
    rows: List<CategoryRow>, onDismiss: () -> Unit,
    onRename: (old: String, new: String) -> Unit, onRemove: (name: String) -> Unit
) {
    var selected by remember { mutableStateOf<CategoryRow?>(null) }
    var newName by remember { mutableStateOf("") }
    var tried by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Clay.Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val c = selected
            if (c == null) {
                NativeText("Categories", 16.sp, tracking = (-.4).sp)
                NativeText("Custom categories are the ones you typed yourself. Tap one to rename or remove it; built-in categories stay as they are.",
                    8.sp, Color(0xFF8C918F), lineHeight = 12.sp)
                if (rows.isEmpty()) NativeText("No payments yet.", 9.sp, Color(0xFF8C918F))
                rows.forEach { row ->
                    val style = categoryStyle(row.name)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .then(if (row.custom) Modifier.clickable { selected = row; newName = row.name; tried = false } else Modifier)
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ClayBadge(style.icon, style.tone, small = true)
                        Column(Modifier.weight(1f)) {
                            NativeText(row.name, 10.sp, weight = FontWeight.Bold, maxLines = 1)
                            NativeText("${row.payments} ${if (row.payments == 1) "payment" else "payments"}", 7.sp, Color(0xFF999999))
                        }
                        if (row.custom) {
                            NativeText("CUSTOM", 6.sp, Tone.Lavender.ink, FontWeight.ExtraBold,
                                Modifier.background(Color(0xFFE3DEF2), CircleShape).padding(horizontal = 6.dp, vertical = 3.dp))
                            NativeIcon(R.drawable.ic_pingpaisa_chevron, 14.dp)
                        }
                    }
                }
            } else {
                val error = if (tried) ManageUi.renameError(c.name, newName) else null
                NativeText(c.name, 16.sp, tracking = (-.4).sp, maxLines = 2)
                NativeText("${c.payments} ${if (c.payments == 1) "payment uses" else "payments use"} this category.", 8.sp, Color(0xFF8C918F))
                ClayTextField("New name", newName, { newName = it.take(40) }, error = error,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
                NativeText("Its payments, remembered merchants and budget move to the new name. If a category with that name already exists, the two are merged.",
                    8.sp, Color(0xFF8C918F), lineHeight = 12.sp)
                PrimaryAction("Rename", Modifier.heightIn(min = 52.dp)) {
                    tried = true
                    focus.clearFocus()
                    if (ManageUi.renameError(c.name, newName) == null) onRename(c.name, newName.trim())
                }
                NativeText("Remove this category", 11.sp, Color(0xFFB0675C), FontWeight.ExtraBold,
                    Modifier.align(Alignment.CenterHorizontally).clip(CircleShape).clickable { confirmRemove = true }.padding(horizontal = 16.dp, vertical = 8.dp))
                NativeText("← All categories", 9.sp, Color(0xFF608275), FontWeight.ExtraBold,
                    Modifier.align(Alignment.CenterHorizontally).clip(CircleShape).clickable { selected = null }.padding(horizontal = 16.dp, vertical = 6.dp))
            }
        }
    }
    val c = selected
    if (confirmRemove && c != null) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { NativeText("Remove ${c.name}?", 18.sp) },
        text = { NativeText("Its ${c.payments} ${if (c.payments == 1) "payment moves" else "payments move"} to Uncategorized. Merchants remembered for it are forgotten, and its budget, if it has one, is deleted.",
            12.sp, lineHeight = 18.sp) },
        confirmButton = { TextButton({ confirmRemove = false; onRemove(c.name) }) { NativeText("Remove", 12.sp, Color(0xFFB0675C), FontWeight.Bold) } },
        dismissButton = { TextButton({ confirmRemove = false }) { NativeText("Cancel", 12.sp) } }
    )
}

/** Reset all data, first step: what will be deleted, an export shortcut, and the password. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ResetSheet(
    onDismiss: () -> Unit, onCheckPassword: (password: String, result: (ok: Boolean) -> Unit) -> Unit,
    onExportFirst: () -> Unit, onConfirmed: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    ModalBottomSheet(onDismissRequest = { if (!checking) onDismiss() }, containerColor = Clay.Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NativeText("Reset all data", 16.sp, tracking = (-.4).sp)
            NativeText("This deletes, from this phone:", 9.sp, Color(0xFF8C918F))
            resetDeletes.forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NativeText("•", 9.sp, Color(0xFFB0675C), FontWeight.Bold)
                    NativeText(item, 9.sp, lineHeight = 13.sp)
                }
            }
            NativeText("Want a copy first? Export your data →", 9.sp, Color(0xFF608275), FontWeight.ExtraBold,
                Modifier.clip(CircleShape).clickable(onClick = onExportFirst).padding(vertical = 4.dp))
            ClayTextField("Your password", password, { password = it; error = null }, error = error,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
            PrimaryAction(if (checking) "Checking…" else "Continue", Modifier.heightIn(min = 52.dp), enabled = !checking) {
                focus.clearFocus()
                if (password.isEmpty()) { error = "Enter your password"; return@PrimaryAction }
                checking = true
                onCheckPassword(password) { ok ->
                    checking = false
                    if (ok) onConfirmed() else error = "That isn't your password"
                }
            }
        }
    }
}
