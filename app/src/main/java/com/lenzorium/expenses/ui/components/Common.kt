package com.lenzorium.expenses.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lenzorium.expenses.data.Category
import com.lenzorium.expenses.data.PayMethod
import com.lenzorium.expenses.data.Txn
import com.lenzorium.expenses.data.TxnType
import com.lenzorium.expenses.domain.CurrencyInfo
import com.lenzorium.expenses.domain.LedgerMath
import com.lenzorium.expenses.domain.Money
import com.lenzorium.expenses.ui.theme.LocalMoneyColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// ---------- Dates ----------

object Dates {
    val zone: ZoneId get() = ZoneId.systemDefault()
    private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy")
    private val shortFmt = DateTimeFormatter.ofPattern("d MMM")
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
    private val dayHeaderFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

    fun localDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    fun localDateTime(millis: Long): LocalDateTime = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
    fun startOf(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()
    fun date(millis: Long): String = localDate(millis).format(dateFmt)
    fun date(d: LocalDate): String = d.format(dateFmt)
    fun short(d: LocalDate): String = d.format(shortFmt)
    fun time(millis: Long): String = localDateTime(millis).format(timeFmt)
    fun dateTime(millis: Long): String = "${date(millis)}, ${time(millis)}"
    fun dayHeader(d: LocalDate): String = when (d) {
        LocalDate.now() -> "Today"
        LocalDate.now().minusDays(1) -> "Yesterday"
        else -> d.format(dayHeaderFmt)
    }
    fun epochDay(d: Long): String = date(LocalDate.ofEpochDay(d))
}

// ---------- Icons ----------

object CategoryIcons {
    val choices: List<Pair<String, ImageVector>> = listOf(
        "food" to Icons.Rounded.Restaurant,
        "groceries" to Icons.Rounded.LocalGroceryStore,
        "transport" to Icons.Rounded.DirectionsBus,
        "shopping" to Icons.Rounded.ShoppingBag,
        "bills" to Icons.Rounded.Receipt,
        "rent" to Icons.Rounded.Home,
        "health" to Icons.Rounded.LocalHospital,
        "education" to Icons.Rounded.School,
        "entertainment" to Icons.Rounded.Movie,
        "other" to Icons.Rounded.Category,
        "salary" to Icons.Rounded.Work,
        "business" to Icons.Rounded.Store,
        "freelance" to Icons.Rounded.Laptop,
        "gift" to Icons.Rounded.CardGiftcard,
        "other_income" to Icons.Rounded.AttachMoney,
        "cafe" to Icons.Rounded.LocalCafe,
        "fuel" to Icons.Rounded.LocalGasStation,
        "phone" to Icons.Rounded.PhoneAndroid,
        "internet" to Icons.Rounded.Wifi,
        "pets" to Icons.Rounded.Pets,
        "fitness" to Icons.Rounded.FitnessCenter,
        "travel" to Icons.Rounded.Flight,
        "clothes" to Icons.Rounded.Checkroom,
        "kids" to Icons.Rounded.ChildCare,
        "savings" to Icons.Rounded.Savings,
        "charity" to Icons.Rounded.VolunteerActivism,
        "beauty" to Icons.Rounded.Spa,
        "repair" to Icons.Rounded.Build,
    )
    private val map = choices.toMap()
    fun of(key: String): ImageVector = map[key] ?: Icons.Rounded.Category

