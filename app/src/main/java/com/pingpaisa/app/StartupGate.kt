package com.pingpaisa.app

/** Screens a person can be on before they have an account. */
internal val setupScreens = setOf(Screen.Onboarding, Screen.SignUp, Screen.NotificationAccess)

/**
 * Screens left out of the ☰ drawer once the app is running with an account: Onboarding and
 * Sign up are first-run only, Lock is the login (the gate shows it by itself), and the Style
 * Guide is a design reference. Notification access stays listed until Settings gets its own
 * row for it (step 52). Previews and screenshot tests still list everything.
 */
internal val drawerHiddenScreens = setOf(Screen.Onboarding, Screen.SignUp, Screen.Lock, Screen.Style)

/** Screens that show no ☰ menu button at all: the person isn't signed in yet. */
internal val noMenuScreens = setOf(Screen.Onboarding, Screen.SignUp, Screen.Lock)

/**
 * Decides which screen may actually be shown, given the account state, whether
 * the user has unlocked the app in this session, and the screen navigation
 * asked for ([requested]). Returns null while the account is still loading
 * (the app shows a brief splash instead, so sign-up never flashes for a
 * registered user).
 *
 *  - No account yet       → only the setup screens; anything else goes to Onboarding.
 *  - Account, app lock on, not unlocked → always the login screen, whatever was
 *    restored or requested (so a restored "Home" cannot skip the password).
 *  - Account, unlocked or lock off → anything except the setup-only screens
 *    Onboarding and Sign up, which go to Home.
 *
 * Kept free of Compose so it can be unit-tested.
 */
internal fun gateScreen(account: AccountState, unlocked: Boolean, requested: Screen): Screen? = when (account) {
    AccountState.Loading -> null
    AccountState.NoAccount -> if (requested in setupScreens) requested else Screen.Onboarding
    is AccountState.Ready -> when {
        account.profile.appLockEnabled && !unlocked -> Screen.Lock
        requested == Screen.Onboarding || requested == Screen.SignUp -> Screen.Home
        else -> requested
    }
}

/**
 * The screen where system Back should leave the app instead of navigating:
 * Home normally, Onboarding before sign-up, the login screen while locked.
 */
internal fun isRootScreen(account: AccountState, unlocked: Boolean, shown: Screen): Boolean = when (account) {
    AccountState.NoAccount -> shown == Screen.Onboarding
    is AccountState.Ready -> shown == Screen.Home || (shown == Screen.Lock && account.profile.appLockEnabled && !unlocked)
    AccountState.Loading -> true
}
