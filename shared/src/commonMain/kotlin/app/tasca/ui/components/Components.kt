package app.tasca.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tasca.data.TxnKind
import app.tasca.data.TxnRow
import app.tasca.domain.Money
import app.tasca.ui.AppIcons
import app.tasca.ui.theme.LocalMoneyColors

@Composable
fun IconBubble(icon: String?, color: Color, size: Dp = 40.dp, selected: Boolean = false) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(if (selected) color else color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons[icon], null, tint = if (selected) Color.White else color, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    action?.invoke()
                }
                Spacer(Modifier.height(8.dp))
            }
            content()
        }
    }
}

@Composable
fun kindColor(kind: TxnKind): Color = when (kind) {
    TxnKind.EXPENSE -> LocalMoneyColors.current.expense
    TxnKind.INCOME -> LocalMoneyColors.current.income
    TxnKind.TRANSFER -> LocalMoneyColors.current.transfer
}

@Composable
fun TxnItem(row: TxnRow, perspectiveAccountId: Long? = null, onClick: () -> Unit) {
    val transfer = row.kind == TxnKind.TRANSFER
    val color = if (transfer) LocalMoneyColors.current.transfer else Color(row.categoryColor ?: 0xFF888888.toInt())
    val sign = when {
        row.kind == TxnKind.INCOME -> "+"
        row.kind == TxnKind.EXPENSE -> "−"
        perspectiveAccountId == row.toAccountId -> "+"
        perspectiveAccountId == row.accountId -> "−"
        else -> ""
    }
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (transfer) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.SwapHoriz, null, tint = color)
            }
        } else IconBubble(row.categoryIcon, color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (transfer) "${row.accountName} → ${row.toAccountName}" else row.categoryName.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.recurringId != null) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Outlined.Repeat, "Recurring", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val sub = listOfNotNull(row.note.ifBlank { null }, if (transfer) null else row.accountName).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            sign + Money.format(row.amountCents),
            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
            color = if (transfer) MaterialTheme.colorScheme.onSurface else kindColor(row.kind),
        )
    }
}

data class Slice(val value: Long, val color: Color)

@Composable
fun Donut(slices: List<Slice>, modifier: Modifier = Modifier, thickness: Dp = 22.dp, center: @Composable () -> Unit) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = thickness.toPx()
            val d = size.minDimension - stroke
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            val total = slices.sumOf { it.value }.toFloat()
            drawArc(track, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke))
            if (total <= 0f) return@Canvas
            val gap = if (slices.size > 1) 2f else 0f
            var start = -90f
            for (s in slices) {
                val sweep = 360f * s.value / total
                if (sweep - gap > 0.3f) {
                    drawArc(s.color, start + gap / 2, sweep - gap, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Butt))
                }
                start += sweep
            }
        }
        center()
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
    )
}

@Composable
fun LegendRow(icon: String?, color: Color, name: String, pct: Int?, amount: Long, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBubble(icon, color, 36.dp)
        Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (pct != null) Text("$pct%", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(Money.format(amount), fontWeight = FontWeight.SemiBold)
    }
}
