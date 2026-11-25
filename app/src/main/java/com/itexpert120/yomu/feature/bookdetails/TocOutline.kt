package com.itexpert120.yomu.feature.bookdetails

/**
 * The tree shape of a contents list that is flat and depth-annotated, in document order: an
 * entry's descendants are the entries after it up to the next one at its depth or shallower. An
 * entry with descendants (a volume, a part) can be collapsed. Everything is O(n), so a 1,500-entry
 * book re-derives its rows in well under a frame.
 */
internal class TocOutline(private val depths: IntArray) {
    val size: Int get() = depths.size

    /** How many entries sit under each entry (0 for a chapter with no subsections). */
    val descendants: IntArray = IntArray(depths.size)

    // Document indices in post-order (an entry right after the last of its descendants).
    private val postOrder = IntArray(depths.size)

    init {
        val open = IntArray(depths.size)
        var top = 0
        var emitted = 0
        for (index in depths.indices) {
            while (top > 0 && depths[open[top - 1]] >= depths[index]) {
                val closed = open[--top]
                descendants[closed] = index - closed - 1
                postOrder[emitted++] = closed
            }
            open[top++] = index
        }
        while (top > 0) {
            val closed = open[--top]
            descendants[closed] = depths.size - closed - 1
            postOrder[emitted++] = closed
        }
    }

    fun hasChildren(index: Int): Boolean = descendants[index] > 0

    /** Whether [index] lies inside [ancestor]'s subtree. */
    fun isUnder(index: Int, ancestor: Int): Boolean = index > ancestor && index <= ancestor + descendants[ancestor]

    /**
     * The order rows are listed in: document order, or newest first, where every level's siblings
     * are reversed but a volume still comes before its chapters (the reverse of the post-order).
     * Either way an entry's subtree directly follows it.
     */
    fun order(descending: Boolean): IntArray = if (descending) postOrder.reversedArray() else IntArray(size) { it }

    /** [order] without the entries inside a collapsed entry. */
    fun visible(order: IntArray, collapsed: Set<Int>): IntArray {
        if (collapsed.isEmpty()) return order
        val out = IntArray(order.size)
        var count = 0
        var position = 0
        while (position < order.size) {
            val index = order[position]
            out[count++] = index
            // The subtree is contiguous right after its root in either order.
            position += if (index in collapsed) descendants[index] + 1 else 1
        }
        return out.copyOf(count)
    }

    companion object {
        fun of(depths: List<Int>): TocOutline = TocOutline(depths.toIntArray())
    }
}
