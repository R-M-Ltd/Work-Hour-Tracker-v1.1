package com.rmltd.workhourstracker.util

/**
 * Decimal-only hours parse for Add/Change sheet (1.3.34).
 * Allows `0`, `7.5`, `8.00`. Rejects colon forms like `7:30`.
 */
object TypedHoursParse {

    /**
     * @return parsed hours in [0, 24] inclusive, or null if invalid.
     */
    fun parse(raw: String): Double? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        if (t.contains(':')) return null
        // Reject commas / letters / multiple dots via careful parse
        if (!t.matches(Regex("""^\d+(\.\d+)?$"""))) return null
        val v = t.toDoubleOrNull() ?: return null
        if (v < 0.0 || v > 24.0) return null
        return v
    }

    fun isValid(raw: String): Boolean = parse(raw) != null
}
