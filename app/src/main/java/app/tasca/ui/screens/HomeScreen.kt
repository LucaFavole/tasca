package app.tasca.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.tasca.ui.AppViewModel
import app.tasca.ui.components.Donut
import app.tasca.ui.components.EmptyHint
import app.tasca.ui.components.LegendRow
import app.tasca.ui.components.Slice
import app.tasca.ui.components.kindColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    contentPadding: PaddingValues,
    onOpenActivity: (categoryId: Long?, period: Period) -> Unit,
    onSettings: () -> Unit,
) {
    val balances by vm.balances.collectAsStateWithLifecycle()
    val selected by vm.selectedAccount.collectAsStateWithLifecycle()
    val period by vm.homePeriod.collectAsStateWithLifecycle()
    val kind by vm.homeKind.collectAsStateWithLifecycle()
    val totals by vm.homeTotals.collectAsStateWithLifecycle()
    val summary by vm.homeSummary.collectAsStateWithLifecycle()

    Column {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f)) { AccountPicker(balances, selected) { vm.selectedAccount.value = it } }
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings") }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { PeriodBar(period) { vm.homePeriod.value = it } }
            item {
                val spent = summary.filter { it.kind == TxnKind.EXPENSE }.sumOf { it.totalCents }
                val earned = summary.filter { it.kind == TxnKind.INCOME }.sumOf { it.totalCents }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(TxnKind.EXPENSE to spent, TxnKind.INCOME to earned).forEachIndexed { i, (k, cents) ->
                        SegmentedButton(kind == k, { vm.homeKind.value = k }, SegmentedButtonDefaults.itemShape(i, 2), icon = {}) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (k == TxnKind.EXPENSE) "Expenses" else "Income", style = MaterialTheme.typography.labelMedium)
                                Text(Money.format(cents), fontWeight = FontWeight.Bold, color = kindColor(k))
                            }
                        }
                    }
                }
            }
            item {
                val sum = totals.sumOf { it.totalCents }
                Donut(totals.map { Slice(it.totalCents, Color(it.color)) }, Modifier.fillMaxWidth().height(220.dp)) {
                    Text(Money.format(sum, compact = true), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
            if (totals.isEmpty()) item { EmptyHint("Nothing in this period") }
            val sum = totals.sumOf { it.totalCents }
            totals.forEach { t ->
                item(key = t.categoryId) {
                    LegendRow(t.icon, Color(t.color), t.name, if (sum > 0) Math.round(t.totalCents * 100.0 / sum).toInt() else 0, t.totalCents) {
                        onOpenActivity(t.categoryId, period)
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
    val today = LocalDate.now()
    return when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> d.format(DAY)
    }
}
