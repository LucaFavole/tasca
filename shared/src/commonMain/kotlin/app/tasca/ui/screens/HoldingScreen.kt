package app.tasca.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import app.tasca.data.Holding
import app.tasca.domain.Money
import app.tasca.domain.Trade
import app.tasca.domain.TradeSide
import app.tasca.io.LedgerSheets
import app.tasca.io.SymbolHit
import app.tasca.ui.AppViewModel
import app.tasca.ui.plainDecimal
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

@Composable
fun EditHoldingScreen(vm: AppViewModel, id: Long?, onDone: () -> Unit) {
    val holdings by vm.holdings.collectAsStateWithLifecycle()
    val balances by vm.balances.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    val existing = holdings.firstOrNull { it.id == id }

    var picked by remember { mutableStateOf<SymbolHit?>(null) }
    var livePrice by remember { mutableStateOf<Pair<Double, String>?>(null) }
    var accountId by remember { mutableStateOf<Long?>(null) }
    var quantity by remember { mutableStateOf("") }
    var avgPrice by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var trading by remember { mutableStateOf<TradeSide?>(null) }

    LaunchedEffect(existing?.id) {
        existing?.let {
            picked = SymbolHit(it.symbol, it.name, "", "")
            accountId = it.accountId
            quantity = plainDecimal(it.quantity)
            avgPrice = plainDecimal(it.investedCents / 100.0 / it.quantity, 2)
        }
    }
    LaunchedEffect(picked?.symbol) { livePrice = null; picked?.let { livePrice = vm.livePrice(it.symbol) } }
    LaunchedEffect(balances.size) { if (accountId == null) accountId = balances.firstOrNull { !it.deleted }?.id }

    SubScreen(if (existing == null) "Add stock or ETF" else "Investment", onDone, actions = {
        if (existing != null) TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
    }) {
        val p = picked
        if (p == null) {
            SymbolSearch(vm) { picked = it }
            return@SubScreen
        }
        Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        val now = livePrice?.let { (price, cur) ->
                            " · today ${Money.format((price * 100).roundToLong())}" + if (cur != currency) " (from $cur)" else ""
                        }.orEmpty()
                        Text(listOf(p.symbol, p.exchange).filter { it.isNotEmpty() }.joinToString(" · ") + now, style = MaterialTheme.typography.bodySmall)
                    }
                    if (existing == null) TextButton(onClick = { picked = null }) { Text("Change") }
                }
                if (existing != null) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = { trading = TradeSide.BUY }, Modifier.weight(1f)) { Text("Buy more") }
                        FilledTonalButton(onClick = { trading = TradeSide.SELL }, Modifier.weight(1f)) { Text("Sell") }
                    }
                    Text(
                        "For new trades use Buy and Sell: fees and sale profits are recorded for you. Edit the numbers below only to correct them.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Label("Account")
                ChipRow(balances.filter { !it.deleted || it.id == accountId }.map { it.id to it.name }, accountId) { accountId = it }
                Label("Shares")
                OutlinedTextField(
                    quantity, { quantity = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("e.g. 12,345678") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Label("Average buy price ($currency)")
                OutlinedTextField(
                    avgPrice, { avgPrice = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("As shown by your broker") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                val qty = number(quantity)
                val avg = number(avgPrice)
                if (qty != null && avg != null) Text(
                    "Total paid ${Money.format(Trade.cents(qty, avg))}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
            Button(
                onClick = dropUnlessResumed {
                    val qty = number(quantity)
                    val avg = number(avgPrice)
                    error = when {
                        accountId == null -> "Pick an account"
                        qty == null || qty <= 0 -> "Enter how many shares"
                        avg == null || avg < 0 -> "Enter the average buy price"
                        else -> null
                    }
                    if (error == null) {
                        vm.saveHolding(Holding(existing?.id ?: 0, accountId!!, p.symbol, p.name, qty!!, Trade.cents(qty, avg!!)))
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).height(48.dp),
            ) { Text("Save") }
        }
    }

    trading?.let { side ->
        val once = dropUnlessResumed { trading = null; onDone() }
        TradeDialog(side, existing!!, livePrice?.first, vm.taxRatePct, onDismiss = { trading = null }) { shares, unit, fee, rate, tax ->
            if (trading == null) return@TradeDialog
            if (side == TradeSide.SELL) vm.taxRatePct = rate
            vm.trade(existing.id, side, shares, unit, fee, tax)
            once()
        }
    }
    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${existing.name}?") },
            text = { Text("Only removes it from Investments. Your accounts and transactions don't change.") },
            confirmButton = {
                TextButton(onClick = dropUnlessResumed { vm.deleteHolding(existing.id); confirmDelete = false; onDone() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SymbolSearch(vm: AppViewModel, onPick: (SymbolHit) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SymbolHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    fun search() {
        if (query.isBlank()) return
        searching = true; searchError = null
        scope.launch {
            runCatching { vm.searchSymbols(query) }
                .onSuccess { hits = it; if (it.isEmpty()) searchError = "Nothing found for \"$query\"" }
                .onFailure { searchError = "Couldn't reach Yahoo Finance" }
            searching = false
        }
    }
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth(),
            placeholder = { Text("ISIN, ticker or name") }, singleLine = true, shape = MaterialTheme.shapes.medium,
            trailingIcon = {
                if (searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else IconButton(onClick = ::search) { Icon(Icons.Outlined.Search, "Search") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }),
        )
        searchError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        if (hits.isNotEmpty()) Text(
            "The same fund trades on several exchanges, euro ones first. After picking, compare today's price with yours.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        LazyColumn {
            items(hits, key = { it.symbol }) { h ->
                Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onPick(h) }.padding(vertical = 10.dp, horizontal = 4.dp)) {
                    Text(h.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(h.symbol, h.exchange, h.type).filter { it.isNotEmpty() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TradeDialog(
    side: TradeSide,
    h: Holding,
    livePrice: Double?,
    defaultTaxRate: Double,
    onDismiss: () -> Unit,
    onConfirm: (shares: Double, price: Double, feeCents: Long, taxRatePct: Double, taxCents: Long) -> Unit,
) {
    var shares by remember { mutableStateOf("") }
    var price by remember { mutableStateOf(livePrice?.let { plainDecimal(it, 2) } ?: "") }
    var fee by remember { mutableStateOf("1") }
    var taxRate by remember { mutableStateOf(plainDecimal(defaultTaxRate, 2)) }
    var error by remember { mutableStateOf<String?>(null) }
    val buy = side == TradeSide.BUY
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (buy) "Buy more" else "Sell", maxLines = 1) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    shares, { shares = it }, label = { Text("Shares") }, singleLine = true,
                    supportingText = { if (!buy) Text("You have ${plainDecimal(h.quantity)}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    price, { price = it }, label = { Text("Price per share") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        fee, { fee = it }, Modifier.weight(1f), label = { Text("Fee") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    if (!buy) OutlinedTextField(
                        taxRate, { taxRate = it }, Modifier.weight(1f), label = { Text("Tax on gain %") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                val s = number(shares)
                val p = number(price)
                val f = LedgerSheets.parseAmount(fee.ifBlank { "0" }) ?: 0
                val r = number(taxRate) ?: 0.0
                if (!buy && s != null && p != null && s > 0 && s <= h.quantity + 1e-6) {
                    val o = Trade.sale(Trade.apply(side, h.quantity, h.investedCents, s, p), s, p, f, r)
                    Text(
                        "Gain ${Money.signed(o.gainCents)} · tax ${Money.format(o.taxCents)} · you receive ${Money.format(o.receivedCents)}",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                }
                Text(
                    if (buy) "Paid from this account's cash. If you sent new money to the broker, record that transfer first."
                    else "Goes to this account's cash. Gain, fee and tax are recorded for you. The tax applies only to a gain, after the fee.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = number(shares)
                val p = number(price)
                val f = LedgerSheets.parseAmount(fee.ifBlank { "0" })
                error = when {
                    s == null || s <= 0 -> "Enter the shares"
                    !buy && s > h.quantity + 1e-6 -> "You only have ${plainDecimal(h.quantity)}"
                    p == null || p <= 0 -> "Enter the price"
                    f == null || f < 0 -> "Enter the fee, or 0"
                    !buy && (number(taxRate) ?: -1.0) !in 0.0..100.0 -> "Enter a tax rate between 0 and 100"
                    else -> null
                }
                if (error == null) {
                    val rate = if (buy) 0.0 else number(taxRate)!!
                    val tax = if (buy) 0 else Trade.sale(Trade.apply(side, h.quantity, h.investedCents, s!!, p!!), s, p, f!!, rate).taxCents
                    onConfirm(s!!, p!!, f!!, rate, tax)
                }
            }) { Text(if (buy) "Buy" else "Sell") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Accepts "12,345678", "1.234,5" and "265.52". */
private fun number(s: String): Double? = s.trim().replace(" ", "").let {
    if (it.contains(',')) it.replace(".", "").replace(',', '.') else it
}.toDoubleOrNull()?.takeIf { it.isFinite() }
