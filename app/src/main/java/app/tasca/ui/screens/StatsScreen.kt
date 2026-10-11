package app.tasca.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tasca.data.MonthTotal
import app.tasca.data.TxnKind
import app.tasca.domain.Insights
import app.tasca.domain.Money
import app.tasca.domain.StatsRange
import app.tasca.domain.oneDecimal
import app.tasca.ui.AppViewModel
import app.tasca.ui.components.IconBubble
import app.tasca.ui.components.SectionCard
import app.tasca.ui.theme.LocalMoneyColors
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun StatsScreen(vm: AppViewModel, contentPadding: PaddingValues) {
    val range by vm.statsRange.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    val totals by vm.statsTotals.collectAsStateWithLifecycle()
    val insights by vm.insights.collectAsStateWithLifecycle()
    val balances by vm.balances.collectAsStateWithLifecycle()
    val selected by vm.selectedAccount.collectAsStateWithLifecycle()
    val ins = insights

    Column {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { AccountPicker(balances, selected, showBalance = false) { vm.selectedAccount.value = it } }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(3 to "3M", 6 to "6M", 12 to "1Y").forEach { (n, label) ->
                    FilterChip(range.preset == n, { vm.statsRange.value = StatsRange.last(n) }, label = { Text(label) })
                }
                FilterChip(
                    range.preset == null, { picking = true },
                    label = { Text(if (range.preset == null) shortRange(range) else "Custom") },
                    leadingIcon = { Icon(Icons.Outlined.DateRange, null, Modifier.size(16.dp)) },
                )
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ins == null) return@LazyColumn
            item { ThisMonth(ins) }
            item { Overview(ins, range) }
            if (ins.tips.isNotEmpty()) item { Tips(ins.tips) }
            item { Chart(range, totals) }
            if (ins.weights.isNotEmpty()) item { Weights(ins) }
        }
    }
    if (picking) RangePicker(range, onDismiss = { picking = false }) { vm.statsRange.value = it; picking = false }
}

