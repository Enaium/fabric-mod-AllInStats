/*
 * Copyright (c) 2026 Enaium
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package cn.enaium.allinstats.db

/**
 * Counters are accumulated in memory and written in the background, the game thread never waits for
 * the database. The amount of a bucket is added to the row that is already there, so the counters of
 * the running minute are written as well and the interface sees them immediately.
 *
 * @author Enaium
 */
class StatsRecorder(private val intervalMillis: Long = 2_000L) {
    private val lock = Any()
    private val current = HashMap<BucketKey, Long>()
    private var lastFlush = 0L

    /**
     * Adds [amount] to the counter of the current minute.
     */
    fun record(key: BucketKey, amount: Long, now: Long) {
        if (amount == 0L) {
            return
        }
        val bucketKey = key.copy(bucket = minuteOf(now))
        synchronized(lock) {
            current.merge(bucketKey, amount, Long::plus)
        }
    }

    /**
     * The counters that are waiting to be written, or an empty list while the flush interval did not
     * elapse. The counters are only released by [written], so a failed write does not lose them.
     */
    fun flush(now: Long, force: Boolean = false): List<CounterRow> {
        if (!force && now - lastFlush < intervalMillis) {
            return emptyList()
        }
        lastFlush = now
        return snapshot()
    }

    /**
     * Releases the counters of a flush that was written.
     */
    fun written(rows: List<CounterRow>) {
        if (rows.isEmpty()) {
            return
        }
        synchronized(lock) {
            rows.forEach { row ->
                val key = BucketKey(row.worldId, row.playerUuid, row.type, row.statKey, row.source, row.bucket)
                val remaining = current.getOrDefault(key, 0L) - row.amount
                if (remaining > 0L) {
                    current[key] = remaining
                } else {
                    current.remove(key)
                }
            }
        }
    }

    /**
     * Number of counters that are still waiting to be written.
     */
    fun pending(): Int = synchronized(lock) { current.size }

    private fun snapshot(): List<CounterRow> {
        val rows = ArrayList<CounterRow>()
        synchronized(lock) {
            current.forEach { (key, amount) ->
                rows.add(
                    CounterRow(
                        worldId = key.worldId,
                        playerUuid = key.playerUuid,
                        type = key.type,
                        statKey = key.statKey,
                        source = key.source,
                        bucket = key.bucket,
                        amount = amount,
                    )
                )
            }
        }
        return rows
    }

    companion object {
        const val BUCKET_MILLIS = 60_000L

        fun minuteOf(time: Long): Long = time / BUCKET_MILLIS * BUCKET_MILLIS
    }
}

/**
 * The counter identity of one minute.
 *
 * @author Enaium
 */
data class BucketKey(
    val worldId: String,
    val playerUuid: String,
    val type: String,
    val statKey: String,
    val source: String,
    val bucket: Long,
)
