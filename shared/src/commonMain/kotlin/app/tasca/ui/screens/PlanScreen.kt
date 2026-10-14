package app.tasca.ui.screens

import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import app.tasca.data.Category
import app.tasca.data.CategoryKind
import app.tasca.data.Recurring
import app.tasca.data.TxnKind
import app.tasca.domain.Frequency
import app.tasca.domain.Money
import app.tasca.domain.alreadyPosted
import app.tasca.domain.today
import app.tasca.io.LedgerSheets
import app.tasca.ui.AppViewModel
import app.tasca.ui.components.EmptyHint
import app.tasca.ui.components.IconBubble
import app.tasca.ui.components.SectionCard
import app.tasca.ui.components.kindColor
import app.tasca.ui.theme.LocalMoneyColors
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(vm: AppViewModel, contentPadding: PaddingValues, onSettings: () -> Unit, onEditRecurring: (Long?) -> Unit) {
    val recurring by vm.recurring.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val spend by vm.monthBudgetSpend.collectAsStateWithLifecycle()
    var budgetFor by remember { mutableStateOf<Category?>(null) }
    var pickBudget by remember { mutableStateOf(false) }

    Column {
        TopAppBar(
            title = { Text("Plan", fontWeight = FontWeight.Bold) },
            actions = { IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(
                    title = "Regular payments",
                    action = { TextButton(onClick = { onEditRecurring(null) }) { Icon(Icons.Outlined.Add, null); Text("Add") } },
                ) {
                    if (recurring.isEmpty()) EmptyHint("Rent, salary, subscriptions… add them once and they're recorded on each due date.")
                    val monthly = recurring.filter { it.active && !it.finished && it.kind == TxnKind.EXPENSE }.sumOf { monthlyEquivalent(it) }
                    if (monthly > 0) {
                        Text(
                            "≈ ${Money.format(monthly)} of fixed costs per month",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    recurring.forEach { r ->
                        val c = categories.firstOrNull { it.id == r.categoryId }
                        Row(
                            Modifier.fillMaxWidth().clickable { onEditRecurring(r.id) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (c != null) IconBubble(c.icon, Color(c.color), 36.dp)
                            else Icon(Icons.Outlined.SwapHoriz, null, tint = LocalMoneyColors.current.transfer, modifier = Modifier.padding(6.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val status = when {
                                    !r.active -> "Paused"
                                    r.finished -> "Ended"
                                    else -> "Next: ${relativeDay(r.nextDate)}"
                                }
                                Text(
                                    "${r.frequency.describe(r.every)} · $status",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(Money.format(r.amountCents), color = kindColor(r.kind), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            item {
                SectionCard(
                    title = "Monthly budgets",
                    action = { TextButton(onClick = { pickBudget = true }) { Icon(Icons.Outlined.Add, null); Text("Add") } },
                ) {
                    if (budgets.isEmpty()) EmptyHint("Set a monthly limit on a category to see here how much of it you've spent.")
                    budgets.forEach { b ->
                        val c = categories.firstOrNull { it.id == b.categoryId } ?: return@forEach
                        val spent = spend.firstOrNull { it.categoryId == c.id }?.totalCents ?: 0
                        Row(
                            Modifier.fillMaxWidth().clickable { budgetFor = c }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconBubble(c.icon, Color(c.color), 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name)
                                val left = b.monthlyCents - spent
                                Text(
                                    if (left >= 0) "${Money.format(left)} left this month" else "${Money.format(-left)} over budget",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (left >= 0) MaterialTheme.colorScheme.onSurfaceVariant else LocalMoneyColors.current.expense,
                                )
                            }
                            Text(Money.format(b.monthlyCents), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (pickBudget) {
        AlertDialog(
            onDismissRequest = { pickBudget = false },
            title = { Text("Budget for which category?") },
            text = {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(Modifier.verticalScroll(rememberScrollState())) {
                    categories.filter { it.kind == CategoryKind.EXPENSE && budgets.none { b -> b.categoryId == it.id } }.forEach { c ->
                        CategoryTile(c, false) { pickBudget = false; budgetFor = c }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickBudget = false }) { Text("Cancel") } },
        )
    }
    budgetFor?.let { c ->
        val current = budgets.firstOrNull { it.categoryId == c.id }
        var text by remember(c.id) { mutableStateOf(current?.let { Money.plain(it.monthlyCents) } ?: "") }
        AlertDialog(
            onDismissRequest = { budgetFor = null },
            title = { Text("Monthly budget · ${c.name}") },
            text = {
                OutlinedTextField(
                    text, { text = it }, singleLine = true, placeholder = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.setBudget(c.id, LedgerSheets.parseAmount(text)); budgetFor = null }) { Text("Save") }
            },
            dismissButton = {
                if (current != null) TextButton(onClick = { vm.setBudget(c.id, null); budgetFor = null }) { Text("Remove") }
                else TextButton(onClick = { budgetFor = null }) { Text("Cancel") }
            },
        )
    }
}

private fun monthlyEquivalent(r: Recurring): Long = when (r.frequency) {
    Frequency.DAILY -> r.amountCents * 365 / 12 / r.every
    Frequency.WEEKLY -> r.amountCents * 52 / 12 / r.every
    Frequency.MONTHLY -> r.amountCents / r.every
    Frequency.YEARLY -> r.amountCents / 12 / r.every
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRecurringScreen(vm: AppViewModel, id: Long?, onDone: () -> Unit) {
    val draft = remember { TxnDraft(TxnKind.EXPENSE) }
    var original by remember { mutableStateOf<Recurring?>(null) }
    var frequency by remember { mutableStateOf(Frequency.MONTHLY) }
    var every by remember { mutableStateOf("1") }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var active by remember { mutableStateOf(true) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(id) {
        if (id != null) vm.recurringById(id)?.let {
            original = it; draft.load(it); frequency = it.frequency; every = it.every.toString(); endDate = it.endDate; active = it.active
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(if (id == null) "New regular payment" else "Regular payment") },
            navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            actions = { if (id != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            DraftFields(vm, draft, showDate = false)
            Label("Repeats")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Frequency.entries.forEach { f -> FilterChip(frequency == f, { frequency = f }, label = { Text(f.label) }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Every")
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    every, { every = it.filter(Char::isDigit).take(3) }, Modifier.width(80.dp), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.width(8.dp))
                Text(frequency.unit + "(s)")
            }
            Label("Starts")
            AssistChip({ pickStart = true }, { Text(relativeDay(draft.date)) }, leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null) })
            Label("Ends")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(endDate == null, { endDate = null }, label = { Text("Never") })
                AssistChip({ pickEnd = true }, { Text(endDate?.let(::relativeDay) ?: "On a date…") }, leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null) })
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Active")
                    Text("Paused payments are not recorded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(active, { active = it })
            }
            if (draft.date < today() && original == null) {
                Text(
                    "The start date is in the past: occurrences up to today are added right away.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        Keypad(draft, "Save", error, dropUnlessResumed {
            error = draft.problem() ?: if (draft.note.isBlank()) "Give it a name" else null
            if (error != null) return@dropUnlessResumed
            val n = every.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val base = Recurring(
                id = id ?: 0, name = draft.note.trim(), kind = draft.kind, amountCents = draft.amount!!,
                accountId = draft.accountId!!,
                toAccountId = if (draft.kind == TxnKind.TRANSFER) draft.toAccountId else null,
                categoryId = if (draft.kind == TxnKind.TRANSFER) null else draft.categoryId,
                frequency = frequency, every = n, startDate = draft.date, endDate = endDate, active = active,
            )
            vm.saveRecurring(base.copy(posted = alreadyPosted(original, base)))
            onDone()
        })
    }

    if (pickStart || pickEnd) {
        val initial = if (pickStart) draft.date else endDate ?: draft.date.plus(1, DateTimeUnit.YEAR)
        val state = rememberDatePickerState(initial.pickerMillis())
        DatePickerDialog(
            onDismissRequest = { pickStart = false; pickEnd = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val d = pickedDate(it)
                        if (pickStart) draft.date = d else endDate = d
                    }
                    pickStart = false; pickEnd = false
                }) { Text("OK") }
            },
        ) { DatePicker(state) }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this regular payment?") },
            text = { Text("Transactions it already recorded are kept.") },
            confirmButton = { TextButton(onClick = dropUnlessResumed { vm.deleteRecurring(id!!); confirmDelete = false; onDone() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
