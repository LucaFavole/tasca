package app.tasca.domain

import kotlin.math.abs

/** Evaluates the keypad expression, e.g. "12,50+3×2". Returns cents, or null when incomplete or invalid. */
object Calc {
    const val OPERATORS = "+-×÷"

    fun isOperator(c: Char) = c in OPERATORS

    fun evaluate(expr: String): Long? {
        val tokens = tokenize(expr.replace(',', '.')) ?: return null
        if (tokens.isEmpty()) return null
        var i = 0
        fun term(): Fraction? {
            var acc = tokens.getOrNull(i++) as? Fraction ?: return null
            while (i < tokens.size && (tokens[i] == '×' || tokens[i] == '÷')) {
                val op = tokens[i++]
                val rhs = tokens.getOrNull(i++) as? Fraction ?: return null
                acc = (if (op == '×') acc * rhs else acc / rhs) ?: return null
            }
            return acc
        }
        var total = term() ?: return null
        while (i < tokens.size) {
            val op = tokens[i++]
            val rhs = term() ?: return null
            total = (if (op == '+') total + rhs else total - rhs) ?: return null
        }
        return total.cents()
    }

    private fun tokenize(s: String): List<Any>? {
        val out = mutableListOf<Any>()
        val num = StringBuilder()
        fun flush(): Boolean {
            if (num.isEmpty()) return true
            out += Fraction.parse(num.toString()) ?: return false
            num.clear()
            return true
        }
        for (c in s) {
            when {
                c.isDigit() || c == '.' -> num.append(c)
                isOperator(c) -> { if (!flush()) return null; out += c }
                c.isWhitespace() -> {}
                else -> return null
            }
        }
        return if (flush()) out else null
    }
}

/** An exact rational number in lowest terms with [den] > 0. Arithmetic returns null where a Long would overflow. */
private class Fraction private constructor(val num: Long, val den: Long) {
    operator fun plus(o: Fraction): Fraction? {
        val g = gcd(den, o.den)
        return of(add(mul(num, o.den / g), mul(o.num, den / g)), mul(den / g, o.den))
    }

    operator fun minus(o: Fraction): Fraction? = plus(Fraction(-o.num, o.den))

    operator fun times(o: Fraction): Fraction? {
        val a = gcd(abs(num), o.den)
        val b = gcd(abs(o.num), den)
        return of(mul(num / a, o.num / b), mul(den / b, o.den / a))
    }

    operator fun div(o: Fraction): Fraction? =
        if (o.num == 0L) null else times(Fraction(if (o.num < 0) -o.den else o.den, abs(o.num)))

    /** Rounded half up, away from zero, to cents. */
    fun cents(): Long? {
        val scaledRest = mul(num % den, 100) ?: return null
        val rest = abs(scaledRest % den)
        val away = if (rest >= den - rest) (if (num < 0) -1 else 1) else 0
        return add(mul(num / den, 100), scaledRest / den + away)
    }

    companion object {
        /** Digits of any script with at most one decimal point, as BigDecimal reads them: "12.5", ".5" and "5." are numbers, "." is not. */
        fun parse(s: String): Fraction? {
            if (s.count { it == '.' } > 1 || s.none { it.isDigit() }) return null
            var num = 0L
            var den = 1L
            var afterPoint = false
            for (c in s) {
                if (c == '.') { afterPoint = true; continue }
                if (!c.isDigit()) return null
                num = add(mul(num, 10), c.digitToInt().toLong()) ?: return null
                if (afterPoint) den = mul(den, 10) ?: return null
            }
            return of(num, den)
        }

        private fun of(num: Long?, den: Long?): Fraction? {
            if (num == null || den == null || den == 0L || num == Long.MIN_VALUE || den == Long.MIN_VALUE) return null
            val g = gcd(abs(num), abs(den))
            val sign = if (den < 0) -1 else 1
            return Fraction(sign * num / g, sign * den / g)
        }

        private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a.coerceAtLeast(1) else gcd(b, a % b)

        private fun add(a: Long?, b: Long?): Long? {
            if (a == null || b == null) return null
            val r = a + b
            return if ((a xor r) and (b xor r) < 0) null else r
        }

        private fun mul(a: Long?, b: Long?): Long? {
            if (a == null || b == null) return null
            if (a == 0L || b == 0L) return 0
            val r = a * b
            return if (r / b != a || (a == -1L && b == Long.MIN_VALUE) || (b == -1L && a == Long.MIN_VALUE)) null else r
        }
    }
}
