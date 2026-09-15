package com.componentvault.android.model

/** Recorded outbound movement is not proof of consumption; adjustments are not usage. */
data class StockUsageSummary(val remaining: Int, val issued: Long) {
    init {
        require(remaining >= 0 && issued >= 0 && issued <= Long.MAX_VALUE - remaining)
    }

    val total: Long get() = remaining.toLong() + issued
    val issuedFraction: Float get() = if (total == 0L) 0f else (issued.toDouble() / total).toFloat()
}
