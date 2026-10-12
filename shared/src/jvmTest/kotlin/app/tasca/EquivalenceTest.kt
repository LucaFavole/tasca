package app.tasca

import app.tasca.domain.Money
import app.tasca.domain.fixed
import app.tasca.domain.oneDecimal
import app.tasca.io.Yahoo
import app.tasca.io.parseDate
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Compares the common Kotlin code with the java.math, java.text and java.time code it replaced,
 * which the app ran before the shared module, over large random inputs.
 */
class EquivalenceTest {
    private val random = Random(20261012)

    @Test fun moneyConversionsMatchBigDecimal() {
        repeat(200_000) {
            val d = when (random.nextInt(4)) {
                0 -> random.nextLong(-10_000_000, 10_000_000) / 1000.0
                1 -> random.nextLong(-1_000_000, 1_000_000) / 100.0 + random.nextDouble(-1e-9, 1e-9)
                2 -> random.nextDouble(-1e9, 1e9)
                else -> random.nextDouble(-1.0, 1.0) * Math.pow(10.0, random.nextInt(-8, 12).toDouble())
            }
            assertEquals(BigDecimal.valueOf(d).setScale(2, RoundingMode.HALF_UP).unscaledValue().toLong(), Money.fromDouble(d), "$d")
            val cents = random.nextLong(-(1L shl 53), 1L shl 53) shr random.nextInt(0, 50)
            assertEquals(BigDecimal.valueOf(cents, 2).toDouble(), Money.toDouble(cents), "$cents")
            assertEquals(BigDecimal.valueOf(cents, 2).stripTrailingZeros().toPlainString(), Money.plain(cents), "$cents")
        }
    }

    @Test fun decimalsMatchStringFormat() {
        repeat(200_000) {
            val d = when (random.nextInt(4)) {
                0 -> random.nextInt(-2000, 20_000) / 20.0
                1 -> random.nextInt(-200_000, 200_000) / 200.0
                2 -> random.nextDouble(-1e6, 1e8)
                else -> random.nextDouble(0.0, 1.0) * Math.pow(10.0, random.nextInt(-6, 9).toDouble())
            }
            assertEquals(String.format(Locale.ITALY, "%.1f", d), oneDecimal(d), "$d")
            assertEquals(String.format(Locale.ITALY, "%.4f", d), fixed(d, 4, ','), "$d")
        }
    }

    @Test fun datesMatchDateTimeFormatter() {
        val formats = listOf("yyyy-MM-dd", "d/M/yyyy", "d.M.yyyy", "d-M-yyyy", "M/d/yyyy", "yyyy/M/d").map(DateTimeFormatter::ofPattern)
        fun old(s: String) = formats.firstNotNullOfOrNull { f -> runCatching { java.time.LocalDate.parse(s, f) }.getOrNull() }?.toString()
        val inputs = buildList {
            for (sep in "-/.") for (a in 0..33) for (b in 0..33) for (y in listOf("2026", "2024", "0000", "0001", "+12026", "26", "02026")) {
                add("$a$sep$b$sep$y"); add("$y$sep$a$sep$b"); add("$y$sep${a.toString().padStart(2, '0')}$sep${b.toString().padStart(2, '0')}")
            }
            val parts = listOf("", "0", "1", "9", "12", "31", "007", "2026", "+2026", "+20261", "-1", "99999999999999999999", "x", " ")
            repeat(100_000) {
                add((1..random.nextInt(1, 6)).joinToString("") { i -> (if (i > 1) "-/.+ "[random.nextInt(5)].toString() else "") + parts[random.nextInt(parts.size)] })
            }
        }
        for (s in inputs) assertEquals(old(s), parseDate(s)?.toString(), s)
    }

    @Test fun urlEncodingMatchesUrlEncoder() {
        val alphabet = "aZ09 .-*_~!'()&=+%/?#àé€ü\u0000\n" + "😀" + "\uD800"
        repeat(50_000) {
            val s = String(CharArray(random.nextInt(0, 12)) { alphabet[random.nextInt(alphabet.length)] })
            assertEquals(URLEncoder.encode(s, "UTF-8"), Yahoo.enc(s), s)
        }
    }
}
