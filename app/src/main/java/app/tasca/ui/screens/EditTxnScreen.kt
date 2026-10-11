package app.tasca.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import app.tasca.data.Category
import app.tasca.data.CategoryKind
import app.tasca.data.Defaults
import app.tasca.data.Recurring
import app.tasca.data.Txn
import app.tasca.data.TxnKind
import app.tasca.domain.Calc
import app.tasca.domain.Frequency
import app.tasca.domain.Money
import app.tasca.domain.ReceiptScan
import app.tasca.domain.Spoken
import app.tasca.domain.guessCategory
import app.tasca.ui.rememberReceiptScanner
import app.tasca.ui.AppViewModel
import app.tasca.ui.components.IconBubble
import app.tasca.ui.components.kindColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Shared draft for a transaction or a recurring payment; both screens edit the same fields. */
class TxnDraft(kind: TxnKind) {
    var kind by mutableStateOf(kind)
    var expr by mutableStateOf("")
    var accountId by mutableStateOf<Long?>(null)
    var toAccountId by mutableStateOf<Long?>(null)
    var categoryId by mutableStateOf<Long?>(null)
    var date by mutableStateOf(LocalDate.now())
    var note by mutableStateOf("")

    val amount: Long? get() = Calc.evaluate(expr)

    /** Why the draft can't be saved yet, or null when it can. */
    fun problem(): String? {
        val a = amount
        return when {
            a == null || a <= 0 -> "Enter an amount"
            accountId == null -> "Pick an account"
            kind == TxnKind.TRANSFER && toAccountId == null -> "Pick the destination account"
            kind == TxnKind.TRANSFER && toAccountId == accountId -> "Choose two different accounts"
            kind != TxnKind.TRANSFER && categoryId == null -> "Pick a category"
            else -> null
        }
    }

    fun load(t: Txn) {
        kind = t.kind; expr = Money.plain(t.amountCents).replace('.', ','); accountId = t.accountId
        toAccountId = t.toAccountId; categoryId = t.categoryId; date = t.date; note = t.note
    }

    fun load(r: Recurring) {
        kind = r.kind; expr = Money.plain(r.amountCents).replace('.', ','); accountId = r.accountId
        toAccountId = r.toAccountId; categoryId = r.categoryId; date = r.startDate; note = r.name
    }

    fun load(s: Spoken) {
        kind = s.kind; expr = s.amountCents?.let { Money.plain(it).replace('.', ',') }.orEmpty()
        categoryId = s.categoryId; date = s.date; note = s.note
    }

    /** Fills what the receipt showed and keeps the rest; returns false when no total was found. */
    fun load(r: ReceiptScan, categories: List<Category>): Boolean {
        kind = TxnKind.EXPENSE
        r.amountCents?.let { expr = Money.plain(it).replace('.', ',') }
        r.date?.let { date = it }
        r.store?.let { store ->
            note = store
            guessCategory(store.lowercase().split(' '), categories.filter { it.kind == CategoryKind.EXPENSE })?.let { categoryId = it.id }
        }
        return r.amountCents != null
    }

