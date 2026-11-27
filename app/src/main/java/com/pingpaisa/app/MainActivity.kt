package com.pingpaisa.app

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import com.pingpaisa.app.ui.scan.ScanViewModel
import com.pingpaisa.app.ui.scan.ScanScreen as IntegratedScanScreen
import com.pingpaisa.app.ui.theme.PingPaisaTheme as ScannerTheme
import com.pingpaisa.app.ui.profile.FinancialProfileScreen
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.pingpaisa.app.data.Categorizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// Shared tokens mirror the original web prototype.
internal object Clay {
    val Cream = Color(0xFFFAF7F2)
    val Sage = Color(0xFFA8C4A2)
    val Teal = Color(0xFF7FA99B)
    val Pink = Color(0xFFF4C6C6)
    val Peach = Color(0xFFF2D3B3)
    val Lavender = Color(0xFFC8BFE7)
    val Ink = Color(0xFF35453F)
    val Muted = Color(0xFF8B928E)
    val White = Color(0xFFFDFBF8)
    val Dark = Color(0xFF242D29)
    val Gutter = 20.dp
    val Shape = RoundedCornerShape(26.dp)
}

internal enum class Screen(val title: String, val group: String) {
    Onboarding("Onboarding", "Get started"), SignUp("Create account", "Get started"),
    NotificationAccess("Notification access", "Get started"),
    Lock("App Lock", "Get started"),
    Home("Home Dashboard", "Money"), Transactions("Transactions", "Money"),
    Detail("Transaction detail", "Money"), Entry("Add an expense", "Money"),
    Budgets("Budgets & goals", "Planning"), Flow("Cash flow", "Planning"),
    Recurring("Recurring payments", "Planning"), Loan("Loan simulator", "Planning"),
    Forecast("Forecast", "Intelligence"), Heatmap("Your money map", "Intelligence"),
    Scan("Scan price", "Intelligence"), Chat("Paisa AI", "Intelligence"),
    Health("Financial health", "Intelligence"), Settings("Settings", "Settings"),
    Style("Style Guide", "Settings"), FinancialProfile("Financial profile", "Settings")
}

class MainActivity : ComponentActivity() {
    // Survives rotation; shared by every screen.
    private val appViewModel: AppViewModel by viewModels()
    private val scanner: ScanViewModel by viewModels()
    internal var shareVersion by mutableIntStateOf(0)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Load the merchant dataset before anything can categorize (same as the old tracker).
        Categorizer.init(applicationContext)
        enableEdgeToEdge()
        if (savedInstanceState == null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) consumeShare(intent)
        setContent { PingPaisa(vm = appViewModel, scanner = scanner, shareVersion = shareVersion) }
    }

    // App lock timer: leaving the app for 2+ minutes asks for the password again.
    // Rotation also stops/starts the activity, so it is ignored.
    override fun onStart() {
        super.onStart()
        appViewModel.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) appViewModel.onAppBackgrounded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Shares are one-shot inputs, not the activity's new launch identity.
        // Keep the original launch intent for recreation/recents after consuming them.
        if (intent.action !in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) setIntent(intent)
        consumeShare(intent)
    }

    private fun consumeShare(incoming: Intent) {
        if (incoming.action !in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) return
        val text = incoming.getStringExtra(Intent.EXTRA_TEXT)
        val subject = incoming.getStringExtra(Intent.EXTRA_SUBJECT)
        val images = if (incoming.action == Intent.ACTION_SEND_MULTIPLE)
            IntentCompat.getParcelableArrayListExtra(incoming, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else listOfNotNull(IntentCompat.getParcelableExtra(incoming, Intent.EXTRA_STREAM, Uri::class.java))
        incoming.action = Intent.ACTION_MAIN
        incoming.removeExtra(Intent.EXTRA_TEXT)
        incoming.removeExtra(Intent.EXTRA_SUBJECT)
        incoming.removeExtra(Intent.EXTRA_STREAM)
        if (text.isNullOrBlank() && subject.isNullOrBlank() && images.isEmpty()) return
        scanner.handleShared(text, subject, images)
        shareVersion++
    }
}

/**
 * [vm] is the live app state. It is null only in Android Studio previews and the
 * screenshot tests, which have no database and keep showing the design's sample data.
 */
