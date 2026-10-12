package app.tasca.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plusMonth
import kotlinx.datetime.yearMonth
import kotlin.math.roundToLong

/** Expense total of one category in one month ("2026-09"). */
data class CategoryMonth(
    val month: String,
    val categoryId: Long,
    val name: String,
    val color: Int,
    val icon: String,
    val totalCents: Long,
    val count: Int,
)

/** How much a category costs, measured against what comes in. */
data class Weight(
    val categoryId: Long,
    val name: String,
    val color: Int,
    val icon: String,
    val monthlyCents: Long,
    val shareOfIncome: Double?,
    val daysOfIncome: Double?,
)

data class Pace(val spentCents: Long, val usualByTodayCents: Long, val usualMonthCents: Long)

data class Insights(
    val months: Int,
    val incomeCents: Long,
    val expenseCents: Long,
    val savingsRate: Int?,
    val runwayMonths: Double?,
    val pace: Pace,
    val weights: List<Weight>,
    val tips: List<String>,
) {
    val savedCents: Long get() = incomeCents - expenseCents
}

/**
 * The months Stats looks at. A preset ends at the current month; a custom range can end anywhere.
 * Averages always use only the complete months in the range.
 */
data class StatsRange(val from: YearMonth, val to: YearMonth, val preset: Int? = null) {
    val months: List<YearMonth> get() = generateSequence(from) { it.plusMonth() }.takeWhile { it <= to }.toList()

    fun completeMonths(current: YearMonth): List<String> = months.filter { it < current }.map { it.toString() }

    companion object {
        fun last(n: Int, current: YearMonth = today().yearMonth) = StatsRange(current.minus(n, DateTimeUnit.MONTH), current, n)
    }
}

fun oneDecimal(v: Double): String = fixed(v, 1, ',')

/**
 * Averages use only complete months before the current one, so a half-finished month never drags them down;
 * the current month is compared against them separately. Empty months before the first transaction don't count.
 */
object InsightsCalc {
    fun compute(
        today: LocalDate,
        averaged: List<String>,
        incomeByMonth: Map<String, Long>,
        expenseByMonth: Map<String, Long>,
        categories: List<CategoryMonth>,
        liquidCents: Long?,
    ): Insights {
        val current = today.yearMonth
        val complete = averaged.dropWhile { (incomeByMonth[it] ?: 0L) == 0L && (expenseByMonth[it] ?: 0L) == 0L }.toSet()
        val months = complete.size.coerceAtLeast(1)
        val avgIncome = complete.sumOf { incomeByMonth[it] ?: 0 } / months
        val avgExpense = complete.sumOf { expenseByMonth[it] ?: 0 } / months
        val rate = if (avgIncome > 0) ((avgIncome - avgExpense) * 100 / avgIncome).toInt() else null
        val runway = if (liquidCents != null && avgExpense > 0) liquidCents.toDouble() / avgExpense else null

        val fraction = today.day.toDouble() / current.numberOfDays
        val pace = Pace(expenseByMonth[current.toString()] ?: 0, (avgExpense * fraction).roundToLong(), avgExpense)

        val past = categories.filter { it.month in complete }
        val byCategory = past.groupBy { it.categoryId }
        val dailyIncome = avgIncome / 30.0
        val weights = byCategory.map { (id, rows) ->
            val monthly = rows.sumOf { it.totalCents } / months
            Weight(
                id, rows.first().name, rows.first().color, rows.first().icon, monthly,
                if (avgIncome > 0) monthly.toDouble() / avgIncome else null,
                if (dailyIncome > 0) monthly / dailyIncome else null,
            )
        }.filter { it.monthlyCents > 0 }.sortedByDescending { it.monthlyCents }

        val tips = buildList {
            val thisMonth = categories.filter { it.month == current.toString() }
            thisMonth.mapNotNull { now ->
                val usual = (byCategory[now.categoryId]?.sumOf { it.totalCents } ?: 0) / months
                (now to usual).takeIf { usual >= 3_000 && now.totalCents > usual * 6 / 5 && now.totalCents - usual >= 2_000 }
            }.maxByOrNull { (now, usual) -> now.totalCents - usual }?.let { (now, usual) ->
                add("${now.name}: ${Money.format(now.totalCents, true)} this month, already more than your usual ${Money.format(usual, true)} for a whole month.")
            }
            if (pace.usualByTodayCents > 0 && pace.spentCents > pace.usualByTodayCents * 5 / 4) {
                add("You're spending faster than usual: ${Money.format(pace.spentCents, true)} so far, against about ${Money.format(pace.usualByTodayCents, true)} by this day.")
            }
            byCategory.mapNotNull { (_, rows) ->
                val count = rows.sumOf { it.count }
                val total = rows.sumOf { it.totalCents }
                val perMonth = count.toDouble() / months
                if (perMonth >= 4 && total / count < 4_000) Triple(rows.first().name, perMonth, total) else null
            }.maxByOrNull { it.third }?.let { (name, perMonth, total) ->
                val yearly = total * 12 / months
                add("$name: about ${perMonth.roundToLong()} times a month at ${Money.format(total / (perMonth * months).roundToLong(), true)} each, ${Money.format(yearly, true)} a year. Spending a quarter less saves ${Money.format(yearly / 4, true)}.")
            }
            if (runway != null) when {
                runway < 3 -> add("Your free cash covers ${oneDecimal(runway)} months of spending. A safety net of 3 to 6 months is a common goal.")
                runway > 9 -> add("You keep ${runway.roundToLong()} months of spending in cash. Part of it could be invested.")
            }
            if (rate != null) when {
                rate >= 30 -> add("You save $rate% of what you earn. Well done.")
                rate < 10 -> add("You save $rate% of what you earn. Aiming for 10 to 20% is a good start.")
            }
        }.take(3)

        return Insights(complete.size, avgIncome, avgExpense, rate, runway, pace, weights, tips)
    }
}
