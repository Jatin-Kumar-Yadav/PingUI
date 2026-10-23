package com.pingpaisa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
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
    Onboarding("Onboarding", "Get started"), Lock("App Lock", "Get started"),
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PingPaisa() }
    }
}

@Composable
internal fun PingPaisa(initialScreen: Screen = Screen.Onboarding) {
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
        BackHandler(drawer.isOpen || current != Screen.Home) {
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
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
                    Screen.entries.groupBy { it.group }.forEach { (group, screens) ->
                        NativeText(group.uppercase(), 7.sp, Clay.Muted, FontWeight.ExtraBold,
                            Modifier.padding(start = 11.dp, top = 13.dp, bottom = 6.dp), tracking = 1.sp)
                        screens.forEach { screen ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 37.dp)
                                .background(if (screen == current) Clay.Sage.copy(alpha = .25f) else Color.Transparent, RoundedCornerShape(14.dp))
                                .clickable { navigate(screen) }.padding(horizontal = 9.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                NativeText((screen.ordinal + 1).toString().padStart(2, '0'), 8.sp, Clay.Muted, modifier = Modifier.width(28.dp))
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
            val tabs = current !in listOf(Screen.Onboarding, Screen.Lock, Screen.Style)
            // Device-owned bars are kept outside the app content, never simulated or doubled.
            Box(Modifier.fillMaxSize().background(if (current == Screen.Scan) Clay.Dark else Clay.Cream)
                .windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                AnimatedContent(current, modifier = Modifier.fillMaxSize(), transitionSpec = {
                    val direction = if (goingBack) -1 else 1
                    // Soft, unhurried page change: the new page glides in and settles, the old one drifts away.
                    (fadeIn(tween(420, 90, SoftEase)) + slideInHorizontally(tween(520, easing = SoftEase)) { it / 10 * direction }
                        + scaleIn(tween(520, easing = SoftEase), initialScale = .985f))
                        .togetherWith(fadeOut(tween(220, easing = SoftEase)) + slideOutHorizontally(tween(520, easing = SoftEase)) { -it / 14 * direction })
                }, label = "Screen navigation") { screen ->
                    states.SaveableStateProvider(screen.name) {
                        NativeScreen(screen, ::navigate, ::menu, ::back)
                    }
                }
                if (tabs) NativeTabs(current, ::navigate, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
    }
}

@Composable
internal fun NativeScreen(screen: Screen, navigate: (Screen) -> Unit, menu: () -> Unit, back: () -> Unit) {
    when (screen) {
        Screen.Home -> HomeDashboard(navigate, menu)
        Screen.Transactions -> TransactionsScreen(menu, navigate)
        Screen.Detail -> DetailScreen(menu, back)
        Screen.Entry -> EntryScreen(menu, back)
        Screen.Budgets -> BudgetsScreen(menu, back)
        Screen.Flow -> FlowScreen(menu, back)
        Screen.Forecast -> ForecastScreen(menu, back)
        Screen.Heatmap -> HeatmapScreen(menu, back)
        Screen.Recurring -> RecurringScreen(menu, back)
        Screen.Scan -> ScanScreen(menu, back)
        Screen.Chat -> ChatScreen(menu, back, navigate)
        Screen.Health -> HealthScreen(menu, back)
        Screen.Loan -> LoanScreen(menu, back)
        Screen.Settings -> SettingsScreen(menu, navigate)
        Screen.Onboarding -> OnboardingScreen(menu) { navigate(Screen.Lock) }
        Screen.Lock -> LockScreen(menu) { navigate(Screen.Home) }
        Screen.Style -> StyleScreen(menu)
    }
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