    val colorChoices: List<Long> = listOf(
        0xFFE4572EL, 0xFF2E9E5BL, 0xFF2D7DD2L, 0xFFD64FA1L, 0xFFF2A007L, 0xFF7353BAL,
        0xFFD7263DL, 0xFF1B998BL, 0xFFFF7F11L, 0xFF6C757DL, 0xFF0B6E4FL, 0xFF8D6E63L,
    )
}

fun txnIcon(t: Txn, cat: Category?): ImageVector = when (t.type) {
    TxnType.SAFE_GIVEN -> Icons.Rounded.Handshake
    TxnType.SAFE_RETURNED -> Icons.Rounded.AssignmentReturned
    TxnType.EXPENSE, TxnType.INCOME -> cat?.let { CategoryIcons.of(it.iconKey) } ?: Icons.Rounded.Category
    TxnType.LOAN_RECEIVED -> Icons.Rounded.AccountBalance
    TxnType.LOAN_REPAYMENT -> Icons.Rounded.Payments
    TxnType.LOAN_CHARGE -> Icons.Rounded.RequestQuote
    TxnType.TRANSFER -> Icons.Rounded.SwapHoriz
}

fun txnTitle(t: Txn, cat: Category?, person: String? = null): String = when (t.type) {
    TxnType.SAFE_GIVEN -> "Given to ${person ?: "someone"}"
    TxnType.SAFE_RETURNED -> "Back from ${person ?: "someone"}"
    TxnType.EXPENSE, TxnType.INCOME -> t.party.ifBlank { cat?.name ?: t.type.label }
    TxnType.LOAN_RECEIVED -> "Loan from ${t.party}"
    TxnType.LOAN_REPAYMENT -> "Repaid to ${t.party}"
    TxnType.LOAN_CHARGE -> "Loan charge · ${t.party}"
    TxnType.TRANSFER -> t.notes.ifBlank { "Transfer" }
}

@Composable
fun txnColor(type: TxnType): Color {
    val m = LocalMoneyColors.current
    return when (type) {
        TxnType.EXPENSE -> m.expense
        TxnType.INCOME -> m.income
        TxnType.TRANSFER -> MaterialTheme.colorScheme.primary
        TxnType.SAFE_GIVEN, TxnType.SAFE_RETURNED -> MaterialTheme.colorScheme.tertiary
        else -> m.loan
    }
}

/** Signed amount text as it affects available cash ("debt +" for charges). */
fun txnAmountText(t: Txn, c: CurrencyInfo, toCurrency: CurrencyInfo? = null): String =
    when (t.type) {
        TxnType.LOAN_CHARGE -> "Debt +${Money.number(t.amountMinor, c.decimals)} ${c.code}"
        TxnType.TRANSFER ->
            if (toCurrency != null && toCurrency.code != c.code) "${Money.format(t.amountMinor, c)} → ${Money.format(LedgerMath.received(t), toCurrency)}"
            else Money.format(t.amountMinor, c)
        // The amount's own sign, in the currency of the account it belongs to.
        else -> Money.formatSigned(LedgerMath.cashDelta(t.copy(accountId = null)), c)
    }

// ---------- Building blocks ----------

@Composable
fun IconBadge(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Int = 40) {
    Box(
        modifier
            .size(size.dp)
            .background(tint.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
fun TxnRow(t: Txn, cat: Category?, currency: CurrencyInfo, onClick: () -> Unit, showDate: Boolean = false, account: String? = null, toCurrency: CurrencyInfo? = null, person: String? = null) {
    val tint = when (t.type) {
        TxnType.EXPENSE, TxnType.INCOME -> cat?.let { Color(it.colorArgb) } ?: txnColor(t.type)
        TxnType.TRANSFER -> MaterialTheme.colorScheme.primary
        TxnType.SAFE_GIVEN, TxnType.SAFE_RETURNED -> MaterialTheme.colorScheme.tertiary
        else -> LocalMoneyColors.current.loan
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(txnIcon(t, cat), tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(txnTitle(t, cat, person), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val parts = buildList {
                if (t.type != TxnType.TRANSFER) add(if (t.type.isLoanRelated || t.type.isSafekeeping) (if (t.type.isSafekeeping) "Safekeeping" else t.type.label) else cat?.name ?: t.type.label)
                (account ?: t.paymentMethod?.label)?.let { add(it) }
                add(if (showDate) Dates.date(t.timestamp) else Dates.time(t.timestamp))
            }
            Text(
                parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            txnAmountText(t, currency, toCurrency).replace(" → ", "\n→ "),
            style = MaterialTheme.typography.titleSmall,
            color = txnColor(t.type),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) { content() }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(icon, MaterialTheme.colorScheme.primary, size = 56)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(12.dp)); action()
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface, caption: String? = null) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (caption != null) Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun MessageDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

// ---------- Form fields ----------

@Composable
fun AmountField(value: String, onChange: (String) -> Unit, currency: CurrencyInfo, modifier: Modifier = Modifier, label: String = "Amount", isError: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' || it == ',' }) },
        label = { Text(label) },
        prefix = { Text("${currency.code} ") },
        singleLine = true,
        isError = isError,
        textStyle = MaterialTheme.typography.headlineSmall,
        keyboardOptions = KeyboardOptions(keyboardType = if (currency.decimals == 0) KeyboardType.Number else KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun TextInput(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, singleLine: Boolean = true, placeholder: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun PayMethodSelector(selected: PayMethod?, onSelect: (PayMethod) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Payment method", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val all = PayMethod.entries
            all.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = selected == m,
                    onClick = { onSelect(m) },
                    shape = SegmentedButtonDefaults.itemShape(i, all.size),
                    icon = {
                        Icon(
                            when (m) {
                                PayMethod.CASH -> Icons.Rounded.Payments
                                PayMethod.BANK -> Icons.Rounded.AccountBalance
                                PayMethod.CARD -> Icons.Rounded.CreditCard
                            }, contentDescription = null, modifier = Modifier.size(18.dp)
                        )
                    },
                ) { Text(m.label) }
            }
        }
    }
}

/** Choose which account (Cash, Bank, ...) money comes from or goes to. Shows each account's current balance. */
@Composable
fun AccountSelector(
    accounts: List<com.lenzorium.expenses.data.Account>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    balances: Map<Long, Long>,
    currency: CurrencyInfo,
    label: String,
    modifier: Modifier = Modifier,
    excludeId: Long? = null,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            accounts.filter { it.id != excludeId }.forEach { a ->
                val sel = a.id == selectedId
                Row(
                    Modifier
                        .background(
                            if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.shapes.medium,
                        )
                        .then(if (sel) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier)
                        .clickable { onSelect(a.id) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(accountIcon(a.kind), null, Modifier.size(20.dp), tint = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(a.name, style = MaterialTheme.typography.titleSmall, color = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                        Text(
                            Money.format(balances[a.id] ?: 0L, a.currency?.let { com.lenzorium.expenses.domain.Currencies.find(it) } ?: currency),
                            style = MaterialTheme.typography.bodySmall,
                            color = if ((balances[a.id] ?: 0L) < 0) LocalMoneyColors.current.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

fun accountIcon(kind: com.lenzorium.expenses.data.AccountKind): ImageVector = when (kind) {
    com.lenzorium.expenses.data.AccountKind.CASH -> Icons.Rounded.Payments
    com.lenzorium.expenses.data.AccountKind.BANK -> Icons.Rounded.AccountBalance
    com.lenzorium.expenses.data.AccountKind.WALLET -> Icons.Rounded.PhoneAndroid
    com.lenzorium.expenses.data.AccountKind.OTHER -> Icons.Rounded.AccountBalanceWallet
}

@Composable
fun CategoryPicker(categories: List<Category>, selectedId: Long?, onSelect: (Long) -> Unit, onAddCustom: (() -> Unit)? = null) {
    Column {
        Text("Category", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            categories.forEach { c ->
                FilterChip(
                    selected = c.id == selectedId,
                    onClick = { onSelect(c.id) },
                    label = { Text(c.name) },
                    leadingIcon = { Icon(CategoryIcons.of(c.iconKey), null, tint = Color(c.colorArgb), modifier = Modifier.size(18.dp)) },
                )
            }
            if (onAddCustom != null) {
                FilterChip(selected = false, onClick = onAddCustom, label = { Text("New") }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) })
            }
        }
    }
}

private fun utcMillis(d: LocalDate): Long = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun fromUtcMillis(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()

/** Date picker dialog. [minDate]/[maxDate] limit the selectable range. */
@Composable
fun DatePickDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit, minDate: LocalDate? = null, maxDate: LocalDate? = null) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = utcMillis(initial),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val d = fromUtcMillis(utcTimeMillis)
                return (minDate == null || !d.isBefore(minDate)) && (maxDate == null || !d.isAfter(maxDate))
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(fromUtcMillis(it)) }; onDismiss() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

@Composable
fun TimePickDialog(initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}

/** Date + time selector for a transaction timestamp. */
@Composable
fun DateTimeField(millis: Long, onChange: (Long) -> Unit, minDate: LocalDate?, showTime: Boolean = true, label: String = "Date") {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val ldt = Dates.localDateTime(millis)
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickDate = true }, contentPadding = PaddingValues(horizontal = 14.dp)) {
                Icon(Icons.Rounded.CalendarToday, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(Dates.dayHeader(ldt.toLocalDate()).let { if (it == "Today" || it == "Yesterday") "$it, ${Dates.short(ldt.toLocalDate())}" else Dates.date(millis) })
            }
            if (showTime) {
                OutlinedButton(onClick = { pickTime = true }, contentPadding = PaddingValues(horizontal = 14.dp)) {
                    Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(Dates.time(millis))
                }
            }
        }
    }
    if (pickDate) {
        DatePickDialog(
            initial = ldt.toLocalDate(),
            minDate = minDate,
            maxDate = LocalDate.now(),
            onPick = { d -> onChange(d.atTime(ldt.toLocalTime()).atZone(Dates.zone).toInstant().toEpochMilli()) },
            onDismiss = { pickDate = false },
        )
    }
    if (pickTime) {
        TimePickDialog(
            initial = ldt.toLocalTime(),
            onPick = { t -> onChange(ldt.toLocalDate().atTime(t).atZone(Dates.zone).toInstant().toEpochMilli()) },
            onDismiss = { pickTime = false },
        )
    }
}

/** Inline error banner shown above form buttons. */
@Composable
fun ErrorBanner(message: String?) {
    if (message == null) return
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
        Spacer(Modifier.width(8.dp))
        Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
    }
}