@Composable
internal fun PingPaisa(initialScreen: Screen = Screen.Onboarding, vm: AppViewModel? = null, scanner: ScanViewModel? = null, shareVersion: Int = 0) {
    val colors = lightColorScheme(primary = Clay.Teal, onPrimary = Color.White,
        background = Clay.Cream, surface = Clay.Cream, onSurface = Clay.Ink,
        secondaryContainer = Clay.Sage, onSecondaryContainer = Clay.Ink)
    MaterialTheme(colorScheme = colors, typography = PingPaisaTypography) {
    // Clay squish press feedback for every clickable, replacing the Material ripple.
    CompositionLocalProvider(LocalIndication provides ClayPressIndication) {
        var current by rememberSaveable { mutableStateOf(initialScreen) }
        var history by rememberSaveable { mutableStateOf(listOf<Screen>()) }
        var goingBack by remember { mutableStateOf(false) }
        val states = rememberSaveableStateHolder()
        val drawer = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        fun navigate(screen: Screen) {
            goingBack = false
            if (current != screen) { history = (history + current).takeLast(20); current = screen }
            scope.launch { drawer.close() }
        }
        fun back() {
            goingBack = true
            current = history.lastOrNull() ?: Screen.Home
            history = history.dropLast(1)
        }
        fun menu() { scope.launch { drawer.open() } }
        /** Go to [screen] and forget the back history (after login and after setup). */
        fun startFresh(screen: Screen) {
            goingBack = false
            history = emptyList()
            current = screen
            scope.launch { drawer.close() }
        }

        // Start-up gate: decides what may be shown (see StartupGate.kt). Previews have no vm and skip it.
        val account = vm?.account?.collectAsStateWithLifecycle()?.value
        val unlocked = vm?.unlocked?.collectAsStateWithLifecycle()?.value ?: false
        val shown: Screen? = if (account == null) current else gateScreen(account, unlocked, current)
        LaunchedEffect(shown) { if (shown != null && shown != current) startFresh(shown) }
        if (shown == null) { StartupSplash(); return@CompositionLocalProvider }
        val atRoot = if (account == null) shown == Screen.Home else isRootScreen(account, unlocked, shown)

        LaunchedEffect(shareVersion) { if (shareVersion > 0) navigate(Screen.Scan) }

        // On a root screen (Home, Onboarding, locked login) system Back leaves the app.
        BackHandler(drawer.isOpen || !atRoot) {
            if (drawer.isOpen) scope.launch { drawer.close() } else back()
        }
        ModalNavigationDrawer(drawerState = drawer, drawerContent = {
            ModalDrawerSheet(modifier = Modifier.fillMaxWidth(.79f), drawerContainerColor = Clay.Cream,
                drawerShape = RoundedCornerShape(topEnd = 31.dp, bottomEnd = 31.dp)) {
                Row(Modifier.fillMaxWidth().height(76.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Brand()
                    Spacer(Modifier.weight(1f))
                    RoundAction(R.drawable.ic_pingpaisa_close, "Close navigator", 33.dp) { scope.launch { drawer.close() } }
                }
                // With an account the drawer leaves out first-run, login and design-reference screens;
                // previews and screenshot tests (no vm) still list every screen.
                val listed = Screen.entries.filter { vm == null || it !in drawerHiddenScreens }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
                    listed.groupBy { it.group }.forEach { (group, screens) ->
                        NativeText(group.uppercase(), 7.sp, Clay.Muted, FontWeight.ExtraBold,
                            Modifier.padding(start = 11.dp, top = 13.dp, bottom = 6.dp), tracking = 1.sp)
                        screens.forEach { screen ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 37.dp)
                                .background(if (screen == shown) Clay.Sage.copy(alpha = .25f) else Color.Transparent, RoundedCornerShape(14.dp))
                                .clickable { navigate(screen) }.padding(horizontal = 9.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                NativeText((listed.indexOf(screen) + 1).toString().padStart(2, '0'), 8.sp, Clay.Muted, modifier = Modifier.width(28.dp))
                                NativeText(screen.title, 9.sp, modifier = Modifier.weight(1f))
                                NativeIcon(R.drawable.ic_pingpaisa_chevron, 13.dp, Clay.Muted)
                            }
                        }
                    }
                }
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NativeIcon(R.drawable.ic_pingpaisa_shield, 15.dp, Clay.Teal)
                    Column { NativeText("Private by design", 9.sp, weight = FontWeight.Bold); NativeText("Your money data stays on your phone", 7.sp, Clay.Muted) }
                }
            }
        }) {
            val tabs = shown !in listOf(Screen.Onboarding, Screen.SignUp, Screen.NotificationAccess, Screen.Lock, Screen.Style)
            // Device-owned bars are kept outside the app content, never simulated or doubled.
            Box(Modifier.fillMaxSize().background(if (shown == Screen.Scan) Clay.Dark else Clay.Cream)
                .windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                AnimatedContent(shown, modifier = Modifier.fillMaxSize(), transitionSpec = {
                    val direction = if (goingBack) -1 else 1
                    // Soft, unhurried page change: the new page glides in and settles, the old one drifts away.
                    (fadeIn(tween(420, 90, SoftEase)) + slideInHorizontally(tween(520, easing = SoftEase)) { it / 10 * direction }
                        + scaleIn(tween(520, easing = SoftEase), initialScale = .985f))
                        .togetherWith(fadeOut(tween(220, easing = SoftEase)) + slideOutHorizontally(tween(520, easing = SoftEase)) { -it / 14 * direction })
                }, label = "Screen navigation") { screen ->
                    states.SaveableStateProvider(screen.name) {
                        // vm == null: previews/screenshot tests keep the menu on every screen.
                        CompositionLocalProvider(LocalMenuEnabled provides (vm == null || screen !in noMenuScreens)) {
                            Box(Modifier.fillMaxSize().padding(bottom = if (screen in listOf(Screen.Scan, Screen.FinancialProfile)) 58.dp else 0.dp)) {
                                NativeScreen(screen, ::navigate, ::menu, ::back, vm, ::startFresh, scanner)
                            }
                        }
                    }
                }
                if (tabs) NativeTabs(shown, ::navigate, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
    }
}

@Composable
internal fun NativeScreen(
    screen: Screen, navigate: (Screen) -> Unit, menu: () -> Unit, back: () -> Unit,
    vm: AppViewModel? = null,   // null only in previews and screenshot tests
    startFresh: (Screen) -> Unit = navigate,
    scanner: ScanViewModel? = null   // the teammate's scanner; null in previews and screenshot tests
) {
    when (screen) {
        Screen.Home -> if (vm == null) HomeDashboard(navigate, menu) else {
            val summary by vm.homeSummary.collectAsStateWithLifecycle()
            val account by vm.account.collectAsStateWithLifecycle()
            val health by vm.health.collectAsStateWithLifecycle()
            val name = (account as? AccountState.Ready)?.profile?.name.orEmpty()
            val risk = health?.result?.risk
            val ui = remember(summary, name, risk) {
                summary?.let { HomeUi.from(it, name, risk) }
            }
            // A blank moment while the first numbers are worked out, then the real screen.
            if (ui == null) Box(Modifier.fillMaxSize())
            else HomeDashboard(navigate, menu, ui) { id ->
                if (id != null) vm.openTransaction(id)
                navigate(Screen.Detail)
            }
        }
        Screen.Transactions -> if (vm == null) TransactionsScreen(menu, navigate) else {
            val all by vm.transactions.collectAsStateWithLifecycle()
            // 0 = this month, -1 = last month, ... Never later than the current month.
            var monthOffset by rememberSaveable { mutableIntStateOf(0) }
            val ui = remember(all, monthOffset) {
                TransactionsUi.from(all, YearMonth.now().plusMonths(monthOffset.toLong()), LocalDate.now())
            }
            TransactionsScreen(menu, navigate, ui, onMonth = { monthOffset = (monthOffset + it).coerceAtMost(0) }) { id ->
                if (id != null) vm.openTransaction(id)
                navigate(Screen.Detail)
            }
        }
        Screen.Detail -> if (vm == null) DetailScreen(menu, back) else {
            val id by vm.selectedTransactionId.collectAsStateWithLifecycle()
            // Live: if the transaction changes (or is removed) the screen follows.
            val source = remember(id) {
                val chosen = id
                if (chosen == null) flowOf(DetailLoad.Missing)
                else vm.observeTransaction(chosen).map { t -> if (t == null) DetailLoad.Missing else DetailLoad.Found(t) }
            }
            val load by source.collectAsStateWithLifecycle(initialValue = DetailLoad.Loading)
            val categories by vm.categoryChoices.collectAsStateWithLifecycle()
            DetailScreen(menu, back, load, categories) { transaction, category, note ->
                vm.saveDetail(transaction, category, note)
            }
        }
        // Saves through the ViewModel; lists update by themselves (live database queries).
        Screen.Entry -> EntryScreen(menu, back, onSave = vm?.let { model ->
            { amount, name, isIncome, category ->
                if (isIncome) model.addIncome(amount, name) else model.addExpense(amount, name, category)
            }
        })
        Screen.Budgets -> if (vm == null) BudgetsScreen(menu, back) else {
            val budgets by vm.budgets.collectAsStateWithLifecycle()
            val all by vm.transactions.collectAsStateWithLifecycle()
            val choices by vm.categoryChoices.collectAsStateWithLifecycle()
            val savingsGoals by vm.savingsGoals.collectAsStateWithLifecycle()
            val ui = remember(budgets, all) { BudgetsUi.from(budgets, all, LocalDate.now()) }
            val goalRows = remember(savingsGoals) { GoalsUi.from(savingsGoals) }
            BudgetsScreen(menu, back, ui, choices,
                goals = goalRows,
                onSaveGoal = { id, name, target, saved ->
                    val existing = savingsGoals.firstOrNull { it.id == id }
                    if (existing == null) vm.addSavingsGoal(name, target, saved)
                    else vm.updateSavingsGoal(existing.copy(name = name, targetAmount = target, savedAmount = saved))
                },
                onDeleteGoal = { id -> savingsGoals.firstOrNull { it.id == id }?.let(vm::deleteSavingsGoal) },
                onSave = { category, limit -> vm.setBudget(category, limit) },
                onDelete = { category -> budgets.firstOrNull { it.category == category }?.let(vm::deleteBudget) })
        }
        Screen.Flow -> if (vm == null) FlowScreen(menu, back) else {
            val health by vm.health.collectAsStateWithLifecycle()
            val all by vm.transactions.collectAsStateWithLifecycle()
            val account by vm.account.collectAsStateWithLifecycle()
            val current = health
            if (current == null) Box(Modifier.fillMaxSize())   // still loading
            else {
                val salary = (account as? AccountState.Ready)?.profile?.monthlySalary ?: 0.0
                val ui = remember(all, current, account) { CashFlowUi.from(all, salary, current.raw.expense, LocalDate.now()) }
                FlowScreen(menu, back, ui)
            }
        }
        Screen.Forecast -> if (vm == null) ForecastScreen(menu, back) else {
            val health by vm.health.collectAsStateWithLifecycle()
            val all by vm.transactions.collectAsStateWithLifecycle()
            val items by vm.recurringItems.collectAsStateWithLifecycle()
            val budgets by vm.budgets.collectAsStateWithLifecycle()
            val current = health
            if (current == null) Box(Modifier.fillMaxSize())   // still loading
            else {
                val ui = remember(all, items, budgets, current) {
                    ForecastUi.from(all, items, current.raw.expense, current.monthlyIncome, budgets, LocalDate.now())
                }
                ForecastScreen(menu, back, ui, onAlert = { navigate(Screen.Budgets) })
            }
        }
        Screen.Heatmap -> if (vm == null) HeatmapScreen(menu, back) else {
            val all by vm.transactions.collectAsStateWithLifecycle()
            val ui = remember(all) { HeatmapUi.from(all, LocalDate.now()) }
            HeatmapScreen(menu, back, ui)
        }
        Screen.Recurring -> if (vm == null) RecurringScreen(menu, back) else {
            val items by vm.recurringItems.collectAsStateWithLifecycle()
            val detected by vm.detectedRecurring.collectAsStateWithLifecycle()
            val all by vm.transactions.collectAsStateWithLifecycle()
            val ui = remember(items, detected, all) { RecurringUi.from(items, detected, all, LocalDate.now()) }
            RecurringScreen(menu, back, ui,
                onAdd = { name, amount, day, type -> vm.addRecurring(name, amount, day, type) },
                onDelete = { id -> items.firstOrNull { it.id == id }?.let(vm::deleteRecurring) })
        }
        Screen.Scan -> if (scanner != null) ScannerTheme {
            IntegratedScanScreen(scanner, onOpenProfile = { navigate(Screen.FinancialProfile) }, onMenu = menu, onBack = back)
        } else NativeText("Open the app on a device to use the camera scanner.", 12.sp, modifier = Modifier.padding(20.dp))
        Screen.Chat -> if (vm == null) ChatScreen(menu, back, navigate) else {
            val health by vm.health.collectAsStateWithLifecycle()
            val all by vm.transactions.collectAsStateWithLifecycle()
            val items by vm.recurringItems.collectAsStateWithLifecycle()
            val budgets by vm.budgets.collectAsStateWithLifecycle()
            val goals by vm.savingsGoals.collectAsStateWithLifecycle()
            val account by vm.account.collectAsStateWithLifecycle()
            val messages by vm.chat.collectAsStateWithLifecycle()
            val confirmedActionIds by vm.confirmedAiActionIds.collectAsStateWithLifecycle()
            val scannedProducts by (scanner?.products ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.pingpaisa.app.data.Product>())).collectAsStateWithLifecycle()
            val scannerSelectedId by (scanner?.selectedId ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsStateWithLifecycle()
            val scannerAssessment by (scanner?.assessment ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsStateWithLifecycle()
            val selectedScan = scannedProducts.firstOrNull { it.id == scannerSelectedId }
            val localContext = androidx.compose.ui.platform.LocalContext.current
            val loanQuotes = remember(localContext) { com.pingpaisa.app.healthscore.QuoteStore.load(localContext) }
            val today = LocalDate.now()
            val monthName = today.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
            val name = (account as? AccountState.Ready)?.profile?.name.orEmpty()
            val monthlySalary = (account as? AccountState.Ready)?.profile?.monthlySalary ?: 0.0
            val context = remember(health, all, items, budgets, goals, name, selectedScan, scannerAssessment) {
                health?.let { h ->
                    AiContext(name, today, h.monthlyIncome, h.raw.expense,
                        ForecastUi.from(all, items, h.raw.expense, h.monthlyIncome, budgets, today).projectedAmount,
                        h.result.score, when (h.result.risk) {
                            com.pingpaisa.app.healthscore.ScoreCalculator.RiskLevel.LOW -> "low risk"
                            com.pingpaisa.app.healthscore.ScoreCalculator.RiskLevel.MEDIUM -> "medium risk"
                            com.pingpaisa.app.healthscore.ScoreCalculator.RiskLevel.HIGH -> "high risk"
                        }, h.insight.text, all, budgets, goals, items, h.raw, loanQuotes, monthlySalary,
                        selectedScan?.price?.takeIf { !selectedScan.needsReview }, selectedScan?.name,
                        selectedScan?.let { scannerAssessment?.line(it.id)?.decision?.name?.lowercase()?.replace('_', ' ') },
                        selectedScan?.let { scannerAssessment?.line(it.id)?.reason },
                        selectedScan?.let { item -> scannerAssessment?.line(item.id)?.let { line ->
                            AiMetric("Scanner price", com.pingpaisa.app.healthscore.Money.inr(item.price ?: 0.0))
                        } })
                }
            }
            ChatScreen(menu, back, navigate, ChatState(
                PaisaAi.greeting(name, monthName), messages, PaisaAi.suggestions(monthName),
                confirmedActionIds = confirmedActionIds,
                onConfirmAction = vm::confirmAiAction,
                onOpenTransaction = { id -> vm.openTransaction(id); navigate(Screen.Detail) }
            ) { q ->
                val time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.getDefault()))
                val previousQuestion = messages.lastOrNull { it.user }?.text
                val reply = context?.let { PaisaAi.answer(q, it, previousQuestion) } ?: AiReply("One moment.", "I’m still loading your numbers. Ask again in a second.")
                vm.addChat(q, reply, time)
            })
        }
        Screen.Health -> if (vm == null) HealthScreen(menu, back) else {
            val health by vm.health.collectAsStateWithLifecycle()
            val history by vm.scoreHistory.collectAsStateWithLifecycle()
            val current = health
            if (current == null) Box(Modifier.fillMaxSize())   // still loading
            else {
                val ui = remember(current, history) {
                    HealthUi.from(current.result, current.insight, current.monthlyIncome, current.raw.expense, history, LocalDate.now())
                }
                // Real scores (never loan "what ifs") are saved so next month can say "Up 4 points".
                LaunchedEffect(current.result.score) { vm.recordScoreIfNeeded(current.result.score, current.result.risk) }
                HealthScreen(menu, back, ui)
            }
        }
        Screen.Loan -> if (vm == null) LoanScreen(menu, back) else {
            val health by vm.health.collectAsStateWithLifecycle()
            val baseline = health?.raw
            if (baseline == null) Box(Modifier.fillMaxSize()) else LoanScreen(menu, back, baseline)
        }
        Screen.Settings -> if (vm == null) SettingsScreen(menu, navigate) else {
            val account by vm.account.collectAsStateWithLifecycle()
            val profile = (account as? AccountState.Ready)?.profile
            val all by vm.transactions.collectAsStateWithLifecycle()
            val overrides by vm.merchantOverrides.collectAsStateWithLifecycle()
            val choices by vm.categoryChoices.collectAsStateWithLifecycle()
            // Notification access is switched in Android's settings, so it is re-checked whenever the app comes back.
            val context = androidx.compose.ui.platform.LocalContext.current
            var capture by remember { mutableStateOf(isNotificationAccessEnabled(context)) }
            androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                capture = isNotificationAccessEnabled(context)
            }
            SettingsScreen(menu, navigate, profile?.let { SettingsAccount(it.name, it.monthlySalary) },
                onSaveProfile = { name, salary -> vm.updateProfile(name, salary) },
                onChangePassword = { current, new, result -> vm.changePassword(current, new, result) },
                captureEnabled = capture, onOpenCapture = { openNotificationAccessSettings(context) },
                lockEnabled = profile?.appLockEnabled,
                onEnableLock = { vm.enableAppLock() },
                onDisableLock = { password, result -> vm.disableAppLock(password, result) },
                merchants = ManageUi.merchants(overrides, all),
                categories = remember(all, overrides) { ManageUi.categories(all, overrides, Categorizer.getAllCategories()) },
                categoryChoices = choices,
                onRecategorizeMerchant = { key, category -> vm.recategorizeMerchant(key, category) },
                onForgetMerchant = { key -> vm.forgetMerchant(key) },
                onRenameCategory = { old, new -> vm.renameCategory(old, new) },
                onRemoveCategory = { name -> vm.removeCategory(name) },
                transactionCount = all.size,
                onExport = { CsvExport.share(context, all) },
                onLogOut = if (profile?.appLockEnabled == true) ({ vm.lockNow() }) else null,
                onCheckPassword = { password, result -> vm.checkPassword(password, result) },
                // Android's own "Clear data": wipes the database, preferences, files and cache (the scanner's
                // too) and closes the app; the next start is a fresh sign-up.
                onReset = { context.getSystemService(android.app.ActivityManager::class.java).clearApplicationUserData() })
        }
        Screen.Onboarding -> OnboardingScreen(menu) { navigate(Screen.SignUp) }
        // First run: Onboarding → Sign up → Notification access → Home. Setup screens are
        // dropped from the back history so Back from Home can't return to them.
        Screen.SignUp -> SignUpScreen(menu) { name, salary, password, done ->
            if (vm == null) done(false)   // previews/screenshot tests have no database
            else vm.createAccount(name, salary, password) { created ->
                if (created) startFresh(Screen.NotificationAccess)
                done(created)
            }
        }
        Screen.NotificationAccess -> NotificationAccessScreen(menu) { startFresh(Screen.Home) }
        Screen.Lock -> {
            val account = vm?.account?.collectAsStateWithLifecycle()?.value
            LockScreen(
                menu,
                name = (account as? AccountState.Ready)?.profile?.name,
                // Previews/screenshot tests have no database, so they unlock straight away.
                onLogin = { password, result -> if (vm == null) result(LoginResult.Success) else vm.login(password, result) }
            ) { startFresh(Screen.Home) }   // login is not kept in the back history
        }
        Screen.FinancialProfile -> if (scanner != null) {
            val profile by scanner.profile.collectAsState()
            // Step 48: a profile never saved starts filled in from the app's own data (salary, recurring
            // payments, spending). Read from the database once; the form is shown when it is ready.
            val context = androidx.compose.ui.platform.LocalContext.current
            val prefill by produceState<com.pingpaisa.app.finance.FinancialProfile?>(null, vm) {
                value = if (vm == null) com.pingpaisa.app.finance.FinancialProfile() else TrackerExpenseHistory.prefill(context.applicationContext)
            }
            val start = profile ?: prefill
            if (start == null) Box(Modifier.fillMaxSize())   // reading the database, a moment
            else FinancialProfileScreen(start, onSave = { scanner.saveProfile(it); back() }, onBack = back,
                onMenu = menu, persistent = scanner.profileIsPersistent)
        } else NativeText("Open Settings on a device to edit your financial profile.", 12.sp, modifier = Modifier.padding(20.dp))
        Screen.Style -> StyleScreen(menu)
    }
}

