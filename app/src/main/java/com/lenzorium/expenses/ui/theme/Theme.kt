package com.lenzorium.expenses.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.lenzorium.expenses.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lenzorium.expenses.data.ThemeMode

@Immutable
data class MoneyColors(
    val expense: Color,
    val income: Color,
    val loan: Color,
    val warning: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val onHero: Color,
)

val LocalMoneyColors = staticCompositionLocalOf {
    MoneyColors(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.DarkGray, Color.Gray, Color.White)
}

// Lenzorium brand palette, taken from the logo: royal blue, navy wordmark, sky-blue accents.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0A63C9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E6FF),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = Color(0xFF3A5A86),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE8F8),
    onSecondaryContainer = Color(0xFF0B2545),
    tertiary = Color(0xFF0090E0),
    tertiaryContainer = Color(0xFFCDEBFF),
    background = Color(0xFFF4F7FC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF4F7FC),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFDFE7F2),
    onSurfaceVariant = Color(0xFF475467),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEFF4FA),
    surfaceContainer = Color(0xFFE9EFF7),
    surfaceContainerHigh = Color(0xFFE2E9F3),
    surfaceContainerHighest = Color(0xFFDBE3EE),
    outline = Color(0xFF7A8597),
    outlineVariant = Color(0xFFC8D1DE),
    inverseSurface = Color(0xFF0E1E3A),
    inversePrimary = Color(0xFF8CC0FF),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82B8FF),
    onPrimary = Color(0xFF00305F),
    primaryContainer = Color(0xFF004A90),
    onPrimaryContainer = Color(0xFFD7E6FF),
    secondary = Color(0xFFB5C7E3),
    onSecondary = Color(0xFF1F3350),
    secondaryContainer = Color(0xFF233756),
    onSecondaryContainer = Color(0xFFDCE8F8),
    tertiary = Color(0xFF7DD0FF),
    tertiaryContainer = Color(0xFF004C70),
    background = Color(0xFF0A111E),
    onBackground = Color(0xFFE2E8F2),
    surface = Color(0xFF0A111E),
    onSurface = Color(0xFFE2E8F2),
    surfaceVariant = Color(0xFF364256),
    onSurfaceVariant = Color(0xFFB8C3D3),
    surfaceContainerLowest = Color(0xFF060C17),
    surfaceContainerLow = Color(0xFF101929),
    surfaceContainer = Color(0xFF152033),
    surfaceContainerHigh = Color(0xFF1C283D),
    surfaceContainerHighest = Color(0xFF263349),
    outline = Color(0xFF8792A4),
    outlineVariant = Color(0xFF334057),
    inverseSurface = Color(0xFFE2E8F2),
    inversePrimary = Color(0xFF0A63C9),
    error = Color(0xFFFFB4AB),
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Tabular figures so amounts line up in columns. */
private fun style(size: Int, weight: FontWeight, line: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Inter,
    fontSize = size.sp,
    fontWeight = weight,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
    fontFeatureSettings = "tnum",
)

private val AppTypography = Typography(
    displayLarge = style(44, FontWeight.Bold, 52, -1.0),
    displayMedium = style(38, FontWeight.Bold, 46, -0.8),
    displaySmall = style(32, FontWeight.Bold, 40, -0.6),
    headlineLarge = style(28, FontWeight.Bold, 36, -0.4),
    headlineMedium = style(24, FontWeight.Bold, 32, -0.3),
    headlineSmall = style(21, FontWeight.SemiBold, 28, -0.2),
    titleLarge = style(19, FontWeight.SemiBold, 26, -0.1),
    titleMedium = style(16, FontWeight.SemiBold, 22),
    titleSmall = style(14, FontWeight.SemiBold, 20),
    bodyLarge = style(16, FontWeight.Normal, 24),
    bodyMedium = style(14, FontWeight.Normal, 20),
    bodySmall = style(12, FontWeight.Normal, 16, 0.1),
    labelLarge = style(14, FontWeight.SemiBold, 20, 0.1),
    labelMedium = style(12, FontWeight.Medium, 16, 0.2),
    labelSmall = style(11, FontWeight.Medium, 16, 0.4),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun isAppDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun LenzoriumTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = isAppDark(mode)
    val money = if (dark) {
        MoneyColors(
            expense = Color(0xFFFF8A80), income = Color(0xFF6FD8B0), loan = Color(0xFFFFC66D),
            warning = Color(0xFFFFB74D), heroStart = Color(0xFF0B4FA6), heroEnd = Color(0xFF07214D), onHero = Color.White,
        )
    } else {
        MoneyColors(
            expense = Color(0xFFC62828), income = Color(0xFF0B7A4B), loan = Color(0xFFB26A00),
            warning = Color(0xFFE65100), heroStart = Color(0xFF0A6BD6), heroEnd = Color(0xFF002A6B), onHero = Color.White,
        )
    }
    CompositionLocalProvider(LocalMoneyColors provides money) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
