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

package cn.enaium.allinstats.query

import cn.enaium.allinstats.db.StatsDatabase
import cn.enaium.allinstats.model.Granularity
import cn.enaium.allinstats.model.StatGroup
import cn.enaium.allinstats.model.StatSource
import cn.enaium.allinstats.model.StatType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * One entry of the "concrete object" list (stone, dirt, pig, ...).
 */
data class KeyEntry(val key: String, val total: Long)

/**
 * One plotted series, values are dense: every bucket of the time range has a value.
 */
data class ChartSeries(
    val times: DoubleArray,
    val zeros: DoubleArray,
    val key: String,
    val type: StatType,
    val source: StatSource,
    val values: DoubleArray,
    val open: DoubleArray = DoubleArray(0),
    val high: DoubleArray = DoubleArray(0),
    val low: DoubleArray = DoubleArray(0),
    val close: DoubleArray = DoubleArray(0),
) {
    val total: Double get() = values.sum()
    val peak: Double get() = values.maxOrNull() ?: 0.0
}

/**
 * Everything a chart needs: the buckets and the series.
 */
data class ChartData(
    val times: DoubleArray,
    val series: List<ChartSeries>,
    val granularity: Granularity,
) {
    /**
     * The range the data covers, a single bucket is widened so that it has a visible width.
     */
    val dataFrom: Long get() = times.firstOrNull()?.toLong() ?: 0L

    val dataTo: Long get() = times.lastOrNull()?.toLong() ?: 0L

    val isEmpty: Boolean get() = times.isEmpty() || series.all { it.total == 0.0 }
}

/**
 * Reads the counters and turns the minute buckets into the requested resolution.
 */
class StatsRepository(private val db: StatsDatabase) {
    fun keys(
        worldId: String,
        playerUuid: String?,
        group: StatGroup,
        sources: Collection<StatSource>,
        search: String,
        from: Long,
        to: Long,
        limit: Int = 500,
    ): List<KeyEntry> =
        db.keyTotals(
            worldId = worldId,
            playerUuid = playerUuid,
            types = group.types.map { it.id },
            sources = sourceFilter(group, sources),
            search = search,
            from = from,
            to = to,
            limit = limit,
        ).map { KeyEntry(it.key, it.total) }

    /**
     * Totals of every (type, source) pair of one key, used for the summary panel.
     */
    fun totals(
        worldId: String,
        playerUuid: String?,
        group: StatGroup,
        sources: Collection<StatSource>,
        key: String,
        from: Long,
        to: Long,
    ): Map<Pair<StatType, StatSource>, Long> =
        db.totals(
            worldId = worldId,
            playerUuid = playerUuid,
            types = group.types.map { it.id },
            sources = sourceFilter(group, sources),
            statKey = key,
            from = from,
            to = to,
        ).mapNotNull { row ->
            val type = StatType.of(row.type) ?: return@mapNotNull null
            type to StatSource.of(row.source) to row.total
        }.toMap()

