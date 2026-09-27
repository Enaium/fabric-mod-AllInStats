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

package cn.enaium.allinstats

import cn.enaium.allinstats.db.CounterRow
import cn.enaium.allinstats.db.StatsDatabase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files

class StatsDatabaseTest {
    private fun database(): StatsDatabase {
        val file = Files.createTempDirectory("allinstats").resolve("stats")
        return StatsDatabase("jdbc:h2:file:${file.toAbsolutePath()}")
    }

    @Test
    fun `counters are added to the bucket of their unique key`() {
        val db = database()
        db.ensureWorld("world", "World")
        db.ensurePlayer("world", "player", "Player")

        db.addCounters(listOf(row(bucket = 60_000, amount = 3)))
        // the same bucket is written again (a flush while the minute is still running), it must add
        // to the row that is already there instead of creating a second one
        db.addCounters(listOf(row(bucket = 60_000, amount = 5)))

        val worlds = db.worldIds()
        assertEquals(1, worlds.size)
        assertEquals("world", worlds[0].worldId)

        val totals = db.keyTotals("world", "player", listOf("block_broken"), null, "", 0, Long.MAX_VALUE, 10)
        assertEquals(1, totals.size)
        assertEquals("minecraft:stone", totals[0].key)
        assertEquals(8, totals[0].total)
    }

    @Test
    fun `totals are grouped by type and source and filtered by range`() {
        val db = database()
        db.ensureWorld("world", "World")
        db.addCounters(
            listOf(
                row(bucket = 60_000, amount = 3),
                row(bucket = 120_000, amount = 4),
                row(bucket = 180_000, amount = 7, player = "other"),
                row(bucket = 240_000, amount = 2, type = "item_dropped", source = ""),
            )
        )

        val totals = db.totals(
            "world",
            "player",
            listOf("block_broken", "item_dropped"),
            null,
            "minecraft:stone",
            0,
            180_000,
        )
        assertEquals(1, totals.size)
        assertEquals("block_broken", totals[0].type)
        assertEquals(7, totals[0].total)

        val points = db.minutes(
            "world",
            null,
            listOf("block_broken"),
            null,
            "minecraft:stone",
            0,
            Long.MAX_VALUE,
        )
        assertEquals(3, points.size)
        assertEquals(listOf(60_000L, 120_000L, 180_000L), points.map { it.bucket })
        assertEquals(14, points.sumOf { it.amount })
        assertEquals(180_000L, db.lastBucket("world", "other") ?: 0L)
        assertEquals(240_000L, db.lastBucket("world", "player") ?: 0L)
        assertEquals(240_000L, db.lastBucket("world", null) ?: 0L)
    }

    @Test
    fun `fishing is a source of the acquired items`() {
        val db = database()
        db.ensureWorld("world", "World")
        db.addCounters(
            listOf(
                row(bucket = 60_000, amount = 5, type = "item_acquired", source = "fishing"),
                row(bucket = 60_000, amount = 2, type = "item_acquired", source = "pickup"),
            )
        )

        val fishing = db.keyTotals("world", null, listOf("item_acquired"), listOf("fishing"), "", 0, Long.MAX_VALUE, 10)
        assertEquals(1, fishing.size)
        assertEquals(5, fishing[0].total)

        val all = db.totals("world", null, listOf("item_acquired"), null, "minecraft:cod", 0, Long.MAX_VALUE)
        assertEquals(7, all.sumOf { it.total })
        assertEquals(setOf("fishing", "pickup"), all.map { it.source }.toSet())
    }

    @Test
    fun `the world that was played last comes first`() {
        val db = database()
        db.ensureWorld("old", "Old")
        db.ensureWorld("recent", "Recent")
        db.addCounters(
            listOf(
                CounterRow("old", "player", "block_broken", "minecraft:stone", "", 60_000, 1),
                CounterRow("recent", "player", "block_broken", "minecraft:stone", "", 120_000, 1),
            )
        )
        val worlds = db.worldIds()
        assertEquals(listOf("recent", "old"), worlds.map { it.worldId })
        assertEquals(120_000L, worlds.first().lastBucket)
        assertEquals(60_000L, worlds.last().lastBucket)
    }

    @Test
    fun `a world without counters is only listed while it is the running one`() {
        val empty = cn.enaium.allinstats.db.WorldRow("empty", "Empty", 0L, 0L)
        val played = cn.enaium.allinstats.db.WorldRow("played", "Played", 0L, 60_000L)
        assertEquals(
            listOf("played"),
            cn.enaium.allinstats.gui.StatsViewModel.visibleWorlds(listOf(empty, played), null).map { it.worldId },
        )
        assertEquals(
            listOf("empty", "played"),
            cn.enaium.allinstats.gui.StatsViewModel.visibleWorlds(listOf(empty, played), "empty").map { it.worldId },
        )
    }

    private fun row(
        bucket: Long,
        amount: Long,
        player: String = "player",
        type: String = "block_broken",
        source: String = "",
    ) = CounterRow(
        worldId = "world",
        playerUuid = player,
        type = type,
        statKey = if (source == "fishing" || source == "pickup") "minecraft:cod" else "minecraft:stone",
        source = source,
        bucket = bucket,
        amount = amount,
    )
}
