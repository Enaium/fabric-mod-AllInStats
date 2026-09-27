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

package cn.enaium.allinstats.export

import cn.enaium.allinstats.model.ChartType
import cn.enaium.allinstats.model.StatSource
import cn.enaium.allinstats.query.ChartData
import cn.enaium.allinstats.query.ChartSeries
import cn.enaium.allinstats.query.TimeAxis
import java.awt.*
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO

/**
 * Writes the chart data as CSV - one row per bucket, one column per series.
 *
 * @author Enaium
 */
object CsvExport {
    fun write(
        file: File,
        data: ChartData,
        showKey: Boolean = false,
        showSource: Boolean = true,
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.append("time")
            data.series.forEach { series ->
                writer.append(',').append(csv(column(series, showKey, showSource)))
            }
            writer.newLine()
            for (index in data.times.indices) {
                writer.append(Instant.ofEpochMilli(data.times[index].toLong()).atZone(zone).format(formatter))
                data.series.forEach { series ->
                    writer.append(',').append(number(series.values.getOrElse(index) { 0.0 }))
                }
                writer.newLine()
            }
        }
    }

    private fun column(series: ChartSeries, showKey: Boolean, showSource: Boolean): String {
        val parts = ArrayList<String>(3)
        if (showKey) {
            parts.add(series.key)
        }
        parts.add(series.type.id)
        if (showSource && series.source != StatSource.NONE) {
            parts.add(series.source.id)
        }
        return parts.joinToString(".")
    }

    private fun number(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private fun csv(value: String): String =
        if (value.any { it == ',' || it == '"' }) "\"${value.replace("\"", "\"\"")}\"" else value
}

/**
 * Renders the chart with AWT, the resulting PNG is independent of the running game.
 *
 * @author Enaium
 */
object ChartImageExport {
    /**
     * On macOS the game owns the main thread (GLFW/AppKit), the AWT toolkit never finishes its
     * initialization from another thread there and the export would block forever, so the image is
     * drawn without a window system on that platform.
     */
    private fun prepareToolkit() {
        if (isMac) {
            System.setProperty("java.awt.headless", "true")
        }
    }

    private val isMac: Boolean = System.getProperty("os.name").lowercase().contains("mac")

    /**
     * The toolkit cannot always be switched to the headless mode (another mod may have initialized
     * it already), so the drawing is watched: a deadlocked toolkit would hold the export thread
     * forever, which is reported instead.
     */
    private fun <T> watched(work: () -> T): T {
        var result: Result<T>? = null
        val thread = Thread({
            result = runCatching(work)
        }, "AllInStats Image")
        thread.isDaemon = true
        thread.start()
        thread.join(WATCHDOG_MILLIS)
        if (thread.isAlive) {
            throw IllegalStateException(
                "the image toolkit did not start, the export is not available in this environment"
            )
        }
        return result!!.getOrThrow()
    }

    private const val WATCHDOG_MILLIS = 30_000L

    private val background = Color(24, 26, 32)
    private val panel = Color(32, 35, 42)
    private val foreground = Color(220, 224, 232)
    private val grid = Color(58, 62, 72)
    private val palette = listOf(
        Color(94, 168, 255),
        Color(255, 153, 87),
        Color(129, 212, 133),
        Color(232, 103, 145),
        Color(198, 160, 255),
        Color(255, 220, 120),
    )
    private val up = Color(224, 96, 96)
    private val down = Color(104, 200, 132)

    fun write(
        file: File,
        data: ChartData,
        type: ChartType,
        title: String,
        from: Long,
        to: Long,
        showKey: Boolean = false,
        showSource: Boolean = true,
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        prepareToolkit()
        watched { render(file, data, type, title, from, to, showKey, showSource, zone) }
    }

