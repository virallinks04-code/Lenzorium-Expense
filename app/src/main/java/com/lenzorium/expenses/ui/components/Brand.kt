package com.lenzorium.expenses.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.lenzorium.expenses.R

/** The app icon (same artwork as the launcher icon) for welcome and lock screens. */
@Composable
fun BrandMark(size: Int = 88, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.26f).dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0E7A57), Color(0xFF0A4D5C)))),
    ) {
        // The foreground artwork is drawn for a 108dp canvas with a 72dp visible area; scale it up to fill.
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = 1.5f, scaleY = 1.5f),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
    }
}
