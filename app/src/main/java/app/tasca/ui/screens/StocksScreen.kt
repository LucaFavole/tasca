package app.tasca.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tasca.data.Holding
import app.tasca.data.Portfolio
import app.tasca.domain.Money
import app.tasca.io.LedgerSheets
import app.tasca.io.SymbolHit
import app.tasca.ui.AppViewModel
import app.tasca.ui.theme.LocalMoneyColors
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val QTY = DecimalFormat("#,##0.####")
private val TIME = DateTimeFormatter.ofPattern("d MMM HH:mm")

@Composable
private fun gainColor(cents: Long) = when {
    cents > 0 -> LocalMoneyColors.current.income
    cents < 0 -> LocalMoneyColors.current.expense
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun pct(p: Double?) = p?.let { (if (it > 0) "+" else "") + "%.2f%%".format(it) } ?: ""

@Composable
fun StocksScreen(vm: AppViewModel, onBack: () -> Unit, onEdit: (Long?) -> Unit) {
    val holdings by vm.holdings.collectAsStateWithLifecycle()
    val quotes by vm.quotes.collectAsStateWithLifecycle()
    val balances by vm.balances.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    var fetched by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!fetched) { fetched = true; vm.refreshQuotes() } }

    val bySymbol = quotes.associateBy { it.symbol }
    val all = Portfolio.of(holdings, bySymbol)
    val cash = balances.sumOf { it.balanceCents }
    val updated = holdings.mapNotNull { bySymbol[it.symbol]?.fetchedAt }.minOrNull()

    SubScreen("Investments", onBack, actions = {
        if (refreshing) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
        else IconButton(onClick = vm::refreshQuotes) { Icon(Icons.Outlined.Refresh, "Refresh prices") }
    }) {
        LazyColumn(Modifier.padding(horizontal = 16.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total with gains", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money.format(cash + all.gainCents), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${Money.signed(all.gainCents)}  ${pct(all.gainPct)}",
                        color = gainColor(all.gainCents), fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Total without gains ${Money.format(cash)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Invested ${Money.format(all.investedCents)} → now ${Money.format(all.valueCents)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        updated?.let { "Prices from Yahoo Finance, " + Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(TIME) } ?: "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = { onEdit(null) }) { Icon(Icons.Outlined.Add, null); Text("Add stock or ETF") }
                }
            }
            if (holdings.isEmpty()) item {
                Text(
                    "Add what you own in each account: search by ISIN, ticker or name, then enter how many shares and what you paid in total.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            holdings.groupBy { it.accountId }.forEach { (accountId, list) ->
                val p = Portfolio.of(list, bySymbol)
                item(key = "a$accountId") {
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            balances.firstOrNull { it.id == accountId }?.name.orEmpty(), Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        )
                        val cashCents = (balances.firstOrNull { it.id == accountId }?.balanceCents ?: 0) - p.investedCents
                        Text(
                            "cash ${Money.format(cashCents)} · ${Money.format(p.valueCents)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (cashCents < 0) LocalMoneyColors.current.expense else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                items(list, key = { it.id }) { h -> HoldingRow(h, bySymbol[h.symbol]) { onEdit(h.id) } }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun HoldingRow(h: Holding, q: app.tasca.data.Quote?, onClick: () -> Unit) {
    val value = q?.valueCents(h.quantity)
    val gain = value?.minus(h.investedCents)
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(h.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val day = q?.previousClose?.takeIf { it > 0 }?.let { (q.price - it) * 100 / it }
            Text(
                listOfNotNull(
                    "${QTY.format(h.quantity)} × " + (q?.let { "%.2f %s".format(it.price, it.currency) } ?: "…"),
                    day?.let { "today ${pct(it)}" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(value?.let { Money.format(it) } ?: Money.format(h.investedCents), fontWeight = FontWeight.SemiBold)
            if (gain != null) {
                Text(
                    "${Money.signed(gain)} ${pct(if (h.investedCents > 0) gain * 100.0 / h.investedCents else null)}",
                    style = MaterialTheme.typography.bodySmall, color = gainColor(gain),
                )
            }
        }
    }
}
