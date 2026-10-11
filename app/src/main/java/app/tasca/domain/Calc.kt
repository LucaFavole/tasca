package app.tasca.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Evaluates the keypad expression, e.g. "12,50+3×2". Returns cents, or null when incomplete or invalid. */
object Calc {
    const val OPERATORS = "+-×÷"

    fun isOperator(c: Char) = c in OPERATORS

    fun evaluate(expr: String): Long? {
        val tokens = tokenize(expr.replace(',', '.')) ?: return null
        if (tokens.isEmpty()) return null
        var i = 0
        fun term(): BigDecimal? {
            var acc = tokens.getOrNull(i++) as? BigDecimal ?: return null
            while (i < tokens.size && (tokens[i] == '×' || tokens[i] == '÷')) {
                val op = tokens[i++]
                val rhs = tokens.getOrNull(i++) as? BigDecimal ?: return null
                acc = if (op == '×') acc.multiply(rhs) else {
                    if (rhs.signum() == 0) return null
                    acc.divide(rhs, MathContext.DECIMAL64)
                }
            }
            return acc
        }
        var total = term() ?: return null
        while (i < tokens.size) {
            val op = tokens[i++]
            val rhs = term() ?: return null
            total = if (op == '+') total.add(rhs) else total.subtract(rhs)
        }
        return total.setScale(2, RoundingMode.HALF_UP).unscaledValue().toLong()
    }

    private fun tokenize(s: String): List<Any>? {
        val out = mutableListOf<Any>()
        val num = StringBuilder()
        fun flush(): Boolean {
            if (num.isEmpty()) return true
            out += num.toString().toBigDecimalOrNull() ?: return false
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
