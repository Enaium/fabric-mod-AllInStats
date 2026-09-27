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

package cn.enaium.allinstats.gui

import cn.enaium.allinstats.model.ChartType
import cn.enaium.allinstats.model.StatGroup
import cn.enaium.allinstats.query.ChartData
import cn.enaium.allinstats.query.ChartSeries
import cn.enaium.allinstats.query.TimeAxis
import imgui.ImGui
import imgui.flag.ImGuiMouseButton
import imgui.extension.implot.ImPlot
import imgui.extension.implot.ImPlotSpec
import imgui.extension.implot.flag.ImPlotAxis
import imgui.extension.implot.flag.ImPlotAxisFlags
import imgui.extension.implot.flag.ImPlotCond
import imgui.extension.implot.flag.ImPlotFlags
import imgui.extension.implot.flag.ImPlotLegendFlags
import imgui.extension.implot.flag.ImPlotLocation
import imgui.extension.implot.flag.ImPlotMarker

/**
 * The time window of a plot. The range is owned by the mod instead of ImPlot, because ImPlot can
 * only label an axis with numbers (the binding has no formatter callback) while a zoomed time axis
 * has to stay readable.
 *
 * @author Enaium
 */
class PlotWindow {
    var from: Long = 0L
        private set
    var to: Long = 0L
        private set

    var valid: Boolean = false
        private set

    fun set(from: Long, to: Long) {
        if (to <= from) {
            return
        }
        this.from = from
        this.to = to
        valid = true
    }

    fun reset() {
        valid = false
    }

    fun reset(from: Long, to: Long) {
        valid = false
        set(from, to)
    }

    val span: Long get() = to - from
}

/**
 * Draws the series with ImPlot. Zooming, panning and fitting are handled here, the axis is labelled
 * with dates of the visible window.
 *
 * @author Enaium
 */
object ImPlotChart {
    private const val HEIGHT = 360f
    private const val MIN_SPAN = 60_000L

    private val spec = ImPlotSpec()

    private val palette = arrayOf(
        floatArrayOf(0.37f, 0.66f, 1f, 1f),
        floatArrayOf(1f, 0.6f, 0.34f, 1f),
        floatArrayOf(0.5f, 0.83f, 0.52f, 1f),
        floatArrayOf(0.91f, 0.4f, 0.57f, 1f),
        floatArrayOf(0.78f, 0.63f, 1f, 1f),
        floatArrayOf(1f, 0.86f, 0.47f, 1f),
        floatArrayOf(0.45f, 0.85f, 0.85f, 1f),
        floatArrayOf(0.95f, 0.75f, 0.6f, 1f),
        floatArrayOf(0.6f, 0.7f, 1f, 1f),
        floatArrayOf(0.9f, 0.5f, 0.8f, 1f),
        floatArrayOf(0.65f, 0.9f, 0.5f, 1f),
        floatArrayOf(1f, 0.55f, 0.55f, 1f),
        floatArrayOf(0.5f, 0.75f, 0.75f, 1f),
        floatArrayOf(0.85f, 0.8f, 0.4f, 1f),
        floatArrayOf(0.7f, 0.55f, 0.95f, 1f),
        floatArrayOf(0.45f, 0.6f, 0.9f, 1f),
        floatArrayOf(0.95f, 0.65f, 0.35f, 1f),
        floatArrayOf(0.55f, 0.85f, 0.65f, 1f),
        floatArrayOf(0.85f, 0.5f, 0.5f, 1f),
    )

    private val up = floatArrayOf(0.88f, 0.36f, 0.36f, 1f)
    private val down = floatArrayOf(0.35f, 0.75f, 0.45f, 1f)

    /**
     * A line needs two points to be drawn, so a chart with few buckets marks every value as well.
     */
    private const val MARKER_LIMIT = 64