    /**
     * Builds the chart of the selected counters. Every counter (and, when the group has a source
     * dimension, every source) becomes its own series.
     *
     * @param candle the values of every minute are kept, so that a candle can be built from them.
     * Only a single counter can be drawn that way.
     */
    fun chart(
        worldId: String,
        playerUuid: String?,
        group: StatGroup,
        keys: Collection<String>,
        from: Long,
        to: Long,
        granularity: Granularity,
        metrics: Collection<StatType>,
        sources: Collection<StatSource>,
        candle: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ChartData {
        val metrics = metrics.toList()
        if (metrics.isEmpty() || keys.isEmpty()) {
            return ChartData(DoubleArray(0), emptyList(), granularity)
        }
        val minutes = db.minutesOfKeys(
            worldId = worldId,
            playerUuid = playerUuid,
            types = metrics.map { it.id },
            sources = sourceFilter(group, sources),
            keys = keys.toList(),
            from = from,
            to = to,
        )
        val buckets = ArrayList<Long>()
        val index = HashMap<Long, Int>()
        fun bucketIndex(time: Long): Int {
            val start = Buckets.start(time, granularity, zone)
            return index.getOrPut(start) {
                buckets.add(start)
                buckets.size - 1
            }
        }

        // (key, type, source) -> bucket index -> [sum, open, close, high, low, seen]
        val accumulator = LinkedHashMap<Triple<String, StatType, StatSource>, HashMap<Int, DoubleArray>>()
        for (point in minutes) {
            val type = StatType.of(point.type) ?: continue
            val source = StatSource.of(point.source)
            val identity = Triple(point.key, type, source)
            val slot = accumulator.getOrPut(identity) { HashMap() }
            val bucket = bucketIndex(point.bucket)
            val values = slot.getOrPut(bucket) { DoubleArray(6) }
            val amount = point.amount.toDouble()
            if (values[5] == 0.0) {
                values[5] = 1.0
                values[1] = amount
                values[4] = amount
            }
            values[2] = amount
            if (amount > values[3]) {
                values[3] = amount
            }
            if (amount < values[4]) {
                values[4] = amount
            }
            values[0] += amount
        }

        val times = buckets.map { it.toDouble() }.toDoubleArray()
        val zeros = DoubleArray(buckets.size)
        val series = accumulator.entries
            .sortedWith(compareBy({ it.key.first }, { it.key.second.ordinal }, { it.key.third.ordinal }))
            .map { (identity, values) ->
                val sums = DoubleArray(buckets.size)
                values.forEach { (bucket, value) -> sums[bucket] = value[0] }
                if (!candle) {
                    ChartSeries(times, zeros, identity.first, identity.second, identity.third, sums)
                } else {
                    ChartSeries(
                        times,
                        zeros,
                        identity.first,
                        identity.second,
                        identity.third,
                        sums,
                        open = DoubleArray(buckets.size) { values[it]?.get(1) ?: 0.0 },
                        high = DoubleArray(buckets.size) { values[it]?.get(3) ?: 0.0 },
                        low = DoubleArray(buckets.size) { values[it]?.get(4) ?: 0.0 },
                        close = DoubleArray(buckets.size) { values[it]?.get(2) ?: 0.0 },
                    )
                }
            }
        return ChartData(times, series, granularity)
    }

    companion object {
        /**
         * The sources that are queried. A group whose counters do not have a source ignores what the
         * interface selected, otherwise the wrong row set would be read.
         */
        fun sourceFilter(group: StatGroup, selected: Collection<StatSource>): List<String>? {
            val sources = if (group.sourceSelectable) selected else group.sources
            return sources.map { it.id }.distinct().takeIf { it.isNotEmpty() }
        }

        fun format(time: Long, granularity: Granularity, zone: ZoneId = ZoneId.systemDefault()): String {
            val dateTime = Instant.ofEpochMilli(time).atZone(zone)
            return when (granularity) {
                Granularity.MINUTE, Granularity.HOUR -> dateTime.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                Granularity.DAY, Granularity.WEEK -> dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                Granularity.MONTH -> dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM"))
                Granularity.YEAR -> dateTime.format(DateTimeFormatter.ofPattern("yyyy"))
            }
        }
    }
}

/**
 * Minute buckets are aligned to epoch minutes, longer resolutions follow the local calendar.
 */
object Buckets {
    fun start(time: Long, granularity: Granularity, zone: ZoneId = ZoneId.systemDefault()): Long =
        when (granularity) {
            Granularity.MINUTE -> time / 60_000L * 60_000L
            Granularity.HOUR -> time / 3_600_000L * 3_600_000L
            else -> dateOf(time, zone)
                .let {
                    when (granularity) {
                        Granularity.WEEK -> it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        Granularity.MONTH -> it.withDayOfMonth(1)
                        Granularity.YEAR -> it.withDayOfYear(1)
                        else -> it
                    }
                }
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        }