    fun toTxn(id: Long, recurringId: Long?) = Txn(
        id = id, kind = kind, amountCents = amount!!, date = date, accountId = accountId!!,
        toAccountId = if (kind == TxnKind.TRANSFER) toAccountId else null,
        categoryId = if (kind == TxnKind.TRANSFER) null else categoryId,
        note = note.trim(), recurringId = recurringId,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditTxnScreen(vm: AppViewModel, txnId: Long?, initialKind: TxnKind = TxnKind.EXPENSE, onDone: () -> Unit) {
    val draft = remember { TxnDraft(initialKind) }
    var recurringId by remember { mutableStateOf<Long?>(null) }
    var repeat by rememberSaveable { mutableStateOf<Frequency?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val selected by vm.selectedAccount.collectAsStateWithLifecycle()
    var reading by remember { mutableStateOf(false) }
    val scanReceipt = rememberReceiptScanner(onStart = { reading = true }) { r ->
        reading = false
        if (r != null) error = if (draft.load(r, vm.categories.value)) null else "Couldn't find the total: type it in"
    }

    LaunchedEffect(txnId) {
        if (txnId != null) vm.txn(txnId)?.let { draft.load(it); recurringId = it.recurringId }
        else {
            draft.accountId = selected
            vm.spoken?.let { draft.load(it); vm.spoken = null }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(if (txnId == null) "New transaction" else "Edit transaction") },
            navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            actions = {
                if (txnId != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete") }
                else if (reading) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                else IconButton(onClick = scanReceipt) { Icon(Icons.Outlined.DocumentScanner, "Scan a receipt") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            DraftFields(vm, draft)
            if (txnId == null) {
                Label("Repeat")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(repeat == null, { repeat = null }, label = { Text("Never") })
                    listOf(Frequency.WEEKLY, Frequency.MONTHLY, Frequency.YEARLY).forEach { f ->
                        FilterChip(repeat == f, { repeat = f }, label = { Text(f.label) })
                    }
                }
                if (repeat != null) {
                    Text(
                        "Saved as a regular payment starting on the chosen date. It's added automatically on each due date.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else if (recurringId != null) {
                Text(
                    "Created by a regular payment. Editing changes only this occurrence.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        Keypad(
            draft,
            saveLabel = if (repeat != null) "Save regular payment" else "Save",
            error = error,
            onSave = dropUnlessResumed {
                error = draft.problem()
                if (error != null) return@dropUnlessResumed
                val freq = repeat
                if (freq != null) {
                    vm.saveRecurring(
                        Recurring(
                            name = draft.note.trim().ifEmpty { vm.categories.value.firstOrNull { it.id == draft.categoryId }?.name ?: "Transfer" },
                            kind = draft.kind, amountCents = draft.amount!!, accountId = draft.accountId!!,
                            toAccountId = if (draft.kind == TxnKind.TRANSFER) draft.toAccountId else null,
                            categoryId = if (draft.kind == TxnKind.TRANSFER) null else draft.categoryId,
                            frequency = freq, startDate = draft.date,
                        )
                    )
                } else {
                    vm.saveTxn(draft.toTxn(txnId ?: 0, recurringId))
                }
                onDone()
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this transaction?") },
            confirmButton = { TextButton(onClick = dropUnlessResumed { vm.deleteTxn(txnId!!); confirmDelete = false; onDone() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun Label(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DraftFields(vm: AppViewModel, draft: TxnDraft, showDate: Boolean = true) {
    val balances by vm.balances.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf(false) }
    val accounts = balances.filter { !it.deleted || it.id == draft.accountId || it.id == draft.toAccountId }

    LaunchedEffect(accounts.size) {
        if (draft.accountId == null) draft.accountId = accounts.firstOrNull()?.id
    }

    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf(TxnKind.EXPENSE to "Expense", TxnKind.INCOME to "Income", TxnKind.TRANSFER to "Transfer").forEachIndexed { i, (k, label) ->
            SegmentedButton(draft.kind == k, {
                if (draft.kind != k) draft.categoryId = null
                draft.kind = k
            }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(label) }
        }
    }

    val value = draft.amount
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (value != null) Money.format(value) else Money.format(0),
            fontSize = 40.sp, fontWeight = FontWeight.Bold, color = kindColor(draft.kind), maxLines = 1,
        )
        if (draft.expr.any(Calc::isOperator)) {
            Text(draft.expr, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    Label(if (draft.kind == TxnKind.TRANSFER) "From account" else "Account")
    ChipRow(accounts.map { it.id to it.name }, draft.accountId) {
        draft.accountId = it
        if (draft.toAccountId == it) draft.toAccountId = null
    }
    if (draft.kind == TxnKind.TRANSFER) {
        Label("To account")
        ChipRow(accounts.map { it.id to it.name }, draft.toAccountId, disabled = draft.accountId) { draft.toAccountId = it }
    } else {
        Label("Category")
        val kind = if (draft.kind == TxnKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.filter { it.kind == kind }.forEach { c ->
                CategoryTile(c, draft.categoryId == c.id) { draft.categoryId = c.id }
            }
            Column(
                Modifier.width(76.dp).clickable { newCategory = true }.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(2.dp)) {
                    Icon(Icons.Outlined.Add, null, Modifier.padding(10.dp))
                }
                Text("New", style = MaterialTheme.typography.labelSmall)
            }
        }
        if (newCategory) {
            NewCategoryDialog(onDismiss = { newCategory = false }) { name ->
                vm.saveCategory(Defaults.category(name, kind, categories.size))
                newCategory = false
            }
        }
    }

    if (showDate) {
        Label("Date")
        val today = LocalDate.now()
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(draft.date == today, { draft.date = today }, label = { Text("Today") })
            FilterChip(draft.date == today.minusDays(1), { draft.date = today.minusDays(1) }, label = { Text("Yesterday") })
            val custom = draft.date != today && draft.date != today.minusDays(1)
            AssistChip(
                onClick = { picking = true },
                label = { Text(if (custom) relativeDay(draft.date) else "Pick…") },
                leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null) },
            )
        }
    }

    Label(if (showDate) "Note" else "Name")
    OutlinedTextField(
        draft.note, { draft.note = it }, Modifier.fillMaxWidth(),
        placeholder = { Text(if (showDate) "What was it for?" else "e.g. Rent, Spotify, Salary") },
        singleLine = true, shape = MaterialTheme.shapes.medium,
    )

    if (picking) {
        val state = rememberDatePickerState(draft.date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { draft.date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

/** One scrollable line of chips, so many accounts never push the rest of the form off screen. */
@Composable
fun ChipRow(items: List<Pair<Long, String>>, selected: Long?, disabled: Long? = null, onSelect: (Long) -> Unit) {
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(Unit) {
        val i = items.indexOfFirst { it.first == selected }
        if (i > 0) state.scrollToItem(i)
    }
    androidx.compose.foundation.lazy.LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(items.size) { i ->
            val (id, name) = items[i]
            FilterChip(selected == id && id != disabled, { onSelect(id) }, label = { Text(name) }, enabled = id != disabled)
        }
    }
}

@Composable
fun CategoryTile(c: Category, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(76.dp).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBubble(c.icon, Color(c.color), 44.dp, selected)
        Text(
            c.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun NewCategoryDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New category") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Name") }) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name.trim()) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun Keypad(draft: TxnDraft, saveLabel: String, error: String?, onSave: () -> Unit) {
    @OptIn(ExperimentalLayoutApi::class)
    val showKeys = !WindowInsets.isImeVisible
    fun press(k: String) {
        val e = draft.expr
        draft.expr = when (k) {
            "⌫" -> e.dropLast(1)
            "," -> {
                val lastNumber = e.takeLastWhile { !Calc.isOperator(it) }
                if (lastNumber.contains(',')) e else e + (if (lastNumber.isEmpty()) "0," else ",")
            }
            in listOf("+", "-", "×", "÷") -> when {
                e.isEmpty() -> e
                Calc.isOperator(e.last()) -> e.dropLast(1) + k
                else -> e + k
            }
            else -> {
                val decimals = e.takeLastWhile { !Calc.isOperator(it) }.substringAfter(',', "")
                if (e.takeLastWhile { !Calc.isOperator(it) }.contains(',') && decimals.length >= 2) e else e + k
            }
        }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 2.dp) {
        Column(Modifier.navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (showKeys) listOf(
                listOf("7", "8", "9", "÷"),
                listOf("4", "5", "6", "×"),
                listOf("1", "2", "3", "-"),
                listOf(",", "0", "⌫", "+"),
            ).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { k ->
                        val op = Calc.isOperator(k[0])
                        FilledTonalButton(
                            onClick = { press(k) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (op) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = if (op) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                            ),
                        ) {
                            if (k == "⌫") Icon(Icons.AutoMirrored.Outlined.Backspace, "Backspace")
                            else Text(if (k == "-") "−" else k, fontSize = 20.sp)
                        }
                    }
                }
            }
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(48.dp), shape = MaterialTheme.shapes.medium) {
                Text(saveLabel, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