/** Shown for the moment it takes to read the account on start, instead of guessing a screen. */
@Composable
private fun StartupSplash() {
    Box(Modifier.fillMaxSize().background(Clay.Cream), contentAlignment = Alignment.Center) { Brand(large = true) }
}

@Composable
private fun NativeTabs(current: Screen, navigate: (Screen) -> Unit, modifier: Modifier) {
    val selected = when (current) {
        Screen.Home -> Screen.Home; Screen.Scan -> Screen.Scan; Screen.Chat -> Screen.Chat
        Screen.Settings, Screen.FinancialProfile -> Screen.Settings; else -> Screen.Transactions
    }
    Row(modifier.fillMaxWidth().height(58.dp).background(Clay.Cream.copy(alpha = .96f))
        .drawWithContent {
            drawContent()
            drawLine(Color(0xFF56625B).copy(alpha = .06f), Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
        }.padding(start = 12.dp, end = 12.dp, top = 9.dp),
        horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.Top) {
        listOf(Triple(Screen.Home,"Home",R.drawable.ic_pingpaisa_home),
            Triple(Screen.Transactions,"Stats",R.drawable.ic_pingpaisa_chart),
            Triple(Screen.Scan,"Scan",R.drawable.ic_pingpaisa_scan),
            Triple(Screen.Chat,"AI",R.drawable.ic_pingpaisa_chat),
            Triple(Screen.Settings,"You",R.drawable.ic_pingpaisa_user)).forEach { (screen,label,icon) ->
            Column(Modifier.widthIn(min = 48.dp).fillMaxHeight()
                .selectable(screen == selected, role = Role.Tab, onClick = { navigate(screen) }),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val active = screen == selected
                val color by animateColorAsState(if (active) Color(0xFF608578) else Color(0xFFAFB1AE), tween(320, easing = SoftEase), label = "Tab selection")
                val lift by androidx.compose.animation.core.animateFloatAsState(if (active) 1f else 0f,
                    androidx.compose.animation.core.spring(dampingRatio = .5f, stiffness = 420f), label = "Tab lift")
                Box(Modifier.size(18.dp,20.dp).graphicsLayer {
                    translationY = -2.dp.toPx() * lift; scaleX = 1f + .08f * lift; scaleY = 1f + .08f * lift
                }, contentAlignment = Alignment.Center) { NativeIcon(icon,18.dp,color) }
                NativeText(label,7.sp,color,FontWeight.Bold)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "Compact phone", showBackground = true, widthDp = 320, heightDp = 568)
@Preview(name = "Large phone", showBackground = true, widthDp = 430, heightDp = 932)
@Preview(name = "Landscape phone", showBackground = true, widthDp = 844, heightDp = 390)
@Preview(name = "Large text", showBackground = true, widthDp = 360, heightDp = 800, fontScale = 1.5f)
@Composable
private fun PingPaisaPreview() { PingPaisa() }
