package com.roadsearch.openeditvideo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.roadsearch.openeditvideo.R

/** Bebas Neue: bold headers, stylised titles, timecodes and numbers. */
val BebasNeue = FontFamily(Font(R.font.bebas_neue_regular, FontWeight.Normal))

/** Inter (variable font): utility controls, sub-texts, values. */
val Inter = FontFamily(
    listOf(
        FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold,
    ).map { w ->
        Font(
            resId = R.font.inter_variable,
            weight = w,
            variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)),
        )
    },
)

private fun inter(size: Int, weight: FontWeight, line: Int = size + 6) =
    TextStyle(fontFamily = Inter, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

private fun bebas(size: Int, spacing: Float = 0.5f) =
    TextStyle(fontFamily = BebasNeue, fontWeight = FontWeight.Normal, fontSize = size.sp, lineHeight = (size + 4).sp, letterSpacing = spacing.sp)

internal val MotionTypography = Typography(
    displayLarge = bebas(56), displayMedium = bebas(44), displaySmall = bebas(36),
    headlineLarge = bebas(32), headlineMedium = bebas(28), headlineSmall = bebas(24),
    titleLarge = inter(20, FontWeight.SemiBold),
    titleMedium = inter(16, FontWeight.SemiBold),
    titleSmall = inter(14, FontWeight.Medium),
    bodyLarge = inter(16, FontWeight.Normal),
    bodyMedium = inter(14, FontWeight.Normal),
    bodySmall = inter(12, FontWeight.Normal),
    labelLarge = inter(14, FontWeight.Medium),
    labelMedium = inter(12, FontWeight.Medium),
    labelSmall = inter(11, FontWeight.Medium),
)
