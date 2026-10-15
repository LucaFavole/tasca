package app.tasca.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tasca.data.TxnKind
import androidx.compose.material.icons.outlined.Check
import app.tasca.data.Account
import app.tasca.data.AccountBalance
import app.tasca.data.Category
import app.tasca.data.CategoryKind
import app.tasca.data.Defaults
import app.tasca.data.Palette
import app.tasca.domain.Money
import app.tasca.io.LedgerSheets
import app.tasca.ui.AppIcons
import app.tasca.ui.AppViewModel
import app.tasca.ui.CategoryUsage
import app.tasca.ui.Platform
import app.tasca.ui.components.IconBubble
import app.tasca.ui.components.SectionCard
import app.tasca.ui.theme.ColorTheme
import app.tasca.ui.theme.ThemeMode
import app.tasca.ui.theme.availableColorThemes
import app.tasca.ui.theme.scheme
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubScreen(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Column {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            actions = { actions() },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        content()
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, platform: Platform, onBack: () -> Unit, onCategories: () -> Unit) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    val colors by vm.colors.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    var confirmErase by remember { mutableStateOf(false) }
    var editCurrency by remember { mutableStateOf(false) }
    var pickDefault by remember { mutableStateOf<TxnKind?>(null) }
    val balances by vm.balances.collectAsStateWithLifecycle()
    val defaults by vm.defaultAccounts.collectAsStateWithLifecycle()
    val defaultNames = remember(balances, defaults) {
        TxnKind.entries.associateWith { k -> balances.firstOrNull { it.id == vm.defaultAccount(k) }?.name ?: "None" }
    }
    val reminder by vm.reminder.collectAsStateWithLifecycle()
    val notifications = platform.rememberNotificationAsk(onDenied = { vm.setReminder(false) })
    val importer = platform.rememberImportPicker(vm::importExcel)
    val exporter = platform.rememberExportPicker(vm::exportExcel)
    val scope = rememberCoroutineScope()

    SubScreen("Settings", onBack) {
        LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard {
                    NavRow("Categories", "Edit names, icons and colours", onCategories)
                }
            }
            item {
                SectionCard(title = "Default accounts") {
                    NavRow("Expenses", defaultNames.getValue(TxnKind.EXPENSE)) { pickDefault = TxnKind.EXPENSE }
                    NavRow("Income", defaultNames.getValue(TxnKind.INCOME)) { pickDefault = TxnKind.INCOME }
                }
            }
            item {
                SectionCard(title = "Excel") {
                    ActionRow(Icons.Outlined.FileUpload, "Import from Excel", "Your old app's file or a Tasca backup. Anything already present is skipped.") {
                        importer()
                    }
                    ActionRow(Icons.Outlined.FileDownload, "Export to Excel", "Everything: transactions, accounts, opening balances and investments. Keep it as a backup.") {
                        exporter(vm.exportFileName())
                    }
                }
            }
            item {
                SectionCard(title = "Appearance") {
                    ThemePicker(colors, vm::setColors)
                    Spacer(Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { i, m ->
                            SegmentedButton(
                                theme == m, { vm.setTheme(m) }, SegmentedButtonDefaults.itemShape(i, 3), enabled = !colors.darkOnly,
                            ) {
                                Text(m.name.lowercase().replaceFirstChar(Char::uppercase))
                            }
                        }
                    }
                    if (colors.darkOnly) Text(
                        "${colors.label} is always dark",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    NavRow("Currency", currency) { editCurrency = true }
                }
            }
            item {
                SectionCard {
                    ListItem(
                        headlineContent = { Text("Daily reminder") },
                        supportingContent = { Text("At 23:00, asks if you recorded the day's expenses") },
                        trailingContent = {
                            Switch(reminder, { on ->
                                vm.setReminder(on)
                                // Undispatched, so Android asks within the tap as before; iOS has to wait for the answer.
                                if (on) scope.launch(start = CoroutineStart.UNDISPATCHED) { if (!notifications.allowed()) notifications.ask() }
                            })
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            item {
                SectionCard {
                    ActionRow(Icons.Outlined.DeleteForever, "Erase all data", "Removes every account, category and transaction") { confirmErase = true }
                }
            }
            item {
                Text(
                    "Tasca 1.0 · no ads, no tracking, no account. Your data stays on this phone: export to Excel to back it up.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("Erase everything?") },
            text = { Text("This cannot be undone. Export to Excel first if you want a backup.") },
            confirmButton = { TextButton(onClick = { vm.eraseAll(); confirmErase = false }) { Text("Erase", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text("Cancel") } },
        )
    }
    pickDefault?.let { kind ->
        AlertDialog(
            onDismissRequest = { pickDefault = null },
            title = { Text(if (kind == TxnKind.INCOME) "New income goes to" else "New expenses go to") },
            text = {
                Column {
                    balances.filter { !it.deleted }.forEach { a ->
                        ListItem(
                            headlineContent = { Text(a.name) },
                            trailingContent = { if (a.id == vm.defaultAccount(kind)) Icon(Icons.Outlined.Check, null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable {
                                vm.setDefaultAccount(kind, a.id)
                                pickDefault = null
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickDefault = null }) { Text("Cancel") } },
        )
    }

    if (editCurrency) {
        var code by remember { mutableStateOf(currency) }
        AlertDialog(
            onDismissRequest = { editCurrency = false },
            title = { Text("Currency") },
            text = {
                Column {
                    OutlinedTextField(code, { code = it.uppercase().take(3) }, singleLine = true, label = { Text("ISO code, e.g. EUR, USD, CHF") })
                    Text("Only changes how amounts are shown.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { vm.setCurrency(code); editCurrency = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editCurrency = false }) { Text("Cancel") } },
        )
    }
}

/** Each colour theme as a swatch of its background and accent, three to a row. */
@Composable
private fun ThemePicker(selected: ColorTheme, onPick: (ColorTheme) -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        availableColorThemes().chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { t ->
                    val look = t.scheme(dark)
                    val ring = if (t == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    Column(
                        Modifier.weight(1f).clip(MaterialTheme.shapes.medium).clickable { onPick(t) }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(48.dp).border(if (t == selected) 3.dp else 1.dp, ring, CircleShape).padding(4.dp)
                                .clip(CircleShape).background(look.background),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(18.dp).clip(CircleShape).background(look.primary))
                        }
                        Text(
                            t.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp),
                            fontWeight = if (t == selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
    )
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StylePicker(icon: String, color: Int, onIcon: (String) -> Unit, onColor: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Colour", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Palette.colors.forEach { c ->
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Color(c))
                        .then(if (c == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                        .clickable { onColor(c) },
                )
            }
        }
        Text("Icon", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AppIcons.all.keys.forEach { k ->
                Box(Modifier.clip(CircleShape).clickable { onIcon(k) }) { IconBubble(k, Color(color), 36.dp, selected = k == icon) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(vm: AppViewModel, onBack: () -> Unit) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    var kind by remember { mutableStateOf(CategoryKind.EXPENSE) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Pair<Category, CategoryUsage>?>(null) }
    val scope = rememberCoroutineScope()

    SubScreen("Categories", onBack, actions = {
        IconButton(onClick = { editing = Defaults.category("", kind, categories.size).copy(icon = "category") }) { Icon(Icons.Outlined.Add, "Add category") }
    }) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(CategoryKind.EXPENSE to "Expenses", CategoryKind.INCOME to "Income").forEachIndexed { i, (k, l) ->
                    SegmentedButton(kind == k, { kind = k }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
                }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.filter { it.kind == kind }.forEach { c -> CategoryTile(c, false) { editing = c } }
            }
        }
    }

    editing?.let { c ->
        var name by remember(c) { mutableStateOf(c.name) }
        var icon by remember(c) { mutableStateOf(c.icon) }
        var color by remember(c) { mutableStateOf(c.color) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (c.id == 0L) "New category" else "Edit category") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    StylePicker(icon, color, { icon = it }, { color = it })
                    if (c.id != 0L) TextButton(onClick = {
                        scope.launch { deleting = c to vm.categoryUsage(c.id); editing = null }
                    }) { Text("Delete category", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { vm.saveCategory(c.copy(name = name.trim(), icon = icon, color = color)); editing = null }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { (c, used) ->
        var target by remember(c) { mutableStateOf<Long?>(null) }
        val others = categories.filter { it.kind == c.kind && it.id != c.id }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${c.name}?") },
            text = {
                Column {
                    if (used.txns == 0) Text("No transactions use it.")
                    else {
                        Text("${used.txns} transactions use it. Move them to:")
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            others.forEach { o -> FilterChip(target == o.id, { target = o.id }, label = { Text(o.name) }) }
                        }
                    }
                    if (used.recurring > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (used.recurring == 1) "Its regular payment will be deleted too."
                            else "Its ${used.recurring} regular payments will be deleted too.",
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = used.txns == 0 || target != null, onClick = { vm.deleteCategory(c.id, target); deleting = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}
