package com.lenzorium.expenses.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lenzorium.expenses.domain.BudgetLevel
import com.lenzorium.expenses.ui.theme.LocalMoneyColors

/** Colour for a budget level. Text labels always accompany it, so colour is never the only signal. */
@Composable
fun budgetColor(level: BudgetLevel): Color = when (level) {
    BudgetLevel.OK -> MaterialTheme.colorScheme.primary
    BudgetLevel.NEAR -> LocalMoneyColors.current.warning
    BudgetLevel.OVER -> LocalMoneyColors.current.expense
}

/** Circular progress with a label in the middle. [fraction] above 1 draws a full ring. */
@Composable
fun ProgressRing(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    stroke: Dp = 10.dp,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    centerTop: String? = null,
    centerBottom: String? = null,
) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(250), label = "ring")
    Box(modifier.size(size).semantics { contentDescription = "${(fraction * 100).toInt()} percent" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val inset = w / 2
            val arc = Size(this.size.width - w, this.size.height - w)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
            if (f > 0f) drawArc(color, -90f, 360f * f, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            centerTop?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            centerBottom?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** Thin rounded progress bar. */
@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(250), label = "bar")
    Box(
        modifier.fillMaxWidth().height(height)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(50))
    ) {
        if (f > 0f) Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(color, RoundedCornerShape(50)))
    }
}
