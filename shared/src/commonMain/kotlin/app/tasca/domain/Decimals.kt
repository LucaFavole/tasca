package app.tasca.domain

/**
 * [value] with exactly [decimals] digits after [point], rounded half up: what Java's "%.Nf" prints.
 * NaN and infinities print as their names.
 */
internal fun fixed(value: Double, decimals: Int, point: Char): String {
    if (!value.isFinite()) return value.toString()
    val (negative, digits) = roundHalfUp(value, decimals)
    val padded = digits.padStart(decimals + 1, '0')
    val fraction = if (decimals > 0) point + padded.takeLast(decimals) else ""
    return (if (negative) "-" else "") + padded.dropLast(decimals) + fraction
}

/**
 * The sign of [value], and the digits of its magnitude times 10^[decimals] rounded half up.
 * Like Java's BigDecimal.valueOf and String.format, it rounds the shortest decimal form of the double
 * rather than its exact binary value, so 0.15 rounds to 0.2.
 */
internal fun roundHalfUp(value: Double, decimals: Int): Pair<Boolean, String> {
    if (!value.isFinite()) throw NumberFormatException("Not a finite number: $value")
    val text = value.toString().lowercase()
    val negative = text.startsWith('-')
    val mantissa = text.removePrefix("-").substringBefore('e')
    val exponent = text.substringAfter('e', "0").toInt()
    val whole = mantissa.substringBefore('.')
    val digits = whole + mantissa.substringAfter('.', "")
    val keep = whole.length + exponent + decimals
    val kept = when {
        keep <= 0 -> "0"
        keep >= digits.length -> digits.padEnd(keep, '0')
        else -> digits.substring(0, keep)
    }
    val up = keep in digits.indices && digits[keep] >= '5'
    val rounded = if (up) increment(kept) else kept
    return negative to rounded.trimStart('0').ifEmpty { "0" }
}

private fun increment(digits: String): String {
    val chars = digits.toCharArray()
    for (i in chars.indices.reversed()) {
        if (chars[i] != '9') {
            chars[i] = chars[i] + 1
            return chars.concatToString()
        }
        chars[i] = '0'
    }
    return "1" + chars.concatToString()
}
