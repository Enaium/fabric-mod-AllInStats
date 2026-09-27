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

import cn.enaium.allinstats.export.ChartImageExport
import cn.enaium.allinstats.export.CsvExport
import cn.enaium.allinstats.model.ChartType
import cn.enaium.allinstats.model.Granularity
import cn.enaium.allinstats.model.StatGroup
import cn.enaium.allinstats.model.StatSource
import cn.enaium.allinstats.query.StatsRepository
import cn.enaium.allinstats.db.CounterRow
import cn.enaium.allinstats.db.StatsDatabase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import javax.imageio.ImageIO

class ExportTest {
    private val directory = Files.createTempDirectory("allinstats-export")

    private fun chart(): Pair<StatsDatabase, cn.enaium.allinstats.query.ChartData> {
        val database = StatsDatabase("jdbc:h2:file:${directory.resolve("stats").toAbsolutePath()}")
        database.ensureWorld("world", "World")
        val minute = 60_000L
        val from = 1_700_000_000_000L / minute * minute
        database.addCounters(
            (0 until 120).map { index ->
                CounterRow(
                    worldId = "world",
                    playerUuid = "player",
                    type = "block_broken",
                    statKey = "minecraft:stone",
                    source = "",
                    bucket = from + index * minute,
                    amount = (index % 7 + 1).toLong(),
                )
            }
        )
        val data = StatsRepository(database).chart(
            worldId = "world",
            playerUuid = "player",
            group = StatGroup.BLOCK,
            keys = listOf("minecraft:stone"),
            from = 0,
            to = Long.MAX_VALUE,
            granularity = Granularity.HOUR,
            metrics = listOf(cn.enaium.allinstats.model.StatType.BLOCK_BROKEN),
            sources = listOf(StatSource.NONE),
            candle = false,
        )
        return database to data
    }

    @Test
    fun `the chart is written as csv and as png`() {
        val (_, data) = chart()
        val series = data.series.single()
        // 17 full weeks of 1..7 plus the first value of the next one
        assertEquals(477.0, series.total, 0.001)

        val csv = directory.resolve("chart.csv").toFile()
        CsvExport.write(csv, data)
        val lines = csv.readLines()
        assertEquals(data.times.size + 1, lines.size)
        assertTrue(lines[0].startsWith("time,"), lines[0])
        assertTrue(lines[0].contains("block_broken"), lines[0])
        assertTrue(lines[0].endsWith("block_broken"), lines[0])
        val written = lines.drop(1).sumOf { it.substringAfterLast(',').toDouble() }
        assertEquals(series.total, written, 0.001)

        val png = directory.resolve("chart.png").toFile()
        ChartImageExport.write(png, data, ChartType.LINE, "minecraft:stone", data.dataFrom, data.dataTo)
        assertTrue(png.length() > 1000, "the image is empty")
        val image = ImageIO.read(png)
        assertEquals(1280, image.width)
        assertEquals(720, image.height)
        val colors = HashSet<Int>()
        for (x in 0 until image.width step 4) {
            for (y in 0 until image.height step 4) {
                colors.add(image.getRGB(x, y))
            }
        }
        assertTrue(colors.size > 10, "the image has only ${colors.size} colors, nothing was drawn")
    }

    @Test
    fun `several counters are drawn as several series`() {
        val (database, _) = chart()
        database.addCounters(
            (0 until 120).map { index ->
                CounterRow(
                    worldId = "world",
                    playerUuid = "player",
                    type = "block_broken",
                    statKey = "minecraft:dirt",
                    source = "",
                    bucket = (1_700_000_000_000L / 60_000L * 60_000L) + index * 60_000L,
                    amount = 3,
                )
            }
        )
        val data = StatsRepository(database).chart(
            worldId = "world",
            playerUuid = "player",
            group = StatGroup.BLOCK,
            keys = listOf("minecraft:stone", "minecraft:dirt"),
            from = 0,
            to = Long.MAX_VALUE,
            granularity = Granularity.HOUR,
            metrics = listOf(cn.enaium.allinstats.model.StatType.BLOCK_BROKEN),
            sources = listOf(StatSource.NONE),
            candle = false,
        )
        assertEquals(2, data.series.size)
        assertEquals(setOf("minecraft:stone", "minecraft:dirt"), data.series.map { it.key }.toSet())
        assertEquals(477.0, data.series.first { it.key == "minecraft:stone" }.total, 0.001)
        assertEquals(360.0, data.series.first { it.key == "minecraft:dirt" }.total, 0.001)

        // the csv names the counters of every series
        val csv = directory.resolve("multi.csv").toFile()
        CsvExport.write(csv, data, showKey = true, showSource = false)
        val header = csv.readLines().first()
        assertTrue(header.contains("minecraft:stone.block_broken"), header)
        assertTrue(header.contains("minecraft:dirt.block_broken"), header)
    }

    @Test
    fun `the time axis is labelled for any range`() {
        val zone = java.time.ZoneId.systemDefault()
        val base = 1_700_000_000_000L
        // a single bucket has to be labelled as well
        val single = cn.enaium.allinstats.query.TimeAxis.ticks(base, base + 60_000, zone = zone)
        assertTrue(single.first.size >= 2, "a one minute range has " + single.first.size + " ticks")
        assertTrue(single.second.all { it.isNotBlank() })
        // a zoomed range keeps dates instead of numbers
        val zoomed = cn.enaium.allinstats.query.TimeAxis.ticks(base, base + 15 * 60_000, zone = zone)
        assertTrue(zoomed.second.all { it.contains(":") }, zoomed.second.joinToString())
        val year = cn.enaium.allinstats.query.TimeAxis.ticks(base, base + 365L * 24 * 60 * 60 * 1000, zone = zone)
        assertTrue(year.first.size >= 2)
        assertTrue(year.second.all { it.length == 7 || it.length == 10 }, year.second.joinToString())
    }

    @Test
    fun `a candlestick chart is written as png`() {
        val (database, _) = chart()
        val data = StatsRepository(database).chart(
            worldId = "world",
            playerUuid = "player",
            group = StatGroup.BLOCK,
            keys = listOf("minecraft:stone"),
            from = 0,
            to = Long.MAX_VALUE,
            granularity = Granularity.DAY,
            metrics = listOf(cn.enaium.allinstats.model.StatType.BLOCK_BROKEN),
            sources = listOf(StatSource.NONE),
            candle = true,
        )
        val png = directory.resolve("candle.png").toFile()
        ChartImageExport.write(png, data, ChartType.CANDLE, "minecraft:stone", data.dataFrom, data.dataTo)
        val image = ImageIO.read(png)
        val colors = HashSet<Int>()
        for (x in 0 until image.width step 3) {
            for (y in 0 until image.height step 3) {
                colors.add(image.getRGB(x, y))
            }
        }
        assertTrue(colors.size > 10, "the candle image has only ${colors.size} colors")
        assertTrue(data.series.first().high.isNotEmpty(), "the candle series has no open/high/low/close")
    }
}
