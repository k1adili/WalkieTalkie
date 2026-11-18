package com.k1adili.walkietalkie.engine

/**
 * Tiny per-sender FIFO. Waits for [prebuffer] frames before releasing audio so that small
 * network hiccups don't turn into stutter, and drops the oldest frames if it ever grows
 * past [maxFrames] so latency stays bounded.
 */
class JitterBuffer(
    private val prebuffer: Int = 3,
    private val maxFrames: Int = 15,
) {
    private val queue = ArrayDeque<ShortArray>()
    private var primed = false

    @Synchronized
    fun put(frame: ShortArray) {
        queue.addLast(frame)
        while (queue.size > maxFrames) queue.removeFirst()
        if (!primed && queue.size >= prebuffer) primed = true
    }

    /** Next frame, or null while (re)buffering or after an underrun. */
    @Synchronized
    fun poll(): ShortArray? {
        if (!primed) return null
        val frame = queue.removeFirstOrNull()
        if (frame == null) primed = false
        return frame
    }

    /** Sender stopped: let whatever is left drain even if it is shorter than the prebuffer. */
    @Synchronized
    fun flush() {
        if (queue.isNotEmpty()) primed = true
    }

    @Synchronized
    fun size(): Int = queue.size

    @Synchronized
    fun clear() {
        queue.clear()
        primed = false
    }
}