    /**
     * @param id identity of the shown data, ImPlot remembers the state of every plot.
     * @param window the visible time window, it is updated by the interaction of the user.
     */
    fun draw(
        id: String,
        data: ChartData,
        group: StatGroup,
        type: ChartType,
        showSource: Boolean,
        showKey: Boolean,
        window: PlotWindow,
        resetZoom: Boolean,
    ) {
        val width = ImGui.getContentRegionAvail().x.coerceAtLeast(320f)
        val from = dataFrom(data)
        val to = dataTo(data)
        if (!window.valid || resetZoom || window.to < from || window.from > to) {
            // a window that does not overlap the data (or was never set) would draw an empty plot
            window.set(from, to)
        }
        legend(data, group, showSource, showKey)
        ImPlot.setNextAxisLimits(ImPlotAxis.X1, window.from.toDouble(), window.to.toDouble(), ImPlotCond.Always)
        if (!ImPlot.beginPlot(
                "##$id",
                width,
                HEIGHT,
                ImPlotFlags.NoTitle or ImPlotFlags.NoLegend or ImPlotFlags.NoMouseText,
            )
        ) {
            return
        }
        ImPlot.setupAxes("", "", ImPlotAxisFlags.NoLabel, ImPlotAxisFlags.AutoFit)
        ImPlot.setupAxisFormat(ImPlotAxis.Y1, "%.0f")
        val (ticks, labels) = TimeAxis.ticks(window.from, window.to, data.granularity)
        if (ticks.isNotEmpty()) {
            ImPlot.setupAxisTicks(ImPlotAxis.X1, ticks, ticks.size, labels)
        }
        data.series.forEachIndexed { index, series ->
            when (type) {
                ChartType.LINE -> line(series, index, group, showSource, showKey)
                ChartType.AREA -> area(series, index, group, showSource, showKey)
                ChartType.BAR -> bars(series, index, data, group, showSource, showKey)
                ChartType.SCATTER -> scatter(series, index, group, showSource, showKey)
                ChartType.CANDLE -> candles(series, index, group, showSource, showKey)
            }
        }
        readout(window)
        interact(window, data)
        ImPlot.endPlot()
    }

    /**
     * The series are listed above the plot with the colour they are drawn with, the legend of
     * ImPlot would cover the chart itself.
     */
    private fun legend(data: ChartData, group: StatGroup, showSource: Boolean, showKey: Boolean) {
        val available = ImGui.getContentRegionAvail().x
        var used = 0f
        data.series.forEachIndexed { index, series ->
            val color = color(index)
            val label = StatNames.seriesLabel(series, group, showSource, showKey)
            val width = ImGui.calcTextSize("\u25A0  $label").x + 24f
            if (used > 0f && used + width > available) {
                used = 0f
            } else if (used > 0f) {
                ImGui.sameLine()
            }
            ImGui.textColored(color[0], color[1], color[2], 1f, "\u25A0")
            ImGui.sameLine()
            ImGui.textUnformatted(label)
            used += width
        }
    }

    /**
     * ImPlot can only print the raw value of the cursor, the readout is written here so that the
     * time is shown as a date.
     */
    private fun readout(window: PlotWindow) {
        if (!ImPlot.isPlotHovered()) {
            return
        }
        val mouse = ImPlot.getPlotMousePos()
        val time = mouse.x.toLong().coerceIn(window.from, window.to)
        ImGui.textColored(
            0.6f,
            0.65f,
            0.7f,
            1f,
            StatsViewModel.format(time) + "  ·  " + if (mouse.y == mouse.y.toLong().toDouble()) {
                mouse.y.toLong().toString()
            } else {
                String.format("%.2f", mouse.y)
            },
        )
    }

    private fun dataFrom(data: ChartData): Long {
        val first = data.dataFrom
        val last = data.dataTo
        val span = (last - first).coerceAtLeast(MIN_SPAN)
        return first - span / 20
    }

    private fun dataTo(data: ChartData): Long {
        val first = data.dataFrom
        val last = data.dataTo
        val span = (last - first).coerceAtLeast(MIN_SPAN)
        return last + span / 20
    }

    /**
     * Zooming with the wheel, panning by dragging and fitting with a double click.
     */
    private fun interact(window: PlotWindow, data: ChartData) {
        if (!ImPlot.isPlotHovered()) {
            return
        }
        val io = ImGui.getIO()
        if (io.mouseWheel != 0f) {
            val mouse = ImPlot.getPlotMousePos().x
            val factor = if (io.mouseWheel > 0) 0.8 else 1.25
            var from = mouse - (mouse - window.from) * factor
            var to = mouse + (window.to - mouse) * factor
            if (to - from < MIN_SPAN) {
                val center = (from + to) / 2
                from = center - MIN_SPAN / 2
                to = center + MIN_SPAN / 2
            }
            window.set(from.toLong(), to.toLong())
        }
        if (ImGui.isMouseDragging(ImGuiMouseButton.Left)) {
            val delta = ImGui.getMouseDragDelta(ImGuiMouseButton.Left)
            val start = ImPlot.pixelsToPlot(0f, 0f).x
            val end = ImPlot.pixelsToPlot(delta.x, 0f).x
            val shift = (start - end).toLong()
            if (shift != 0L) {
                window.set(window.from + shift, window.to + shift)
                ImGui.resetMouseDragDelta(ImGuiMouseButton.Left)
            }
        }
        if (ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) {
            window.reset(dataFrom(data), dataTo(data))
        }
    }