@Composable
private fun ThisMonth(ins: Insights) {
    val p = ins.pace
    SectionCard(title = "This month") {
        Text(Money.format(p.spentCents), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("spent so far", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (p.usualMonthCents > 0) {
            val diff = p.spentCents - p.usualByTodayCents
            val color = if (diff > 0) LocalMoneyColors.current.expense else LocalMoneyColors.current.income
            Spacer(Modifier.height(10.dp))
            PaceBar(p.spentCents, p.usualByTodayCents, p.usualMonthCents, color)
            Spacer(Modifier.height(6.dp))
            Text(
                (if (diff > 0) "${Money.format(diff, true)} more" else "${Money.format(-diff, true)} less") +
                    " than usual by today · a usual month is ${Money.format(p.usualMonthCents, true)}",
                style = MaterialTheme.typography.bodySmall, color = color,
            )
        }
    }
}

/** Spent so far against a whole usual month; the tick marks where a usual month would be by today. */
@Composable
private fun PaceBar(spent: Long, usualToday: Long, usualMonth: Long, color: Color) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val marker = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val max = maxOf(usualMonth, spent).toFloat()
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        drawRoundRect(color, size = Size(size.width * spent / max, size.height), cornerRadius = r)
        val x = size.width * usualToday / max
        drawRect(marker, Offset(x - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
    }
}

@Composable
private fun Overview(ins: Insights, range: StatsRange) {
    val title = if (range.preset != null) "Last ${range.preset} months, on average" else "${longRange(range)}, on average"
    SectionCard(title = title) {
        if (ins.months == 0) {
            Text(
                "Pick a range with at least one finished month to see averages.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Figure("Earned", Money.format(ins.incomeCents, true), LocalMoneyColors.current.income)
            Figure("Spent", Money.format(ins.expenseCents, true), LocalMoneyColors.current.expense)
            Figure("Saved", Money.format(ins.savedCents, true), MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Figure("Saving rate", ins.savingsRate?.let { "$it%" } ?: "–", MaterialTheme.colorScheme.primary)
            if (ins.runwayMonths != null) Figure("Cash lasts", "${oneDecimal(ins.runwayMonths)} months", MaterialTheme.colorScheme.primary)
        }
        if (ins.runwayMonths != null) Text(
            "How long your money outside investments covers your usual spending.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun Figure(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun Tips(tips: List<String>) {
    SectionCard {
        tips.forEachIndexed { i, tip ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            Row {
                Icon(Icons.Outlined.Lightbulb, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(10.dp))
                Text(tip, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Chart(range: StatsRange, totals: List<MonthTotal>) {
    val income = LocalMoneyColors.current.income
    val expense = LocalMoneyColors.current.expense
    val shown = range.months
    val byMonth = shown.map { ym ->
        val k = ym.toString()
        Triple(
            ym,
            totals.filter { it.month == k && it.kind == TxnKind.INCOME }.sumOf { it.totalCents },
            totals.filter { it.month == k && it.kind == TxnKind.EXPENSE }.sumOf { it.totalCents },
        )
    }
    SectionCard(title = "Income and expenses") {
        val max = byMonth.maxOfOrNull { maxOf(it.second, it.third) }?.coerceAtLeast(1) ?: 1
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val slot = size.width / byMonth.size
            val bar = (slot * 0.32f).coerceAtMost(16.dp.toPx())
            byMonth.forEachIndexed { i, (_, inc, out) ->
                val x = slot * i + slot / 2
                val hIn = size.height * inc / max
                val hOut = size.height * out / max
                drawRoundRect(income, Offset(x - bar - 1, size.height - hIn), Size(bar, hIn), CornerRadius(3.dp.toPx()))
                drawRoundRect(expense, Offset(x + 1, size.height - hOut), Size(bar, hOut), CornerRadius(3.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            val fmt = DateTimeFormatter.ofPattern("MMM")
            byMonth.forEachIndexed { i, (ym, _, _) ->
                Text(
                    if (shown.size <= 8 || i % ((shown.size + 7) / 8) == 0) ym.format(fmt).take(3) else "",
                    Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Weights(ins: Insights) {
    SectionCard(title = "What your spending weighs") {
        Text(
            "Per month on average, as a share of your income.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        val top = ins.weights.first().monthlyCents.toFloat()
        ins.weights.take(8).forEach { w ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBubble(w.icon, Color(w.color), 32.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(w.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Money.format(w.monthlyCents, true), fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        Box(Modifier.fillMaxWidth(w.monthlyCents / top).height(6.dp).clip(CircleShape).background(Color(w.color)))
                    }
                    if (w.shareOfIncome != null && w.daysOfIncome != null) Text(
                        "${(w.shareOfIncome * 100).roundToInt()}% of income · ${days(w.daysOfIncome)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

private val MONTH = DateTimeFormatter.ofPattern("MMM")
private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy")

private fun shortRange(r: StatsRange): String = r.from.format(MONTH).take(3) + "–" + r.to.format(MONTH).take(3)

private fun longRange(r: StatsRange): String =
    if (r.from == r.to) r.from.format(MONTH_YEAR) else r.from.format(MONTH_YEAR) + " – " + r.to.format(MONTH_YEAR)

@Composable
private fun RangePicker(initial: StatsRange, onDismiss: () -> Unit, onPick: (StatsRange) -> Unit) {
    val now = YearMonth.now()
    val choices = (0L..35L).map { now.minusMonths(it) }
    var from by remember { mutableStateOf(initial.from) }
    var to by remember { mutableStateOf(initial.to) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Months to analyse") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MonthDropdown("From", from, choices) { from = it; if (to.isBefore(it)) to = it }
                MonthDropdown("To", to, choices.filter { !it.isBefore(from) }) { to = it }
                Text(
                    "Averages use only the finished months in this range.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(StatsRange(from, to)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MonthDropdown(label: String, value: YearMonth, choices: List<YearMonth>, onChange: (YearMonth) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(56.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { open = true }) {
                Text(value.format(MONTH_YEAR))
                Icon(Icons.Outlined.ArrowDropDown, null)
            }
            DropdownMenu(open, { open = false }, Modifier.heightIn(max = 320.dp)) {
                choices.forEach { m -> DropdownMenuItem({ Text(m.format(MONTH_YEAR)) }, { onChange(m); open = false }) }
            }
        }
    }
}

private fun days(d: Double): String =
    if (d < 1) "${(d * 24).roundToInt()} hours of income" else "${oneDecimal(d)} days of income"
