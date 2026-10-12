package app.tasca

import app.tasca.domain.isoCurrency
import app.tasca.ui.formatDate
import app.tasca.ui.formatQuantity
import app.tasca.ui.formatTwoDecimals
import app.tasca.ui.plainDecimal
import kotlinx.datetime.LocalDateTime
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The screens' dates and numbers read as they did when the screens called java.time and java.text themselves. */
class FormatTest {
    private val before = Locale.getDefault()

    @BeforeTest fun italian() = Locale.setDefault(Locale.ITALY)

    @AfterTest fun restore() = Locale.setDefault(before)

    @Test fun dates() {
        val time = LocalDateTime(2026, 10, 5, 9, 7)
        assertEquals("lun 5 ott", formatDate(time, "EEE d MMM"))
        assertEquals("ott", formatDate(time, "MMM"))
        assertEquals("ott 2026", formatDate(time, "MMM yyyy"))
        assertEquals("5 ott 09:07", formatDate(time, "d MMM HH:mm"))
    }

    @Test fun quantitiesGroupAndKeepUpToFourDecimals() {
        assertEquals("1.234,5679", formatQuantity(1234.56789))
        assertEquals("3", formatQuantity(3.0))
        assertEquals("0,125", formatQuantity(0.125))
    }

    @Test fun twoDecimalsRoundHalfUpOnTheWrittenValue() {
        assertEquals("12,35", formatTwoDecimals(12.345))
        assertEquals("-0,50", formatTwoDecimals(-0.5))
        assertEquals("1234,00", formatTwoDecimals(1234.0))
    }

    @Test fun plainDecimalsRoundTheExactBinaryValue() {
        assertEquals("41,678225", plainDecimal(41.678225, 6))
        assertEquals("265,52", plainDecimal(265.52, 6))
        assertEquals("0,3", plainDecimal(0.1 + 0.2, 6))
        // 0.1234565 is stored as 0.12345649999…, so it rounds down.
        assertEquals("0,123456", plainDecimal(0.1234565, 6))
        assertEquals("0", plainDecimal(0.0, 6))
    }

    @Test fun currencies() {
        assertEquals("USD", isoCurrency("usd"))
        assertNull(isoCurrency("XYZ"))
    }
}