    private fun render(
        file: File,
        data: ChartData,
        type: ChartType,
        title: String,
        from: Long,
        to: Long,
        showKey: Boolean,
        showSource: Boolean,
        zone: ZoneId,
    ) {

        val width = 1280
        val height = 720
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.color = background
            graphics.fillRect(0, 0, width, height)

            val left = 90
            val right = width - 40
            val top = 108
            val bottom = height - 60
            graphics.color = panel
            graphics.fillRoundRect(left, top, right - left, bottom - top, 12, 12)

            val visible = data.series.flatMap { series ->
                val values = if (type == ChartType.CANDLE) series.high.toList() else series.values.toList()
                values.filterIndexed { index, _ -> data.times[index] >= from && data.times[index] <= to }
            }
            val max = visible.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0

            drawGrid(graphics, left, top, right, bottom, max)
            val (ticks, labels) = TimeAxis.ticks(from, to, data.granularity, zone)
            drawTimeAxis(graphics, data, from, to, ticks, labels, left, top, right, bottom)

            data.series.forEachIndexed { index, series ->
                val color = palette[index % palette.size]
                when (type) {
                    ChartType.LINE, ChartType.AREA -> drawLine(graphics, data, from, to, series.values, color, left, top, right, bottom, max, type == ChartType.AREA)
                    ChartType.BAR -> drawBars(graphics, data, from, to, series.values, color, left, top, right, bottom, max, data.series.size, index)
                    ChartType.SCATTER -> drawScatter(graphics, data, from, to, series.values, color, left, top, right, bottom, max)
                    ChartType.CANDLE -> drawCandles(graphics, data, from, to, series, left, top, right, bottom, max)
                }
            }

            graphics.color = foreground
            graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 24)
            graphics.drawString(title, left, 44)
            graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, 14)
            var legendX = left
            data.series.forEachIndexed { index, series ->
                val color = palette[index % palette.size]
                graphics.color = color
                graphics.fillRect(legendX, 66, 14, 14)
                graphics.color = foreground
                val label = cn.enaium.allinstats.gui.StatNames.seriesLabel(
                    series,
                    cn.enaium.allinstats.model.StatGroup.values().first { it.types.contains(series.type) },
                    showSource,
                    showKey,
                )
                graphics.drawString(label, legendX + 20, 78)
                legendX += 20 + graphics.fontMetrics.stringWidth(label) + 30
            }
        } finally {
            graphics.dispose()
        }
        ImageIO.write(image, "png", file)
    }

    private fun drawGrid(graphics: Graphics2D, left: Int, top: Int, right: Int, bottom: Int, max: Double) {
        graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, 13)
        val steps = 5
        for (step in 0..steps) {
            val value = max / steps * step
            val y = bottom - ((bottom - top) * step / steps.toDouble()).toInt()
            graphics.color = grid
            graphics.drawLine(left, y, right, y)
            graphics.color = foreground
            val text = if (value == value.toLong().toDouble()) value.toLong().toString() else String.format("%.1f", value)
            graphics.drawString(text, left - 12 - graphics.fontMetrics.stringWidth(text), y + 5)
        }
    }

    private fun position(time: Double, from: Long, to: Long, left: Int, right: Int): Int {
        if (to <= from) {
            return (left + right) / 2
        }
        return left + (((time - from) / (to - from).toDouble()) * (right - left)).toInt()
    }

    private fun visible(time: Double, from: Long, to: Long): Boolean = time >= from && time <= to

    private fun valueAt(value: Double, max: Double, top: Int, bottom: Int): Int =
        bottom - ((value / max) * (bottom - top)).toInt()

    private fun drawTimeAxis(
        graphics: Graphics2D,
        data: ChartData,
        from: Long,
        to: Long,
        ticks: DoubleArray,
        labels: Array<String>,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
        for (index in ticks.indices) {
            val x = position(ticks[index], from, to, left, right)
            graphics.color = grid
            graphics.drawLine(x, top, x, bottom)
            graphics.color = foreground
            val text = labels[index]
            graphics.drawString(text, x - graphics.fontMetrics.stringWidth(text) / 2, bottom + 22)
        }
    }

    private fun drawLine(
        graphics: Graphics2D,
        data: ChartData,
        from: Long,
        to: Long,
        values: DoubleArray,
        color: Color,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        max: Double,
        area: Boolean,
    ) {
        if (values.isEmpty()) {
            return
        }
        val path = Path2D.Double()
        var first: Double? = null
        var last: Double? = null
        values.indices.forEach { index ->
            val time = data.times[index]
            if (!visible(time, from, to)) {
                return@forEach
            }
            val x = position(time, from, to, left, right).toDouble()
            val y = valueAt(values[index], max, top, bottom).toDouble()
            if (first == null) {
                first = x
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
            last = x
        }
        if (first == null || last == null) {
            return
        }
        if (area) {
            val fill = Path2D.Double(path)
            fill.lineTo(last, bottom.toDouble())
            fill.lineTo(first, bottom.toDouble())
            fill.closePath()
            graphics.color = Color(color.red, color.green, color.blue, 64)
            graphics.fill(fill)
        }
        graphics.color = color
        graphics.stroke = BasicStroke(2f)
        graphics.draw(path)
    }

    private fun drawBars(
        graphics: Graphics2D,
        data: ChartData,
        from: Long,
        to: Long,
        values: DoubleArray,
        color: Color,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        max: Double,
        seriesCount: Int,
        seriesIndex: Int,
    ) {
        if (values.isEmpty() || to <= from) {
            return
        }
        val step = (right - left).toDouble() / (data.times.count { it >= from && it <= to }).coerceAtLeast(1)
        val width = (step / seriesCount).coerceAtLeast(1.0)
        graphics.color = color
        var visibleIndex = 0
        values.indices.forEach { index ->
            val time = data.times[index]
            if (!visible(time, from, to)) {
                return@forEach
            }
            val x = left + visibleIndex * step + width * seriesIndex
            visibleIndex++
            val y = valueAt(values[index], max, top, bottom)
            graphics.fillRect(x.toInt(), y, width.toInt().coerceAtLeast(1), bottom - y)
        }
    }

    private fun drawScatter(
        graphics: Graphics2D,
        data: ChartData,
        from: Long,
        to: Long,
        values: DoubleArray,
        color: Color,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        max: Double,
    ) {
        graphics.color = color
        values.indices.forEach { index ->
            val time = data.times[index]
            if (!visible(time, from, to)) {
                return@forEach
            }
            val x = position(time, from, to, left, right)
            val y = valueAt(values[index], max, top, bottom)
            graphics.fillOval(x - 3, y - 3, 6, 6)
        }
    }

    private fun drawCandles(
        graphics: Graphics2D,
        data: ChartData,
        from: Long,
        to: Long,
        series: cn.enaium.allinstats.query.ChartSeries,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        max: Double,
    ) {
        if (data.times.isEmpty() || to <= from) {
            return
        }
        val step = (right - left).toDouble() / data.times.count { it >= from && it <= to }.coerceAtLeast(1)
        var visibleIndex = 0
        data.times.indices.forEach { index ->
            if (!visible(data.times[index], from, to)) {
                return@forEach
            }
            val open = series.open.getOrElse(index) { 0.0 }
            val close = series.close.getOrElse(index) { 0.0 }
            val high = series.high.getOrElse(index) { 0.0 }
            val low = series.low.getOrElse(index) { 0.0 }
            if (high == 0.0 && low == 0.0) {
                return@forEach
            }
            val x = left + step * visibleIndex + step / 2
            visibleIndex++
            val color = if (close >= open) up else down
            graphics.color = color
            graphics.stroke = BasicStroke(1.2f)
            graphics.drawLine(x.toInt(), valueAt(low, max, top, bottom), x.toInt(), valueAt(high, max, top, bottom))
            val topY = valueAt(maxOf(open, close), max, top, bottom)
            val bottomY = valueAt(minOf(open, close), max, top, bottom)
            val width = (step * 0.6).toInt().coerceAtLeast(2)
            graphics.fillRect((x - width / 2).toInt(), topY, width, (bottomY - topY).coerceAtLeast(1))
        }
    }
}
