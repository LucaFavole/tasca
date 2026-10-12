package app.tasca

import app.tasca.domain.CategoryMonth
import app.tasca.domain.InsightsCalc
import app.tasca.domain.Position
import app.tasca.domain.StatsRange
import app.tasca.domain.Trade
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth
import kotlin.math.roundToLong

class InsightsTest {
    private val today = LocalDate(2026, 10, 11)
    private val past = listOf("2026-07", "2026-08", "2026-09")

    private fun cat(month: String, name: String, cents: Long, count: Int, id: Long = name.hashCode().toLong()) =
        CategoryMonth(month, id, name, 0, "", cents, count)

    private val categories = past.flatMap { listOf(cat(it, "Casa", 70_000, 1), cat(it, "Svago", 30_000, 9)) } +
        listOf(cat("2026-10", "Svago", 40_000, 6), cat("2026-10", "Casa", 0, 0))

    private fun compute(liquid: Long? = 300_000) = InsightsCalc.compute(
        today, past,
        past.associateWith { 300_000L },
        past.associateWith { 100_000L } + ("2026-10" to 40_000L),
        categories, liquid,
    )

    @Test fun averagesUseOnlyCompleteMonths() {
        val i = compute()
        assertEquals(300_000, i.incomeCents)
        assertEquals(100_000, i.expenseCents)
        assertEquals(66, i.savingsRate)
        assertEquals(3.0, i.runwayMonths!!, 1e-9)
    }

    @Test fun paceComparesWithTheUsualMonthSoFar() {
        val p = compute().pace
        assertEquals(40_000, p.spentCents)
        assertEquals((100_000 * 11.0 / 31).roundToLong(), p.usualByTodayCents)
    }

    @Test fun weightIsShareAndDaysOfIncome() {
        val casa = compute().weights.first()
        assertEquals("Casa", casa.name)
        assertEquals(70_000, casa.monthlyCents)
        assertEquals(0.2333, casa.shareOfIncome!!, 1e-3)
        assertEquals(7.0, casa.daysOfIncome!!, 1e-9)
    }

    @Test fun tipsFlagACategoryAlreadyAboveAUsualMonthAndAHabit() {
        val tips = compute().tips
        assertTrue(tips.first().startsWith("Svago:") && tips.first().contains("already more than"), tips.first())
        assertTrue(tips.any { it.contains("times a month") }, tips.joinToString())
        assertTrue(tips.size <= 3)
    }

    @Test fun noAlarmWhenTheUsualAmountIsTiny() {
        val tiny = listOf("2026-07", "2026-08", "2026-09").map { cat(it, "Sport", 300, 1, id = 9) } + cat("2026-10", "Sport", 9_000, 1, id = 9)
        val tips = InsightsCalc.compute(today, past, emptyMap(), emptyMap(), tiny, null).tips
        assertTrue(tips.none { it.startsWith("Sport") }, tips.joinToString())
    }

    @Test fun customRangeAveragesOnlyItsFinishedMonths() {
        val r = StatsRange(YearMonth(2026, 8), YearMonth(2026, 12))
        assertEquals(listOf("2026-08", "2026-09"), r.completeMonths(YearMonth(2026, 10)))
        assertEquals(5, r.months.size)
        val i = InsightsCalc.compute(today, r.completeMonths(today.yearMonth), mapOf("2026-08" to 200_000L, "2026-09" to 400_000L), emptyMap(), emptyList(), null)
        assertEquals(2, i.months)
        assertEquals(300_000, i.incomeCents)
    }

    @Test fun emptyMonthsBeforeTheFirstTransactionDontLowerAverages() {
        val i = InsightsCalc.compute(
            today, listOf("2026-06", "2026-07", "2026-08", "2026-09"),
            mapOf("2026-08" to 200_000L, "2026-09" to 200_000L), mapOf("2026-08" to 100_000L), emptyList(), null,
        )
        assertEquals(2, i.months)
        assertEquals(200_000, i.incomeCents)
        assertEquals(50_000, i.expenseCents)
    }

    @Test fun rangeWithNoFinishedMonthHasNoAverages() {
        val i = InsightsCalc.compute(today, emptyList(), mapOf("2026-10" to 100_000L), mapOf("2026-10" to 50_000L), emptyList(), null)
        assertEquals(0, i.months)
        assertEquals(0, i.incomeCents)
        assertNull(i.savingsRate)
    }

    @Test fun noRunwayForASingleAccount() {
        assertNull(compute(liquid = null).runwayMonths)
    }

    @Test fun taxAppliesToTheGainAfterTheFee() {
        val o = Trade.sale(Position(0.0, 0, realizedCents = 10_100), 10.0, 100.0, 100, 26.0)
        assertEquals(100_000, o.grossCents)
        assertEquals(2_600, o.taxCents)
        assertEquals(100_000 - 100 - 2_600, o.receivedCents)
    }

    @Test fun noTaxOnALoss() {
        val o = Trade.sale(Position(0.0, 0, realizedCents = -5_000), 10.0, 100.0, 100, 26.0)
        assertEquals(0, o.taxCents)
        assertEquals(99_900, o.receivedCents)
    }
}
