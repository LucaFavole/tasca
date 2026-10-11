package app.tasca.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class PeriodKind(val label: String) { WEEK("Week"), MONTH("Month"), YEAR("Year"), ALL("All") }

/** A closed date range [start, end] with a kind, so it can step forward and back. */
data class Period(val kind: PeriodKind, val start: LocalDate, val end: LocalDate) {
    fun shift(by: Long): Period = when (kind) {
        PeriodKind.WEEK -> of(kind, start.plusWeeks(by))
        PeriodKind.MONTH -> of(kind, start.plusMonths(by))
        PeriodKind.YEAR -> of(kind, start.plusYears(by))
        PeriodKind.ALL -> this
    }

    fun label(): String = when (kind) {
        PeriodKind.WEEK -> "${start.format(SHORT)} – ${end.format(SHORT)}"
        PeriodKind.MONTH -> YearMonth.from(start).let {
            it.month.getDisplayName(TextStyle.FULL, Locale.getDefault()).replaceFirstChar(Char::uppercase) + " " + it.year
        }
        PeriodKind.YEAR -> start.year.toString()
        PeriodKind.ALL -> "All time"
    }

    companion object {
        private val SHORT = DateTimeFormatter.ofPattern("d MMM")
        val EPOCH: LocalDate = LocalDate.of(1970, 1, 1)
        val FAR: LocalDate = LocalDate.of(2999, 12, 31)

        fun of(kind: PeriodKind, anchor: LocalDate = LocalDate.now()): Period = when (kind) {
            PeriodKind.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .let { Period(kind, it, it.plusDays(6)) }
            PeriodKind.MONTH -> YearMonth.from(anchor).let { Period(kind, it.atDay(1), it.atEndOfMonth()) }
            PeriodKind.YEAR -> Period(kind, LocalDate.of(anchor.year, 1, 1), LocalDate.of(anchor.year, 12, 31))
            PeriodKind.ALL -> Period(kind, EPOCH, FAR)
        }
    }
}
