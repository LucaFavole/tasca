package app.tasca

import app.tasca.domain.Calc
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The keypad evaluator now computes on exact fractions of two Longs. Before, it used BigDecimal with
 * 16-digit division. This compares both with an exact BigInteger reference.
 */
class CalcEquivalenceTest {
    private val random = Random(20261012)

    private fun decimal(whole: Int, decimals: Int): String {
        val point = if (random.nextBoolean()) "," else "."
        val fraction = if (decimals == 0) "" else point + random.nextInt(Math.pow(10.0, decimals.toDouble()).toInt()).toString().padStart(decimals, '0')
        return random.nextInt(whole).toString() + fraction
    }

    /**
     * What the keypad is for: up to three amounts below 10.000, each maybe multiplied or divided by counts
     * or factors such as 1,22, with one division at most. These always get the exact amount. The old code
     * agreed except where a division was cut at 16 digits just below a half cent, as in 9503,4÷348×7368,9.
     */
    @Test fun keypadSumsGiveTheExactAmount() {
        var oldWasOff = 0
        repeat(200_000) {
            var divided = false
            val expr = (1..random.nextInt(1, 4)).joinToString("") { i ->
                val factors = (1..random.nextInt(0, 3)).joinToString("") {
                    val op = if (divided) "×" else "×÷"[random.nextInt(2)].toString()
                    if (op == "÷") divided = true
                    op + if (random.nextBoolean()) decimal(1000, 0) else decimal(100, random.nextInt(3))
                }
                (if (i > 1) "+-"[random.nextInt(2)].toString() else "") + decimal(10_000, random.nextInt(3)) + factors
            }
            val exact = ExactCalc.evaluate(expr)
            assertEquals(exact, Calc.evaluate(expr), expr)
            val old = OldCalc.evaluate(expr)
            if (old != exact) {
                assertEquals(1L, kotlin.math.abs(old!! - exact!!), expr)
                oldWasOff++
            }
        }
        println("keypad: the old code was one cent off on $oldWasOff of 200000 expressions")
    }

    /**
     * Anywhere else the result is the exact value rounded half up, or no amount when an exact intermediate
     * value needs more than a Long. BigDecimal could differ in the last cent past 16 significant digits.
     */
    @Test fun anyExpressionGivesTheExactAmountOrNone() {
        val alphabet = "0123456789.,+-×÷ ٣"
        var refused = 0
        var oldWasOff = 0
        repeat(200_000) {
            val expr = if (it % 2 == 0) {
                String(CharArray(random.nextInt(1, 14)) { alphabet[random.nextInt(alphabet.length)] })
            } else {
                (1..random.nextInt(1, 6)).joinToString("") { i ->
                    (if (i > 1) "+-×÷"[random.nextInt(4)].toString() else "") + decimal(100_000, random.nextInt(4))
                }
            }
            val exact = ExactCalc.evaluate(expr)
            val new = Calc.evaluate(expr)
            if (new == null && exact != null) refused++ else assertEquals(exact, new, expr)
            if (exact != OldCalc.evaluate(expr)) oldWasOff++
        }
        println("calculator: of 200000 random expressions, $refused give no amount now; the old code was off on $oldWasOff")
    }

    private fun tokens(expr: String): List<Any>? {
        val out = mutableListOf<Any>()
        val num = StringBuilder()
        fun flush(): Boolean {
            if (num.isEmpty()) return true
            out += num.toString().toBigDecimalOrNull() ?: return false
            num.clear()
            return true
        }
        for (c in expr.replace(',', '.')) {
            when {
                c.isDigit() || c == '.' -> num.append(c)
                Calc.isOperator(c) -> { if (!flush()) return null; out += c }
                c.isWhitespace() -> {}
                else -> return null
            }
        }
        return if (flush()) out else null
    }

    /** The evaluator as it was. */
    private val OldCalc = Evaluator<BigDecimal>(
        { it }, BigDecimal::add, BigDecimal::subtract, BigDecimal::multiply,
        { a, b -> if (b.signum() == 0) null else a.divide(b, MathContext.DECIMAL64) },
        { it.setScale(2, RoundingMode.HALF_UP).unscaledValue().toLong() },
    )

    /** Exact fractions of BigIntegers, as [num]/[den]. */
    private data class Q(val num: BigInteger, val den: BigInteger)

    private val ExactCalc = Evaluator<Q>(
        { Q(it.unscaledValue(), BigInteger.TEN.pow(it.scale())) },
        { a, b -> Q(a.num * b.den + b.num * a.den, a.den * b.den) },
        { a, b -> Q(a.num * b.den - b.num * a.den, a.den * b.den) },
        { a, b -> Q(a.num * b.num, a.den * b.den) },
        { a, b -> if (b.num.signum() == 0) null else Q(a.num * b.den * b.num.signum().toBigInteger(), a.den * b.num.abs()) },
        { q ->
            val (whole, rest) = (q.num * BigInteger.valueOf(100)).divideAndRemainder(q.den)
            val away = if (rest.abs() * BigInteger.TWO >= q.den) q.num.signum() else 0
            (whole + away.toBigInteger()).toLong()
        },
    )

    private inner class Evaluator<T : Any>(
        val of: (BigDecimal) -> T, val plus: (T, T) -> T, val minus: (T, T) -> T, val times: (T, T) -> T,
        val div: (T, T) -> T?, val cents: (T) -> Long,
    ) {
        fun evaluate(expr: String): Long? {
            val tokens = tokens(expr)?.map { if (it is BigDecimal) of(it) else it } ?: return null
            if (tokens.isEmpty()) return null
            var i = 0
            @Suppress("UNCHECKED_CAST")
            fun operand(): T? = tokens.getOrNull(i++)?.takeIf { it !is Char } as T?
            fun term(): T? {
                var acc = operand() ?: return null
                while (i < tokens.size && (tokens[i] == '×' || tokens[i] == '÷')) {
                    val op = tokens[i++]
                    val rhs = operand() ?: return null
                    acc = if (op == '×') times(acc, rhs) else div(acc, rhs) ?: return null
                }
                return acc
            }
            var total = term() ?: return null
            while (i < tokens.size) {
                val op = tokens[i++]
                val rhs = term() ?: return null
                total = if (op == '+') plus(total, rhs) else minus(total, rhs)
            }
            return cents(total)
        }
    }
}
