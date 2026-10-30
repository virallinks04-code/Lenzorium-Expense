package com.lenzorium.expenses.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lenzorium.expenses.domain.CurrencyInfo
import com.lenzorium.expenses.domain.Money

data class Slice(val label: String, val value: Long, val color: Color)

/** Donut chart with a legend listing amount and share for each slice. */
@Composable
fun DonutWithLegend(slices: List<Slice>, currency: CurrencyInfo, centerLabel: String, centerValue: String, maxLegend: Int = 6) {
    val total = slices.sumOf { it.value }.coerceAtLeast(1L)
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(132.dp)) {
                val stroke = 22.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                var start = -90f
                slices.forEach { s ->
                    val sweep = 360f * s.value / total
                    val gap = if (slices.size > 1) 1.5f else 0f
                    drawArc(s.color, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centerLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(centerValue, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val shown = slices.take(maxLegend)
            shown.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(s.color, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(s.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${(100.0 * s.value / total).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (slices.size > maxLegend) {
                val rest = slices.drop(maxLegend).sumOf { it.value }
                Text("+${slices.size - maxLegend} more · ${Money.format(rest, currency)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Horizontal bars, one per row, scaled to the largest value. */
@Composable
fun HorizontalBars(rows: List<Slice>, currency: CurrencyInfo) {
    val max = rows.maxOfOrNull { it.value }?.coerceAtLeast(1L) ?: 1L
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { r ->
            Column {
                Row {
                    Text(r.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(Money.format(r.value, currency), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(10.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(5.dp))) {
                    Box(
                        Modifier
                            .fillMaxWidth((r.value.toFloat() / max).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(r.color, RoundedCornerShape(5.dp))
                    )
                }
            }
        }
    }
}

/** Vertical bar trend chart (e.g. daily spending). Labels are shown for a subset of bars. */
@Composable
fun TrendBars(values: List<Long>, labels: List<String>, color: Color, modifier: Modifier = Modifier) {
    val max = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = (slot * 0.65f).coerceAtMost(28.dp.toPx())
            for (i in 0..3) {
                val y = size.height * i / 3f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            values.forEachIndexed { i, v ->
                val h = size.height * v / max
                if (v > 0) {
                    drawRoundRect(
                        color,
                        topLeft = Offset(i * slot + (slot - barW) / 2, size.height - h),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            val step = when {
                labels.size <= 8 -> 1
                labels.size <= 16 -> 2
                labels.size <= 31 -> 5
                else -> (labels.size / 6).coerceAtLeast(1)
            }
            labels.forEachIndexed { i, l ->
                Text(
                    if (i % step == 0) l else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Two side-by-side bars comparing income and expenses. */
@Composable
fun IncomeVsExpense(income: Long, expense: Long, incomeColor: Color, expenseColor: Color, currency: CurrencyInfo) {
    HorizontalBars(
        listOf(Slice("Income", income, incomeColor), Slice("Expenses", expense, expenseColor)),
        currency,
    )
    Spacer(Modifier.height(8.dp))
    val net = income - expense
    Text(
        "Net: ${Money.formatSigned(net, currency)}",
        style = MaterialTheme.typography.titleSmall,
        color = if (net >= 0) incomeColor else expenseColor,
        modifier = Modifier.padding(top = 2.dp),
    )
}
