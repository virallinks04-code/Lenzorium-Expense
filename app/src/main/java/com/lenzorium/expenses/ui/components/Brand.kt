package com.lenzorium.expenses.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lenzorium.expenses.R

/** The Lenzorium logo (same artwork as the launcher icon) for welcome and lock screens. */
@Composable
fun BrandMark(size: Int = 88, modifier: Modifier = Modifier) {
    Image(
        painterResource(R.drawable.brand_icon),
        contentDescription = "Lenzorium Expenses",
        modifier = modifier.size(size.dp),
    )
}

/** "Lenzorium / — EXPENSES —" wordmark in the logo's navy and blue. */
@Composable
fun BrandWordmark(large: Boolean = true) {
    val dark = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() < 0.3f
    val navy = if (dark) androidx.compose.ui.graphics.Color(0xFFE2E8F2) else androidx.compose.ui.graphics.Color(0xFF002058)
    val blue = if (dark) androidx.compose.ui.graphics.Color(0xFF7DC4FF) else androidx.compose.ui.graphics.Color(0xFF0078D0)
    androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        androidx.compose.material3.Text(
            "Lenzorium",
            style = androidx.compose.material3.MaterialTheme.typography.let { if (large) it.headlineLarge else it.headlineSmall },
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = navy,
        )
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(width = 22.dp, height = 2.dp)
                    .then(Modifier.background(blue.copy(alpha = 0.6f)))
            )
            androidx.compose.material3.Text(
                "EXPENSES",
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                letterSpacing = (if (large) 5 else 4).sp,
                color = blue,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            androidx.compose.foundation.layout.Box(
                Modifier.size(width = 22.dp, height = 2.dp)
                    .then(Modifier.background(blue.copy(alpha = 0.6f)))
            )
        }
    }
}
