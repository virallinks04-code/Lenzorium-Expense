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

// Lenzorium palette (Professional Upgrade proposal): light blue + white + deep navy, a little gold.
// Primary is a slightly deeper shade of the brand blue #4A9EDB so white text on buttons stays readable (WCAG AA).
object Brand {
    val Blue = Color(0xFF4A9EDB)
    val BlueDeep = Color(0xFF1C74BC)
    val SoftBlue = Color(0xFFEAF5FC)
    val Navy = Color(0xFF102A45)
    val Gold = Color(0xFFCCA34C)
    val Positive = Color(0xFF1E9185)
    val Warning = Color(0xFFB94B51)
}

private val LightColors = lightColorScheme(
    primary = Brand.BlueDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EBFA),
    onPrimaryContainer = Brand.Navy,
    secondary = Color(0xFF3F5F80),
    onSecondary = Color.White,
    secondaryContainer = Brand.SoftBlue,
    onSecondaryContainer = Brand.Navy,
    tertiary = Brand.Gold,
    onTertiary = Color(0xFF2B1F00),
    tertiaryContainer = Color(0xFFF6EBCF),
    onTertiaryContainer = Color(0xFF3A2C00),
    background = Color(0xFFF5F9FD),
    onBackground = Brand.Navy,
    surface = Color(0xFFF5F9FD),
    onSurface = Brand.Navy,
    surfaceVariant = Color(0xFFE1EAF3),
    onSurfaceVariant = Color(0xFF4F6278),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F6FB),
    surfaceContainer = Color(0xFFEAF2F9),
    surfaceContainerHigh = Color(0xFFE3ECF5),
    surfaceContainerHighest = Color(0xFFDCE6F0),
    outline = Color(0xFF7D8DA0),
    outlineVariant = Color(0xFFCCD8E4),
    inverseSurface = Brand.Navy,
    inverseOnSurface = Color(0xFFEAF2F9),
    inversePrimary = Color(0xFF9CCDF2),
    error = Brand.Warning,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CC6F0),
    onPrimary = Color(0xFF00324F),
    primaryContainer = Color(0xFF0E4C78),
    onPrimaryContainer = Color(0xFFD6EBFA),
    secondary = Color(0xFFB4C8DE),
    onSecondary = Color(0xFF1E3348),
    secondaryContainer = Color(0xFF1F3953),
    onSecondaryContainer = Color(0xFFDCEAF6),
    tertiary = Color(0xFFE3C278),
    onTertiary = Color(0xFF3D2E00),
    tertiaryContainer = Color(0xFF574400),
    onTertiaryContainer = Color(0xFFF6E2AE),
    background = Color(0xFF0A1625),
    onBackground = Color(0xFFE3EBF4),
    surface = Color(0xFF0A1625),
    onSurface = Color(0xFFE3EBF4),
    surfaceVariant = Color(0xFF34465A),
    onSurfaceVariant = Color(0xFFB7C6D6),
    surfaceContainerLowest = Color(0xFF07111D),
    surfaceContainerLow = Color(0xFF0F1D2E),
    surfaceContainer = Color(0xFF132336),
    surfaceContainerHigh = Color(0xFF1A2B40),
    surfaceContainerHighest = Color(0xFF23364C),
    outline = Color(0xFF8798AB),
    outlineVariant = Color(0xFF31445A),
    inverseSurface = Color(0xFFE3EBF4),
    inverseOnSurface = Brand.Navy,
    inversePrimary = Brand.BlueDeep,
    error = Color(0xFFF2A3A7),
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
    large = RoundedCornerShape(20.dp),
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
            expense = Color(0xFFF2A3A7), income = Color(0xFF6FD1C4), loan = Color(0xFFE3C278),
            warning = Color(0xFFFFB74D), heroStart = Color(0xFF1E6FB0), heroEnd = Color(0xFF0B1E33), onHero = Color.White,
        )
    } else {
        MoneyColors(
            expense = Brand.Warning, income = Brand.Positive, loan = Color(0xFF9A7420),
            warning = Color(0xFFC0661A), heroStart = Color(0xFF3A8FD3), heroEnd = Brand.Navy, onHero = Color.White,
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
