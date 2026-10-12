package app.tasca.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.previousOrSame
import kotlinx.datetime.todayIn
import kotlinx.datetime.yearMonth
import kotlin.time.Clock

enum class PeriodKind(val label: String) { WEEK("Week"), MONTH("Month"), YEAR("Year"), ALL("All") }

/** Today in the phone's time zone. */
fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

/** A closed date range [start, end] with a kind, so it can step forward and back. */
data class Period(val kind: PeriodKind, val start: LocalDate, val end: LocalDate) {
    fun shift(by: Long): Period = when (kind) {
        PeriodKind.WEEK -> of(kind, start.plus(by, DateTimeUnit.WEEK))
        PeriodKind.MONTH -> of(kind, start.plus(by, DateTimeUnit.MONTH))
        PeriodKind.YEAR -> of(kind, start.plus(by, DateTimeUnit.YEAR))
        PeriodKind.ALL -> this
    }

    fun label(): String = when (kind) {
        PeriodKind.WEEK -> "${dayAndMonth(start)} – ${dayAndMonth(end)}"
        PeriodKind.MONTH -> monthName(start.month).replaceFirstChar(Char::uppercase) + " " + start.year
        PeriodKind.YEAR -> start.year.toString()
        PeriodKind.ALL -> "All time"
    }

    companion object {
        val EPOCH: LocalDate = LocalDate(1970, 1, 1)
        val FAR: LocalDate = LocalDate(2999, 12, 31)

        fun of(kind: PeriodKind, anchor: LocalDate = today()): Period = when (kind) {
            PeriodKind.WEEK -> anchor.previousOrSame(DayOfWeek.MONDAY).let { Period(kind, it, it.plus(6, DateTimeUnit.DAY)) }
            PeriodKind.MONTH -> anchor.yearMonth.let { Period(kind, it.firstDay, it.lastDay) }
            PeriodKind.YEAR -> Period(kind, LocalDate(anchor.year, 1, 1), LocalDate(anchor.year, 12, 31))
            PeriodKind.ALL -> Period(kind, EPOCH, FAR)
        }
    }
}

/** The month's full name in the phone's language, as dates spell it: "ottobre". */
internal expect fun monthName(month: Month): String

/** Day and abbreviated month in the phone's language: "5 ott". */
internal expect fun dayAndMonth(date: LocalDate): String
