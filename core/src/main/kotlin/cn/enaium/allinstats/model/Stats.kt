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

package cn.enaium.allinstats.model

/**
 * The kind of action that is counted. Every type has its own counter series.
 */
enum class StatType(val id: String) {
    ITEM_ACQUIRED("item_acquired"),
    ITEM_DROPPED("item_dropped"),
    BLOCK_BROKEN("block_broken"),
    BLOCK_PLACED("block_placed"),
    ENTITY_KILLED("entity_killed"),
    ENTITY_KILLED_BY("entity_killed_by"),
    TOOL_USED("tool_used"),
    TOOL_BROKEN("tool_broken"),
    ;

    companion object {
        private val byId = values().associateBy { it.id }

        fun of(id: String): StatType? = byId[id]

        fun ofAll(ids: Collection<String>): List<StatType> = ids.mapNotNull { byId[it] }
    }
}

/**
 * Why an item came into (or left) the players inventory. [NONE] is used by the types that do not
 * have a source.
 */
enum class StatSource(val id: String) {
    NONE(""),
    PICKUP("pickup"),
    CRAFT("craft"),
    CONTAINER("container"),
    FISHING("fishing"),
    OTHER("other"),
    ;

    companion object {
        private val byId = values().associateBy { it.id }

        fun of(id: String): StatSource = byId[id] ?: OTHER
    }
}

/**
 * The top level categories shown in the interface. The concrete objects (stone, dirt, pig, ...) are
 * the keys of the counters, [sources] are the sources the counters of this category are recorded
 * with and [sourceSelectable] tells whether the interface offers to filter them.
 */
enum class StatGroup(
    val id: String,
    val type: StatType,
    val auxiliary: StatType? = null,
    val sources: List<StatSource> = listOf(StatSource.NONE),
    val sourceSelectable: Boolean = false,
) {
    ITEM("item", StatType.ITEM_ACQUIRED, StatType.ITEM_DROPPED, emptyList(), sourceSelectable = true),
    BLOCK("block", StatType.BLOCK_BROKEN, StatType.BLOCK_PLACED),
    ENTITY("entity", StatType.ENTITY_KILLED, StatType.ENTITY_KILLED_BY),
    TOOL("tool", StatType.TOOL_USED, StatType.TOOL_BROKEN),
    FISHING("fishing", StatType.ITEM_ACQUIRED, null, listOf(StatSource.FISHING)),
    ;

    val types: List<StatType> get() = listOfNotNull(type, auxiliary)

    companion object {
        private val byId = values().associateBy { it.id }

        fun of(id: String): StatGroup? = byId[id]
    }
}

/**
 * The resolution the time axis is aggregated to.
 */
enum class Granularity(val id: String, val millis: Long) {
    MINUTE("minute", 60_000L),
    HOUR("hour", 3_600_000L),
    DAY("day", 86_400_000L),
    WEEK("week", 604_800_000L),
    MONTH("month", 2_592_000_000L),
    YEAR("year", 31_536_000_000L),
    ;

    companion object {
        private val byId = values().associateBy { it.id }

        fun of(id: String): Granularity? = byId[id]
    }
}

/**
 * The style the series is rendered with.
 */
enum class ChartType(val id: String) {
    LINE("line"),
    AREA("area"),
    BAR("bar"),
    SCATTER("scatter"),
    CANDLE("candle"),
    ;

    companion object {
        private val byId = values().associateBy { it.id }

        fun of(id: String): ChartType? = byId[id]
    }
}