    private fun dateOf(time: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
}

/**
 * Tick positions of the time axis. ImPlot has no formatter callback in the binding, so the ticks and
 * their labels are generated here - for the visible range, so that a zoomed axis is labelled with
 * dates as well.
 */
object TimeAxis {
    private val MINUTE = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    private val SECOND = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val MONTH = DateTimeFormatter.ofPattern("yyyy-MM")

    private const val SECOND_MILLIS = 1_000L
    private const val MINUTE_MILLIS = 60 * SECOND_MILLIS
    private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
    private const val DAY_MILLIS = 24 * HOUR_MILLIS

    /**
     * The label of a bucket of the given granularity, a zoomed axis of month buckets has to show
     * months and not the seconds of the window.
     */
    private fun format(granularity: Granularity): Pair<Int, DateTimeFormatter> = when (granularity) {
        Granularity.MINUTE -> 0 to MINUTE
        Granularity.HOUR -> 1 to MINUTE
        Granularity.DAY, Granularity.WEEK -> 2 to DAY
        Granularity.MONTH, Granularity.YEAR -> 3 to MONTH
    }

    /**
     * @param granularity the resolution of the data, it is used when the labels of the window would
     * be finer than the buckets themselves.
     * @return at least two ticks whenever the range is not empty.
     */
    @JvmOverloads
    fun ticks(
        from: Long,
        to: Long,
        granularity: Granularity = Granularity.MINUTE,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Pair<DoubleArray, Array<String>> {
        if (to <= from) {
            return DoubleArray(0) to emptyArray()
        }
        val span = to - from
        val step: Long
        var formatter: DateTimeFormatter
        var rank = 0
        when {
            span <= 2 * MINUTE_MILLIS -> {
                step = SECOND_MILLIS * 10
                formatter = SECOND
            }
            span <= 30 * MINUTE_MILLIS -> {
                step = MINUTE_MILLIS
                formatter = SECOND
                rank = 1
            }
            span <= 2 * HOUR_MILLIS -> {
                step = 15 * MINUTE_MILLIS
                formatter = MINUTE
                rank = 1
            }
            span <= 12 * HOUR_MILLIS -> {
                step = HOUR_MILLIS
                formatter = MINUTE
                rank = 1
            }
            span <= 3 * DAY_MILLIS -> {
                step = 6 * HOUR_MILLIS
                formatter = MINUTE
                rank = 1
            }
            span <= 21 * DAY_MILLIS -> {
                step = DAY_MILLIS
                formatter = DAY
                rank = 2
            }
            span <= 120 * DAY_MILLIS -> {
                step = 7 * DAY_MILLIS
                formatter = DAY
                rank = 2
            }
            span <= 3 * 365 * DAY_MILLIS -> {
                step = 30 * DAY_MILLIS
                formatter = MONTH
                rank = 3
            }
            else -> {
                step = 365 * DAY_MILLIS
                formatter = MONTH
                rank = 3
            }
        }
        val (bucketRank, bucketFormatter) = format(granularity)
        if (bucketRank > rank) {
            // the buckets are coarser than the window, a month bucket is not a time of day
            formatter = bucketFormatter
        }
        val values = ArrayList<Long>()
        val labels = ArrayList<String>()
        val tickGranularity = when {
            step < MINUTE_MILLIS -> Granularity.MINUTE
            step < DAY_MILLIS -> Granularity.HOUR
            else -> Granularity.DAY
        }
        var current = Buckets.start(from, tickGranularity, zone)
        if (current < from) {
            current += step
        }
        while (current <= to && values.size < 40) {
            values.add(current)
            labels.add(Instant.ofEpochMilli(current).atZone(zone).format(formatter))
            current += step
        }
        if (values.size < 2) {
            // a very short range has to be labelled as well
            values.clear()
            labels.clear()
            values.add(from)
            labels.add(Instant.ofEpochMilli(from).atZone(zone).format(formatter))
            values.add(to)
            labels.add(Instant.ofEpochMilli(to).atZone(zone).format(formatter))
        }
        return values.map { it.toDouble() }.toDoubleArray() to labels.toTypedArray()
    }
}
