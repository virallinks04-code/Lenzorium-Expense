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

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6E4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F0D8),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF4A635A),
    secondaryContainer = Color(0xFFCCE8DC),
    onSecondaryContainer = Color(0xFF062019),
    tertiary = Color(0xFF3E6374),
    tertiaryContainer = Color(0xFFC1E8FB),
    background = Color(0xFFF6F8F7),
    onBackground = Color(0xFF181D1B),
    surface = Color(0xFFF6F8F7),
    onSurface = Color(0xFF181D1B),
    surfaceVariant = Color(0xFFDCE5E0),
    onSurfaceVariant = Color(0xFF404944),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F4F2),
    surfaceContainer = Color(0xFFEAEFEC),
    surfaceContainerHigh = Color(0xFFE4EAE7),
    surfaceContainerHighest = Color(0xFFDEE4E1),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFC0C9C3),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FD8B0),
    onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF005139),
    onPrimaryContainer = Color(0xFFB7F0D8),
    secondary = Color(0xFFB0CCC0),
    secondaryContainer = Color(0xFF334B43),
    onSecondaryContainer = Color(0xFFCCE8DC),
    tertiary = Color(0xFFA5CCDF),
    tertiaryContainer = Color(0xFF254B5B),
    background = Color(0xFF0F1413),
    onBackground = Color(0xFFDEE4E1),
    surface = Color(0xFF0F1413),
    onSurface = Color(0xFFDEE4E1),
    surfaceVariant = Color(0xFF404944),
    onSurfaceVariant = Color(0xFFC0C9C3),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF171D1B),
    surfaceContainer = Color(0xFF1B211F),
    surfaceContainerHigh = Color(0xFF252B29),
    surfaceContainerHighest = Color(0xFF303634),
    outline = Color(0xFF8A938D),
    outlineVariant = Color(0xFF404944),
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
            warning = Color(0xFFFFB74D), heroStart = Color(0xFF0E5A42), heroEnd = Color(0xFF123A4A), onHero = Color.White,
        )
    } else {
        MoneyColors(
            expense = Color(0xFFC62828), income = Color(0xFF0B7A4B), loan = Color(0xFFB26A00),
            warning = Color(0xFFE65100), heroStart = Color(0xFF0B6E4F), heroEnd = Color(0xFF1F5468), onHero = Color.White,
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
