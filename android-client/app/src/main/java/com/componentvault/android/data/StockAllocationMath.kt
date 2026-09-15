package com.componentvault.android.data

internal object StockAllocationMath {
    fun applyDelta(current: Int, delta: Int): Int = Math.addExact(current, delta).also {
        require(it >= 0) { "The selected location does not have enough stock." }
    }

    fun transfer(source: Int, destination: Int, quantity: Int): Pair<Int, Int> {
        require(quantity > 0 && source >= quantity) { "The source location does not have enough stock." }
        return (source - quantity) to Math.addExact(destination, quantity)
    }

    fun requireTotal(total: Int, allocations: Iterable<Int>) {
        val sum = allocations.fold(0L) { value, quantity ->
            require(quantity >= 0)
            Math.addExact(value, quantity.toLong())
        }
        require(sum == total.toLong()) { "Allocation total does not match component quantity." }
    }
}
