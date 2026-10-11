package app.tasca.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tasca.data.Account
import app.tasca.data.AccountBalance
import app.tasca.data.Palette
import app.tasca.data.Portfolio
import app.tasca.domain.Money
import app.tasca.io.LedgerSheets
import app.tasca.ui.AppViewModel
import app.tasca.ui.components.IconBubble
import app.tasca.ui.theme.LocalMoneyColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(vm: AppViewModel, contentPadding: PaddingValues, onTransfer: () -> Unit, onTransactions: (Long) -> Unit, onInvestments: () -> Unit) {
    val balances by vm.balances.collectAsStateWithLifecycle()
    val holdings by vm.holdings.collectAsStateWithLifecycle()
    val quotes by vm.quotes.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<AccountBalance?>(null) }
    var creating by remember { mutableStateOf(false) }
    val visible = balances.filter { !it.deleted }
    val hiddenCents = balances.filter { it.deleted }.sumOf { it.balanceCents }

    Column {
        TopAppBar(
            title = { Text("Accounts", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money.format(balances.sumOf { it.balanceCents }), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = onTransfer) { Icon(Icons.Outlined.SwapHoriz, null); Spacer(Modifier.width(6.dp)); Text("Transfer") }
                        FilledTonalButton(onClick = { creating = true }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("Account") }
                    }
                }
            }
            items(visible, key = { it.id }) { a ->
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { editing = a }.padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBubble(a.icon, Color(a.color))
                    Spacer(Modifier.width(12.dp))
                    Text(a.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        Money.format(a.balanceCents), fontWeight = FontWeight.SemiBold,
                        color = if (a.balanceCents < 0) LocalMoneyColors.current.expense else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            item {
                val p = Portfolio.of(holdings, quotes.associateBy { it.symbol })
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp).clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onInvestments).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ShowChart, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Investments", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    if (holdings.isNotEmpty()) Text(
                        Money.signed(p.gainCents), fontWeight = FontWeight.SemiBold,
                        color = if (p.gainCents >= 0) LocalMoneyColors.current.income else LocalMoneyColors.current.expense,
                    )
                }
            }
            if (hiddenCents != 0L) item {
                Text(
                    "Deleted accounts still hold ${Money.format(hiddenCents)}, included in the Total",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }

    if (creating) {
        AccountDialog(null, onDismiss = { creating = false }) { account, balance ->
            vm.saveAccount(account.copy(initialCents = balance ?: 0))
            creating = false
        }
    }
    editing?.let { current ->
        var confirmDelete by remember(current.id) { mutableStateOf(false) }
        val invested = holdings.filter { it.accountId == current.id }.sumOf { it.investedCents }
        AccountDialog(
            current,
            investedCents = invested,
            onDismiss = { editing = null },
            onTransactions = { editing = null; onTransactions(current.id) },
            onDelete = { confirmDelete = true },
        ) { account, entered ->
            val balance = entered?.plus(invested)
            val correction = if (balance != null) balance - current.balanceCents else 0
            vm.saveAccount(account.copy(initialCents = current.initialCents + correction))
            editing = null
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete ${current.name}?") },
                text = { Text("It disappears from the app. Its transactions stay in your history and in the Total.") },
                confirmButton = {
                    TextButton(onClick = { vm.deleteAccount(current.id); confirmDelete = false; editing = null }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun AccountDialog(
    current: AccountBalance?,
    investedCents: Long = 0,
    onDismiss: () -> Unit,
    onTransactions: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onSave: (Account, balance: Long?) -> Unit,
) {
    var name by remember { mutableStateOf(current?.name ?: "") }
    var color by remember { mutableStateOf(current?.color ?: Palette.at(0)) }
    var icon by remember { mutableStateOf(current?.icon ?: "wallet") }
    val cashOnly = investedCents > 0
    var balance by remember { mutableStateOf(current?.let { Money.plain(it.balanceCents - investedCents).replace('.', ',') } ?: "") }
    var styling by remember { mutableStateOf(current == null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            if (current == null) Text("New account")
            else Row(verticalAlignment = Alignment.CenterVertically) {
                Text(current.name, Modifier.weight(1f, fill = false))
                Text(
                    "  ${current.code}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onTransactions != null) FilledTonalButton(onClick = onTransactions, Modifier.fillMaxWidth()) { Text("See transactions") }
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    balance, { balance = it },
                    label = { Text(if (current == null) "Starting balance" else if (cashOnly) "Cash, not invested" else "Current balance") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = {
                        if (cashOnly) Text("Plus ${Money.format(investedCents)} invested. Changing it corrects the balance without adding a transaction")
                        else if (current != null) Text("Changing it corrects the balance without adding a transaction")
                    },
                )
                if (styling) StylePicker(icon, color, onIcon = { icon = it }, onColor = { color = it })
                else TextButton(onClick = { styling = true }) { Text("Change icon and colour") }
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete account", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val base = current?.toAccount() ?: Account(name = "", color = color)
                onSave(base.copy(name = name.trim(), color = color, icon = icon), LedgerSheets.parseAmount(balance))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
