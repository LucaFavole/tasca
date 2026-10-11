package app.tasca.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tasca.data.TxnKind
import app.tasca.domain.Money
import app.tasca.ui.AppViewModel
import app.tasca.ui.components.EmptyHint
import app.tasca.ui.components.TxnItem
import app.tasca.ui.theme.LocalMoneyColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(vm: AppViewModel, contentPadding: PaddingValues, onEditTxn: (Long) -> Unit) {
    val filter by vm.activityFilter.collectAsStateWithLifecycle()
    val rows by vm.activity.collectAsStateWithLifecycle()
    val balances by vm.balances.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    fun update(f: (app.tasca.ui.ActivityFilter) -> app.tasca.ui.ActivityFilter) { vm.activityFilter.value = f(vm.activityFilter.value) }

    Column {
        TopAppBar(
            title = { AccountPicker(balances, filter.accountId, showBalance = false) { id -> update { it.copy(accountId = id) } } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 88.dp),
        ) {
            item {
                OutlinedTextField(
                    value = filter.query,
                    onValueChange = { q -> update { it.copy(query = q) } },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        if (filter.query.isNotEmpty()) IconButton(onClick = { update { it.copy(query = "") } }) { Icon(Icons.Outlined.Close, "Clear") }
                    },
                    placeholder = { Text("Search") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { PeriodBar(filter.period) { p -> update { it.copy(period = p) } } }
            val cat = categories.firstOrNull { it.id == filter.categoryId }
            if (cat != null) item {
                InputChip(
                    selected = true, onClick = { update { it.copy(categoryId = null) } },
                    label = { Text(cat.name) },
                    trailingIcon = { Icon(Icons.Outlined.Close, "Remove filter") },
                )
            }
            if (rows.isEmpty()) item { EmptyHint("No transactions match") }
            rows.groupBy { it.date }.forEach { (date, dayRows) ->
                item(key = "d$date") {
                    val net = dayRows.sumOf {
                        when (it.kind) { TxnKind.INCOME -> it.amountCents; TxnKind.EXPENSE -> -it.amountCents; else -> 0 }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(relativeDay(date), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        if (net != 0L) Text(Money.signed(net), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(dayRows, key = { it.id }) { TxnItem(it, filter.accountId) { onEditTxn(it.id) } }
            }
        }
    }
}
