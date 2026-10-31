package com.lenzorium.expenses.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lenzorium.expenses.data.Category
import com.lenzorium.expenses.domain.CurrencyInfo

/** Large, centred amount entry used at the top of every money form. */
@Composable
fun HeroAmountInput(
    value: String,
    onChange: (String) -> Unit,
    currency: CurrencyInfo,
    accent: Color,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(
                currency.code,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp, top = 6.dp),
            )
            BasicTextField(
                value = value,
                onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' || it == ',' }.take(16)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.displayMedium.copy(color = accent, textAlign = TextAlign.Start, fontWeight = FontWeight.Bold),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(keyboardType = if (currency.decimals == 0) KeyboardType.Number else KeyboardType.Decimal),
                modifier = Modifier.widthIn(min = 32.dp, max = 280.dp).width(IntrinsicSize.Min),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text("0", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        inner()
                    }
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(
            Modifier.fillMaxWidth(0.6f),
            thickness = 2.dp,
            color = if (isError) MaterialTheme.colorScheme.error else accent.copy(alpha = 0.35f),
        )
    }
}

/** Category selector as a grid of round icons (like a phone's app drawer). */
@Composable
fun CategoryGrid(categories: List<Category>, selectedId: Long?, onSelect: (Long) -> Unit, onAddCustom: (() -> Unit)? = null) {
    Column {
        Text("Category", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val perRow = if (maxWidth < 330.dp) 4 else 5
            val cell = maxWidth / perRow
            FlowRow(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                categories.forEach { c ->
                    CategoryCell(cell, c.name, CategoryIcons.of(c.iconKey), Color(c.colorArgb), c.id == selectedId) { onSelect(c.id) }
                }
                if (onAddCustom != null) {
                    CategoryCell(cell, "New", Icons.Rounded.Add, MaterialTheme.colorScheme.outline, false, dashed = true, onClick = onAddCustom)
                }
            }
        }
    }
}

@Composable
private fun CategoryCell(
    width: androidx.compose.ui.unit.Dp,
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    selected: Boolean,
    dashed: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        Modifier.width(width).clickable(onClick = onClick).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .then(if (selected) Modifier.border(2.dp, color, CircleShape).padding(4.dp) else Modifier.padding(4.dp))
                .background(if (selected) color else if (dashed) MaterialTheme.colorScheme.surfaceContainerHigh else color.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (selected) Color.White else color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.sp),
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 1.dp),
        )
    }
}

/** Save button pinned to the bottom of a form, above the keyboard. */
@Composable
fun FormSaveBar(label: String, saving: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
        Column(Modifier.navigationBarsPadding().imePadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Button(
                onClick = onClick,
                enabled = enabled && !saving,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(52.dp),
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(label, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Asks before throwing away unsaved form input when the user goes back. Returns a back action for the top bar. */
@Composable
fun rememberDiscardGuard(dirty: Boolean, onLeave: () -> Unit): () -> Unit {
    var ask by remember { mutableStateOf(false) }
    BackHandler(enabled = dirty) { ask = true }
    if (ask) {
        ConfirmDialog(
            title = "Discard changes?",
            message = "What you've entered on this screen hasn't been saved.",
            confirmLabel = "Discard",
            destructive = true,
            onConfirm = { ask = false; onLeave() },
            onDismiss = { ask = false },
        )
    }
    return { if (dirty) ask = true else onLeave() }
}

/** Small rounded section used to group related form fields. */
@Composable
fun FormSection(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.large)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}
