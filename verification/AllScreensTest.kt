package com.pingpaisa.app

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test
import org.junit.BeforeClass
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

// Installed in a temporary project's test source set by tools/verify_visuals.py.
@RunWith(Parameterized::class)
class AllScreensTest(
    screenName: String, width: Int, height: Int, fontScale: Float
) {
    private val screen = Screen.valueOf(screenName)
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig(
            screenWidth = width, screenHeight = height, xdpi = 160, ydpi = 160,
            density = Density.MEDIUM, softButtons = false, fontScale = fontScale,
            orientation = if (width > height) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT
        ),
        theme = "android:style/Theme.Material.Light.NoActionBar",
        showSystemUi = false,
        useDeviceResolution = true
    )

    @Test fun render() { paparazzi.snapshot(screen.name) { PingPaisa(screen) } }

    companion object {
        @JvmStatic @BeforeClass fun desktopXmlCompatibility() { XmlParserCompatibility.install() }
        @JvmStatic
        @Parameterized.Parameters(name = "{0}-{1}x{2}-font{3}")
        fun screens(): List<Array<Any>> = Screen.entries.map {
            arrayOf<Any>(it.name, 440, 868, 1f)
        } + listOf(
            arrayOf<Any>("Home", 320, 480, 1f),
            arrayOf<Any>("Chat", 844, 302, 1f),
            arrayOf<Any>("Detail", 360, 712, 1.5f)
        )
    }
}
