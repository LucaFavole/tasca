package app.tasca.domain

import kotlin.math.roundToLong

enum class TradeSide { BUY, SELL }

/** A holding's position after a trade, and the profit (or loss, negative) a sale realised. */
data class Position(val quantity: Double, val investedCents: Long, val realizedCents: Long = 0)

/** What a sale leaves you with: the gross proceeds less fee and the tax on the gain net of the fee. */
data class SaleOutcome(val grossCents: Long, val gainCents: Long, val taxCents: Long, val receivedCents: Long)

object Trade {
    fun sale(position: Position, shares: Double, unitPrice: Double, feeCents: Long, taxRatePct: Double): SaleOutcome {
        val gross = cents(shares, unitPrice)
        val taxable = (position.realizedCents - feeCents).coerceAtLeast(0)
        val tax = (taxable * taxRatePct / 100).roundToLong()
        return SaleOutcome(gross, position.realizedCents, tax, gross - feeCents - tax)
    }

    private const val DUST = 1e-6

    fun cents(quantity: Double, unitPrice: Double): Long = (quantity * unitPrice * 100).roundToLong()

    /**
     * Buying adds to the cost; selling removes the sold shares' share of the cost at the average price,
     * and the difference to what the sale brought in is the realised result. Fees are not part of either:
     * they are booked separately as an expense.
     */
    fun apply(side: TradeSide, quantity: Double, investedCents: Long, shares: Double, unitPrice: Double): Position {
        require(shares > 0) { "shares must be positive" }
        return when (side) {
            TradeSide.BUY -> Position(quantity + shares, investedCents + cents(shares, unitPrice))
            TradeSide.SELL -> {
                require(shares <= quantity + DUST) { "You only have ${"%.4f".format(quantity)} shares" }
                val all = shares >= quantity - DUST
                val cost = if (all) investedCents else (investedCents * shares / quantity).roundToLong()
                Position(if (all) 0.0 else quantity - shares, investedCents - cost, cents(shares, unitPrice) - cost)
            }
        }
    }
}
