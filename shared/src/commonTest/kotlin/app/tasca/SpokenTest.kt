package app.tasca

import app.tasca.data.Category
import app.tasca.data.CategoryKind
import app.tasca.data.TxnKind
import app.tasca.domain.SpokenParser
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

class SpokenTest {
    private val today = LocalDate(2026, 10, 11)
    private val categories = listOf(
        Category(1, "Alimentari", CategoryKind.EXPENSE, 0, "cart"),
        Category(2, "Caffè", CategoryKind.EXPENSE, 0, "coffee"),
        Category(3, "Svago", CategoryKind.EXPENSE, 0, "celebration"),
        Category(4, "Altro", CategoryKind.EXPENSE, 0, "other"),
        Category(5, "Stipendio", CategoryKind.INCOME, 0, "work"),
    )

    private fun parse(s: String) = SpokenParser.parse(s, categories, today)

    @Test fun amountCategoryAndNoteFromAShortSentence() {
        val s = parse("12,50 euro spesa esselunga")
        assertEquals(1250L, s.amountCents)
        assertEquals(TxnKind.EXPENSE, s.kind)
        assertEquals(1L, s.categoryId)
        assertEquals("spesa esselunga", s.note)
        assertEquals(today, s.date)
    }

    @Test fun euroAndCentsSpokenSeparately() {
        assertEquals(350L, parse("3 euro e 50 caffè").amountCents)
        assertEquals(2L, parse("3 euro e 50 caffè").categoryId)
        assertEquals(1200L, parse("12 € cinema").amountCents)
    }

    @Test fun incomeAndYesterday() {
        val s = parse("ieri ho ricevuto 1.200 euro di stipendio")
        assertEquals(120_000L, s.amountCents)
        assertEquals(TxnKind.INCOME, s.kind)
        assertEquals(5L, s.categoryId)
        assertEquals(today.minus(1, DateTimeUnit.DAY), s.date)
    }

    @Test fun unknownCategoryAndMissingAmountAreLeftForTheUser() {
        val s = parse("regalo per marco")
        assertNull(s.amountCents)
        assertNull(s.categoryId)
        assertEquals("regalo marco", s.note)
    }
}
