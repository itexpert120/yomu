package com.itexpert120.yomu.data.sync

import java.util.Locale

/**
 * Hybrid logical clock stamp. Orders changes across devices even with skewed wall clocks: a device
 * never issues a stamp lower than one it has seen. [encode] sorts the same as [compareTo], which is
 * what lets the server pick the newer write with a plain string comparison.
 */
data class Hlc(val physical: Long, val counter: Int, val device: String) : Comparable<Hlc> {
    override fun compareTo(other: Hlc): Int = compareValuesBy(this, other, Hlc::physical, Hlc::counter, Hlc::device)

    fun encode(): String = String.format(Locale.ROOT, "%013d:%05d:%s", physical, counter, device)

    companion object {
        fun decode(raw: String): Hlc? {
            val parts = raw.split(':', limit = 3)
            if (parts.size != 3) return null
            val physical = parts[0].toLongOrNull() ?: return null
            val counter = parts[1].toIntOrNull() ?: return null
            return Hlc(physical, counter, parts[2])
        }
    }
}

/** Issues monotonic [Hlc]s for one device. Not thread-safe; callers serialise access. */
class HlcClock(private val device: String, private var last: Hlc? = null, private val now: () -> Long = System::currentTimeMillis) {

    val latest: Hlc? get() = last

    fun tick(): Hlc {
        val wall = now()
        val previous = last
        val next = when {
            previous == null || wall > previous.physical -> Hlc(wall, 0, device)
            else -> Hlc(previous.physical, previous.counter + 1, device)
        }
        last = next
        return next
    }

    /** Folds in a stamp seen from another device so later local stamps sort after it. */
    fun observe(remote: Hlc) {
        val previous = last
        if (previous == null || remote > previous) last = Hlc(remote.physical, remote.counter, device)
    }
}
