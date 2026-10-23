@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.pingpaisa.app

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

// Exact Figma-resolved faces are bundled, so typography also works offline.
internal val PingPaisaFont = FontFamily(
    listOf(400, 500, 600, 700, 800).map { weight ->
        Font(
            resId = R.font.manrope,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight))
        )
    }
)

internal val PingPaisaMath = FontFamily(Font(R.font.noto_math))
internal val PingPaisaSymbols2 = FontFamily(Font(R.font.noto_symbols2))
internal val PingPaisaSymbols = FontFamily(Font(R.font.noto_symbols))
internal val PingPaisaNoto = FontFamily(Font(R.font.noto_sans))

private fun TextStyle.withManrope() = copy(
    fontFamily = PingPaisaFont,
    fontSynthesis = FontSynthesis.None,
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

internal val PingPaisaTypography = Typography().run {
    copy(
        displayLarge = displayLarge.withManrope(),
        displayMedium = displayMedium.withManrope(),
        displaySmall = displaySmall.withManrope(),
        headlineLarge = headlineLarge.withManrope(),
        headlineMedium = headlineMedium.withManrope(),
        headlineSmall = headlineSmall.withManrope(),
        titleLarge = titleLarge.withManrope(),
        titleMedium = titleMedium.withManrope(),
        titleSmall = titleSmall.withManrope(),
        bodyLarge = bodyLarge.withManrope(),
        bodyMedium = bodyMedium.withManrope(),
        bodySmall = bodySmall.withManrope(),
        labelLarge = labelLarge.withManrope(),
        labelMedium = labelMedium.withManrope(),
        labelSmall = labelSmall.withManrope()
    )
}