    private fun color(index: Int): FloatArray = palette[index % palette.size]

    private fun line(series: ChartSeries, index: Int, group: StatGroup, showSource: Boolean, showKey: Boolean) {
        val line = color(index)
        spec.setLineColor(line[0], line[1], line[2], 1f)
        spec.setLineWeight(2f)
        marker(series)
        ImPlot.plotLine(label(series, group, showSource, showKey), series.times, series.values, spec)
    }

    /**
     * Marks the values of a series that has so few buckets that the line alone would not show them.
     */
    private fun marker(series: ChartSeries) {
        val marked = series.times.size in 1..MARKER_LIMIT
        spec.setMarker(if (marked) ImPlotMarker.Circle else ImPlotMarker.None)
        spec.setMarkerSize(3f)
        spec.setMarkerFillColor(spec.getLineColorX(), spec.getLineColorY(), spec.getLineColorZ(), 1f)
    }

    private fun area(series: ChartSeries, index: Int, group: StatGroup, showSource: Boolean, showKey: Boolean) {
        val line = color(index)
        spec.setLineColor(line[0], line[1], line[2], 1f)
        spec.setFillColor(line[0], line[1], line[2], 0.25f)
        spec.setLineWeight(2f)
        marker(series)
        ImPlot.plotShaded(label(series, group, showSource, showKey), series.times, series.values, series.zeros, spec)
    }

    private fun bars(series: ChartSeries, index: Int, data: ChartData, group: StatGroup, showSource: Boolean, showKey: Boolean) {
        val times = series.times
        if (times.isEmpty()) {
            return
        }
        val width = if (data.times.size > 1) (data.times[1] - data.times[0]) else 60_000.0
        val slot = width / data.series.size.coerceAtLeast(1)
        val shift = (index - (data.series.size - 1) / 2.0) * slot
        val shifted = DoubleArray(times.size) { times[it] + shift }
        val line = color(index)
        spec.setFillColor(line[0], line[1], line[2], 0.85f)
        spec.setLineColor(line[0], line[1], line[2], 1f)
        ImPlot.plotBarsV(label(series, group, showSource, showKey), shifted, series.values, slot, spec)
    }

    private fun scatter(series: ChartSeries, index: Int, group: StatGroup, showSource: Boolean, showKey: Boolean) {
        val line = color(index)
        spec.setMarkerLineColor(line[0], line[1], line[2], 1f)
        spec.setMarkerFillColor(line[0], line[1], line[2], 1f)
        ImPlot.plotScatter(label(series, group, showSource, showKey), series.times, series.values, spec)
    }

    /**
     * The binding has no candlestick among its plot functions, so every candle is drawn with the
     * wick and the body as two error bars - a thin one from the low to the high and a thick one from
     * the open to the close.
     */
    private fun candles(series: ChartSeries, index: Int, group: StatGroup, showSource: Boolean, showKey: Boolean) {
        val times = series.times
        if (times.isEmpty()) {
            return
        }
        val label = label(series, group, showSource, showKey)
        for (i in times.indices) {
            val open = series.open.getOrElse(i) { 0.0 }
            val close = series.close.getOrElse(i) { 0.0 }
            val high = series.high.getOrElse(i) { 0.0 }
            val low = series.low.getOrElse(i) { 0.0 }
            val center = (high + low) / 2.0
            val line = if (close >= open) up else down
            spec.setLineColor(line[0], line[1], line[2], 1f)
            spec.setLineWeight(1f)
            ImPlot.plotErrorBars(
                "##$label",
                doubleArrayOf(times[i]),
                doubleArrayOf(center),
                doubleArrayOf((center - low).coerceAtLeast(0.0)),
                doubleArrayOf((high - center).coerceAtLeast(0.0)),
                spec,
            )
            val bodyCenter = (open + close) / 2.0
            spec.setLineWeight(6f)
            ImPlot.plotErrorBars(
                "##$label",
                doubleArrayOf(times[i]),
                doubleArrayOf(bodyCenter),
                doubleArrayOf((bodyCenter - minOf(open, close)).coerceAtLeast(0.0)),
                doubleArrayOf((maxOf(open, close) - bodyCenter).coerceAtLeast(0.0)),
                spec,
            )
        }
    }

    private fun label(series: ChartSeries, group: StatGroup, showSource: Boolean, showKey: Boolean): String =
        StatNames.seriesLabel(series, group, showSource, showKey)
}
