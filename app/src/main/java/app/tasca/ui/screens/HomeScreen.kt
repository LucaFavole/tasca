package app.tasca.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import kotlin.math.abs
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tasca.data.AccountBalance
import app.tasca.data.TxnKind
import app.tasca.domain.Money
import app.tasca.domain.Period
import app.tasca.domain.PeriodKind
import app.tasca.domain.today
import app.tasca.ui.AppViewModel
import app.tasca.ui.HomeView
import app.tasca.ui.components.Donut
import app.tasca.ui.components.EmptyHint
import app.tasca.ui.components.LegendRow
import app.tasca.ui.components.Slice
import app.tasca.ui.components.TxnItem
import app.tasca.ui.components.kindColor
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter

/**
 * The month at a glance and every transaction in it: one header (account, period, expenses and income),
 * then either the category breakdown or the transaction list, switched in place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, contentPadding: PaddingValues, onEditTxn: (Long) -> Unit, onSettings: () -> Unit) {
    val balances by vm.balances.collectAsStateWithLifecycle()
    val selected by vm.selectedAccount.collectAsStateWithLifecycle()
    val period by vm.homePeriod.collectAsStateWithLifecycle()
    val kind by vm.homeKind.collectAsStateWithLifecycle()
    val totals by vm.homeTotals.collectAsStateWithLifecycle()
    val summary by vm.homeSummary.collectAsStateWithLifecycle()
    val view by vm.homeView.collectAsStateWithLifecycle()
    val categoryId by vm.homeCategory.collectAsStateWithLifecycle()
    val query by vm.homeQuery.collectAsStateWithLifecycle()
    val rows by vm.homeRows.collectAsStateWithLifecycle()
    val listKind by vm.homeListKind.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(query.isNotEmpty()) }
    val narrowed by vm.homeNarrowed.collectAsStateWithLifecycle()
    BackHandler(enabled = searching || narrowed) {
        if (searching) {
            searching = false
            vm.homeQuery.value = ""
        } else vm.homeBack()
    }

    Column {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f)) { AccountPicker(balances, selected) { vm.selectedAccount.value = it } }
            IconButton(onClick = {
                searching = !searching
                if (searching) vm.homeView.value = HomeView.TRANSACTIONS else vm.homeQuery.value = ""
            }) { Icon(if (searching) Icons.Outlined.SearchOff else Icons.Outlined.Search, "Search") }
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings") }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().pointerInput(period) {
                if (period.kind == PeriodKind.ALL) return@pointerInput
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = { if (abs(dx) > 120.dp.toPx()) vm.homePeriod.value = period.shift(if (dx < 0) 1 else -1) },
                ) { _, delta -> dx += delta }
            },
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (searching) item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.homeQuery.value = it },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { vm.homeQuery.value = "" }) { Icon(Icons.Outlined.Close, "Clear") }
                    },
                    placeholder = { Text("Search notes, categories, accounts") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { PeriodBar(period) { vm.homePeriod.value = it } }
                    SingleChoiceSegmentedButtonRow(Modifier.width(112.dp)) {
                        listOf(
                            HomeView.CATEGORIES to Icons.Outlined.DonutLarge,
                            HomeView.TRANSACTIONS to Icons.AutoMirrored.Outlined.ViewList,
                        ).forEachIndexed { i, (v, icon) ->
                            SegmentedButton(view == v, { vm.homeView.value = v }, SegmentedButtonDefaults.itemShape(i, 2), icon = {}) {
                                Icon(icon, if (v == HomeView.CATEGORIES) "Categories" else "Transactions", Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
            item {
                val spent = summary.filter { it.kind == TxnKind.EXPENSE }.sumOf { it.totalCents }
                val earned = summary.filter { it.kind == TxnKind.INCOME }.sumOf { it.totalCents }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(TxnKind.EXPENSE to spent, TxnKind.INCOME to earned).forEachIndexed { i, (k, cents) ->
                        val on = if (view == HomeView.CATEGORIES) kind == k else listKind == k
                        SegmentedButton(on, {
                            if (view == HomeView.CATEGORIES) vm.homeKind.value = k
                            else vm.homeListKind.value = if (listKind == k) null else k
                        }, SegmentedButtonDefaults.itemShape(i, 2), icon = {}) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (k == TxnKind.EXPENSE) "Expenses" else "Income", style = MaterialTheme.typography.labelMedium)
                                Text(Money.format(cents), fontWeight = FontWeight.Bold, color = kindColor(k))
                            }
                        }
                    }
                }
            }
            if (view == HomeView.CATEGORIES) {
                val sum = totals.sumOf { it.totalCents }
                if (totals.isEmpty()) item { EmptyHint("Nothing in this period") }
                else item {
                    Donut(totals.map { Slice(it.totalCents, Color(it.color)) }, Modifier.fillMaxWidth().height(170.dp)) {
                        Text(Money.format(sum, compact = true), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
                totals.forEach { t ->
                    item(key = t.categoryId) {
                        LegendRow(t.icon, Color(t.color), t.name, if (sum > 0) Math.round(t.totalCents * 100.0 / sum).toInt() else 0, t.totalCents) {
                            vm.homeCategory.value = t.categoryId
                            vm.homeListKind.value = null
                            vm.homeView.value = HomeView.TRANSACTIONS
                        }
                    }
                }
            } else {
                categories.firstOrNull { it.id == categoryId }?.let { cat ->
                    item {
                        InputChip(
                            selected = true, onClick = { vm.homeCategory.value = null },
                            label = { Text(cat.name) },
                            trailingIcon = { Icon(Icons.Outlined.Close, "Remove filter") },
                        )
                    }
                }
                val shown = if (listKind == null) rows else rows.filter { it.kind == listKind }
                if (shown.isEmpty()) item { EmptyHint("No transactions match") }
                shown.groupBy { it.date }.forEach { (date, dayRows) ->
                    item(key = "d$date") {
                        val net = dayRows.sumOf {
                            when (it.kind) { TxnKind.INCOME -> it.amountCents; TxnKind.EXPENSE -> -it.amountCents; else -> 0 }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(relativeDay(date), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            if (net != 0L) Text(Money.signed(net), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(dayRows, key = { it.id }) { row ->
                        val swipe = rememberSwipeToDismissBoxState(confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.EndToStart) vm.deleteTxnUndoable(row.id)
                            it == SwipeToDismissBoxValue.EndToStart
                        })
                        SwipeToDismissBox(
                            swipe, enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                Box(
                                    Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.errorContainer)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) { Icon(Icons.Outlined.Delete, "Delete", tint = MaterialTheme.colorScheme.onErrorContainer) }
                            },
                        ) {
                            Box(Modifier.background(MaterialTheme.colorScheme.background)) { TxnItem(row, selected) { onEditTxn(row.id) } }
                        }
                    }
                }
            }
        }
    }
}

/** Title-bar dropdown: Total or one account, with its balance. */
@Composable
fun AccountPicker(balances: List<AccountBalance>, selected: Long?, showBalance: Boolean = true, onSelect: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val visible = balances.filter { !it.deleted }
    val current = balances.firstOrNull { it.id == selected }
    val cents = current?.balanceCents ?: balances.sumOf { it.balanceCents }
    Box {
        TextButton(onClick = { open = true }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(current?.name ?: "Total", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.Outlined.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (showBalance) Text(Money.format(cents), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem({ Text("Total") }, { onSelect(null); open = false })
            visible.forEach { a -> DropdownMenuItem({ Text(a.name) }, { onSelect(a.id); open = false }) }
        }
    }
}

/** One line: previous, the period (tap to switch week/month/year/all), next. */
@Composable
fun PeriodBar(period: Period, onChange: (Period) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        val stepping = period.kind != PeriodKind.ALL
        IconButton(onClick = { onChange(period.shift(-1)) }, enabled = stepping) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous") }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            TextButton(onClick = { open = true }) {
                Text(period.label(), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                Icon(Icons.Outlined.ArrowDropDown, null)
            }
            DropdownMenu(open, { open = false }) {
                PeriodKind.entries.forEach { k -> DropdownMenuItem({ Text(k.label) }, { onChange(Period.of(k)); open = false }) }
            }
        }
        IconButton(onClick = { onChange(period.shift(1)) }, enabled = stepping) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next") }
    }
}

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM")

fun relativeDay(d: LocalDate): String {
    val today = today()
    return when (d) {
        today -> "Today"
        today.plus(1, DateTimeUnit.DAY) -> "Tomorrow"
        today.minus(1, DateTimeUnit.DAY) -> "Yesterday"
        else -> d.toJavaLocalDate().format(DAY)
    }
}
