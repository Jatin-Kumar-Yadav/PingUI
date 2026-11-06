package com.pingpaisa.app

import com.pingpaisa.app.data.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupGateTest {

    private fun ready(lock: Boolean = true) = AccountState.Ready(
        UserProfile(name = "Janvi", monthlySalary = 100000.0, passwordHash = "h", passwordSalt = "s", appLockEnabled = lock)
    )

    @Test fun loadingShowsNothingYet() {
        assertNull(gateScreen(AccountState.Loading, unlocked = false, requested = Screen.Home))
    }

    @Test fun noAccountAllowsOnlySetupScreens() {
        assertEquals(Screen.Onboarding, gateScreen(AccountState.NoAccount, false, Screen.Home))
        assertEquals(Screen.Onboarding, gateScreen(AccountState.NoAccount, false, Screen.Settings))
        assertEquals(Screen.Onboarding, gateScreen(AccountState.NoAccount, false, Screen.Lock))
        assertEquals(Screen.SignUp, gateScreen(AccountState.NoAccount, false, Screen.SignUp))
        assertEquals(Screen.NotificationAccess, gateScreen(AccountState.NoAccount, false, Screen.NotificationAccess))
    }

    @Test fun lockedAccountAlwaysGetsLogin() {
        // Including a "Home" restored by Android after the app was killed.
        for (screen in Screen.entries) assertEquals(Screen.Lock, gateScreen(ready(), unlocked = false, requested = screen))
    }

    @Test fun unlockedAccountCanUseTheApp() {
        assertEquals(Screen.Home, gateScreen(ready(), true, Screen.Home))
        assertEquals(Screen.Settings, gateScreen(ready(), true, Screen.Settings))
        assertEquals(Screen.NotificationAccess, gateScreen(ready(), true, Screen.NotificationAccess))
    }

    @Test fun signUpAndOnboardingAreGoneOnceAnAccountExists() {
        assertEquals(Screen.Home, gateScreen(ready(), true, Screen.SignUp))
        assertEquals(Screen.Home, gateScreen(ready(), true, Screen.Onboarding))
    }

    @Test fun appLockOffSkipsLogin() {
        assertEquals(Screen.Home, gateScreen(ready(lock = false), unlocked = false, requested = Screen.Home))
    }

    @Test fun drawerHidesSetupLoginAndStyleButKeepsTheRealScreens() {
        for (hidden in listOf(Screen.Onboarding, Screen.SignUp, Screen.Lock, Screen.Style))
            assertTrue("$hidden should be hidden", hidden in drawerHiddenScreens)
        val visible = Screen.entries.filter { it !in drawerHiddenScreens }
        assertTrue(Screen.Home in visible && Screen.Transactions in visible && Screen.Settings in visible)
        assertTrue(Screen.NotificationAccess in visible)   // until Settings has its own row
        assertEquals(Screen.entries.size - 4, visible.size)
    }

    @Test fun noMenuButtonBeforeSignIn() {
        assertEquals(setOf(Screen.Onboarding, Screen.SignUp, Screen.Lock), noMenuScreens)
        assertFalse(Screen.Home in noMenuScreens)
    }

    @Test fun backLeavesTheAppFromRootScreens() {
        assertTrue(isRootScreen(AccountState.NoAccount, false, Screen.Onboarding))
        assertTrue(isRootScreen(ready(), false, Screen.Lock))
        assertTrue(isRootScreen(ready(), true, Screen.Home))
        assertFalse(isRootScreen(ready(), true, Screen.Settings))
        assertFalse(isRootScreen(ready(), true, Screen.Lock))   // Lock opened from Settings: Back returns
    }
}
