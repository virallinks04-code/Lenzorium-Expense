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
import androidx.compose.ui.text.font.FontWeight
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

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
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
