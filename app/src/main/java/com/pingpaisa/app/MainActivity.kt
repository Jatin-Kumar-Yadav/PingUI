package com.pingpaisa.app

import android.os.Bundle
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
    Forecast("June forecast", "Intelligence"), Heatmap("Your money map", "Intelligence"),
    Scan("Scan price", "Intelligence"), Chat("Paisa AI", "Intelligence"),
    Health("Financial health", "Intelligence"), Settings("Settings", "Settings"),
    Style("Style Guide", "Settings")
}

class MainActivity : ComponentActivity() {
    // Survives rotation; shared by every screen.
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Load the merchant dataset before anything can categorize (same as the old tracker).
        Categorizer.init(applicationContext)
        enableEdgeToEdge()
        setContent { PingPaisa(vm = appViewModel) }
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
}

/**
 * [vm] is the live app state. It is null only in Android Studio previews and the
 * screenshot tests, which have no database and keep showing the design's sample data.
 */
@Composable
internal fun PingPaisa(initialScreen: Screen = Screen.Onboarding, vm: AppViewModel? = null) {
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
                    Column { NativeText("Private by design", 9.sp, weight = FontWeight.Bold); NativeText("100% on-device", 7.sp, Clay.Muted) }
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
                            NativeScreen(screen, ::navigate, ::menu, ::back, vm, ::startFresh)
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
    startFresh: (Screen) -> Unit = navigate
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
        Screen.Scan -> ScanScreen(menu, back)
        Screen.Chat -> if (vm == null) ChatScreen(menu, back, navigate) else {
            val health by vm.health.collectAsStateWithLifecycle()
            val all by vm.transactions.collectAsStateWithLifecycle()
            val items by vm.recurringItems.collectAsStateWithLifecycle()
            val budgets by vm.budgets.collectAsStateWithLifecycle()
            val goals by vm.savingsGoals.collectAsStateWithLifecycle()
            val account by vm.account.collectAsStateWithLifecycle()
            val messages by vm.chat.collectAsStateWithLifecycle()
            val today = LocalDate.now()
            val monthName = today.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
            val name = (account as? AccountState.Ready)?.profile?.name.orEmpty()
            val context = remember(health, all, items, budgets, goals, name) {
                health?.let { h ->
                    AiContext(name, today, h.monthlyIncome, h.raw.expense,
                        ForecastUi.from(all, items, h.raw.expense, h.monthlyIncome, budgets, today).projectedAmount,
                        h.result.score, when (h.result.risk) {
                            com.pingpaisa.app.healthscore.ScoreCalculator.RiskLevel.LOW -> "low risk"
                            com.pingpaisa.app.healthscore.ScoreCalculator.RiskLevel.MEDIUM -> "medium risk"
                            com.pingpaisa.app.healthscore.ScoreCalculator.RiskLevel.HIGH -> "high risk"
                        }, h.insight.text, all, budgets, goals, items)
                }
            }
            ChatScreen(menu, back, navigate, ChatState(PaisaAi.greeting(name, monthName), messages, PaisaAi.suggestions(monthName)) { q ->
                val time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.getDefault()))
                val reply = context?.let { PaisaAi.answer(q, it) } ?: AiReply("One moment.", "I’m still loading your numbers. Ask again in a second.")
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
        Screen.Settings -> SettingsScreen(menu, navigate)
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
        Screen.Settings -> Screen.Settings; else -> Screen.Transactions
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
