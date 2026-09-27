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

import cn.enaium.allinstats.AllInStats
import cn.enaium.allinstats.i18n.I18n
import cn.enaium.allinstats.model.ChartType
import cn.enaium.allinstats.model.Granularity
import cn.enaium.allinstats.model.StatGroup
import cn.enaium.allinstats.model.StatSource
import cn.enaium.allinstats.model.StatType
import imgui.ImGui
import imgui.ImGuiIO
import imgui.flag.ImGuiTableColumnFlags
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import imgui.type.ImString

/**
 * The whole interface is drawn by ImGui, [render] is called by the game side for every frame the
 * screen of this mod is shown.
 *
 * @author Enaium
 */
object StatsGui {
    internal val view = StatsViewModel()

    private val searchText = ImString(64)
    private val fromText = ImString(32)
    private val toText = ImString(32)
    internal val window = PlotWindow()
    private var plotId = ""
    private val sortBox = ImBoolean(true)
    private val autoRefreshBox = ImBoolean(true)
    private val sourceBoxes = StatSource.values().associateWith { ImBoolean(true) }
    private val metricBoxes = StatType.values().associateWith { ImBoolean(false) }
    private var fromTextValue = ""
    private var toTextValue = ""
    private var syncedFrom = Long.MIN_VALUE
    private var syncedTo = Long.MIN_VALUE

    /**
     * Called by the game side when the interface is opened.
     */
    fun open(host: StatsHost) {
        view.initialize(host)
        searchText.set(view.search)
        syncRangeText()
        syncBoxes()
    }

    fun render(io: ImGuiIO) {
        view.update()
        StatsFonts.push()
        try {
            renderFrame(io)
        } finally {
            StatsFonts.pop()
        }
    }

    private fun renderFrame(io: ImGuiIO) {

        val viewport = ImGui.getMainViewport()
        ImGui.setNextWindowPos(viewport.posX, viewport.posY)
        ImGui.setNextWindowSize(viewport.sizeX, viewport.sizeY)
        ImGui.setNextWindowViewport(viewport.id)
        if (!ImGui.begin(
                I18n.translated("ui.title"),
                ImGuiWindowFlags.NoDecoration or ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoBringToFrontOnFocus
            )
        ) {
            ImGui.end()
            return
        }

        header()

        if (AllInStats.db == null) {
            ImGui.spacing()
            ImGui.textColored(0.9f, 0.5f, 0.4f, 1f, I18n.translated("ui.not_ready"))
            AllInStats.openError?.also { error ->
                ImGui.textColored(0.7f, 0.7f, 0.7f, 1f, error)
            }
            ImGui.end()
            return
        }
        if (view.worlds.isEmpty()) {
            ImGui.spacing()
            ImGui.textColored(0.7f, 0.7f, 0.7f, 1f, I18n.translated("ui.empty_db"))
            ImGui.end()
            return
        }

        ImGui.spacing()
        val available = ImGui.getContentRegionAvail()
        val leftWidth = 320f
        if (ImGui.beginChild("##selection", leftWidth, available.y - 8f, true)) {
            selection()
        }
        ImGui.endChild()
        ImGui.sameLine()
        if (ImGui.beginChild("##detail", available.x - leftWidth - 16f, available.y - 8f, true)) {
            detail()
        }
        ImGui.endChild()

        ImGui.end()
    }

    private fun header() {
        if (ImGui.button(I18n.translated("ui.vanilla"))) {
            view.host?.openVanillaStatistics()
        }
        ImGui.sameLine()
        if (ImGui.button(I18n.translated("ui.export_csv"))) {
            view.exportCsv(windowFrom(), windowTo())
        }
        ImGui.sameLine()
        if (ImGui.button(I18n.translated("ui.export_image"))) {
            view.exportImage(windowFrom(), windowTo())
        }
        ImGui.sameLine()
        ImGui.setNextItemWidth(150f)
        val languages = I18n.languages
        if (ImGui.beginCombo("##language", I18n.translated(languageKey(I18n.language)))) {
            languages.forEach { language ->
                if (ImGui.selectable(I18n.translated(languageKey(language)), language == I18n.language)) {
                    I18n.setLanguage(language)
                }
            }
            ImGui.endCombo()
        }
        ImGui.sameLine()
        if (view.loading) {
            ImGui.textColored(0.7f, 0.7f, 0.7f, 1f, I18n.translated("ui.loading"))
            ImGui.sameLine()
        }
        ImGui.textColored(0.6f, 0.65f, 0.7f, 1f, I18n.translated("ui.pending", AllInStats.pending()))
        view.message?.also { message ->
            ImGui.sameLine()
            ImGui.textColored(0.5f, 0.8f, 0.5f, 1f, message)
        }
    }

    /**
     * World, player, category and the list of objects.
     */
    private fun selection() {
        ImGui.textColored(0.6f, 0.65f, 0.7f, 1f, I18n.translated("ui.world"))
        ImGui.setNextItemWidth(-1f)
        val worldName = view.worlds.firstOrNull { it.worldId == view.worldId }?.name ?: view.worldName
        if (ImGui.beginCombo("##world", worldName)) {
            view.worlds.forEach { world ->
                if (ImGui.selectable("${world.name}##${world.worldId}", world.worldId == view.worldId)) {
                    view.world(world.worldId)
                }
            }
            ImGui.endCombo()
        }

        ImGui.spacing()
        ImGui.textColored(0.6f, 0.65f, 0.7f, 1f, I18n.translated("ui.player"))
        ImGui.setNextItemWidth(-1f)
        val playerName = view.players.firstOrNull { it.uuid == view.playerUuid }?.name
            ?: I18n.translated("ui.all_players")
        if (ImGui.beginCombo("##player", playerName)) {
            if (ImGui.selectable(I18n.translated("ui.all_players"), view.playerUuid == null)) {
                view.player(null)
            }
            view.players.forEach { player ->
                if (ImGui.selectable("${player.name}##${player.uuid}", player.uuid == view.playerUuid)) {
                    view.player(player.uuid)
                }
            }
            ImGui.endCombo()
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.textColored(0.6f, 0.65f, 0.7f, 1f, I18n.translated("ui.category"))
        ImGui.spacing()
        StatGroup.values().forEach { group ->
            if (ImGui.selectable(
                    I18n.translated("category.${group.id}") + "##group",
                    group == view.group,
                )
            ) {
                view.group(group)
            }
        }

        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()
        ImGui.setNextItemWidth(-1f)
        if (ImGui.inputTextWithHint("##search", I18n.translated("ui.search"), searchText)) {
            view.search(searchText.get())
        }
        sortBox.set(view.sortByTotal)
        if (ImGui.checkbox(I18n.translated("ui.sort.total"), sortBox)) {
            view.sortByTotal = sortBox.get()
            view.reload()
        }

        ImGui.spacing()
        ImGui.textColored(0.6f, 0.65f, 0.7f, 1f, I18n.translated("ui.count", view.keys.size))
        ImGui.sameLine()
        if (ImGui.smallButton(I18n.translated("ui.select_all"))) {
            view.selectAll()
        }
        ImGui.sameLine()
        if (ImGui.smallButton(I18n.translated("ui.clear_selection"))) {
            view.clearKeys()
        }
        ImGui.textColored(0.6f, 0.65f, 0.7f, 1f, I18n.translated("ui.selected", view.selectedKeys.size))
        ImGui.spacing()
        if (ImGui.beginChild("##keys", 0f, 0f, false)) {
            view.keys.forEach { entry ->
                val selected = view.selectedKeys.contains(entry.key)
                val label = (if (selected) "[x] " else "[  ] ") +
                    "${StatNames.display(view.group, entry.key)} (${entry.total})##${entry.key}"
                if (ImGui.selectable(label, selected)) {
                    view.toggleKey(entry.key)
                }
            }
        }
        ImGui.endChild()
    }

    /**
     * Chart controls, the plot and the summary.
     */
    private fun detail() {
        // the controls are always there: a range without any counter must still be changeable
        when {
            view.selectedKeys.isNotEmpty() -> {
                ImGui.textColored(0.85f, 0.85f, 0.9f, 1f, view.title())
                ImGui.sameLine()
                if (view.selectedKeys.size == 1) {
                    ImGui.textColored(0.6f, 0.65f, 0.7f, 1f, view.selectedKeys.first())
                }
            }

            view.keys.isEmpty() -> ImGui.textColored(0.7f, 0.7f, 0.7f, 1f, I18n.translated("ui.no_data"))
            else -> ImGui.textColored(0.7f, 0.7f, 0.7f, 1f, I18n.translated("ui.select_keys"))
        }
        ImGui.separator()

        controls()
        ImGui.spacing()
        chart()
        ImGui.spacing()
        summary()

        if (view.chartType == ChartType.CANDLE) {
            ImGui.textColored(
                0.7f,
                0.7f,
                0.7f,
                1f,
                I18n.translated("ui.candle_hint", StatsViewModel.CANDLE_RANGE / (24 * 60 * 60 * 1000)),
            )
        }
    }

    private fun controls() {
        syncRangeText()
        ImGui.textColored(0.6f, 0.65f, 0.7f, 1f, I18n.translated("ui.chart_type"))
        ImGui.sameLine()
        ImGui.setNextItemWidth(130f)
        if (ImGui.beginCombo("##chart", I18n.translated("chart.${view.chartType.id}"))) {
            ChartType.values().forEach { type ->
                val enabled = type != ChartType.CANDLE || view.candleAllowed
                if (ImGui.selectable(I18n.translated("chart.${type.id}") + if (enabled) "" else " (1)", type == view.chartType)) {
                    view.chartType(type)
                }
            }
            ImGui.endCombo()
        }
        ImGui.sameLine()
        ImGui.textColored(0.6f, 0.65f, 0.7f, 1f, I18n.translated("ui.granularity"))
        ImGui.sameLine()
        ImGui.setNextItemWidth(110f)
        if (ImGui.beginCombo("##granularity", I18n.translated("granularity.${view.granularity.id}"))) {
            Granularity.values().forEach { granularity ->
                if (ImGui.selectable(
                        I18n.translated("granularity.${granularity.id}"),
                        granularity == view.granularity
                    )
                ) {
                    view.granularity(granularity)
                }
            }
            ImGui.endCombo()
        }

        ImGui.sameLine()
        ImGui.textColored(0.6f, 0.65f, 0.7f, 1f, I18n.translated("ui.range"))
        ImGui.sameLine()
        if (ImGui.smallButton(I18n.translated("ui.preset.hour"))) view.preset(60L * 60 * 1000)
        ImGui.sameLine()
        if (ImGui.smallButton(I18n.translated("ui.preset.day"))) view.preset(24L * 60 * 60 * 1000)
        ImGui.sameLine()
        if (ImGui.smallButton(I18n.translated("ui.preset.week"))) view.preset(7L * 24 * 60 * 60 * 1000)
        ImGui.sameLine()
        if (ImGui.smallButton(I18n.translated("ui.preset.month"))) view.preset(30L * 24 * 60 * 60 * 1000)
        ImGui.sameLine()
        if (ImGui.smallButton(I18n.translated("ui.preset.year"))) view.preset(365L * 24 * 60 * 60 * 1000)
        ImGui.sameLine()
        if (ImGui.smallButton(I18n.translated("ui.preset.all"))) view.preset(0L)

        ImGui.spacing()
        ImGui.textColored(0.6f, 0.65f, 0.7f, 1f, I18n.translated("ui.from"))
        ImGui.sameLine()
        ImGui.setNextItemWidth(170f)
        if (ImGui.inputTextWithHint("##from", TIME_FORMAT_HINT, fromText)) {
            fromTextValue = fromText.get()
            StatsViewModel.parse(fromTextValue)?.also { view.setRange(it, view.to) }
        }
        ImGui.sameLine()
        ImGui.textColored(0.6f, 0.65f, 0.7f, 1f, I18n.translated("ui.to"))
        ImGui.sameLine()
        ImGui.setNextItemWidth(170f)
        if (ImGui.inputTextWithHint("##to", TIME_FORMAT_HINT, toText)) {
            toTextValue = toText.get()
            StatsViewModel.parse(toTextValue)?.also { view.setRange(view.from, it) }
        }
        // the range can be moved without typing as well
        ImGui.sameLine()
        if (ImGui.smallButton("-1d")) view.shiftRange(-DAY_MILLIS)
        ImGui.sameLine()
        if (ImGui.smallButton("-1h")) view.shiftRange(-HOUR_MILLIS)
        ImGui.sameLine()
        if (ImGui.smallButton("+1h")) view.shiftRange(HOUR_MILLIS)
        ImGui.sameLine()
        if (ImGui.smallButton("+1d")) view.shiftRange(DAY_MILLIS)
        ImGui.sameLine()
        if (ImGui.smallButton(I18n.translated("ui.reset_zoom"))) {
            resetZoom = true
        }
        ImGui.sameLine()
        if (ImGui.smallButton(I18n.translated("ui.refresh"))) {
            view.reload()
        }
        ImGui.sameLine()
        autoRefreshBox.set(view.autoRefresh)
        if (ImGui.checkbox(I18n.translated("ui.auto_refresh"), autoRefreshBox)) {
            view.autoRefresh = autoRefreshBox.get()
        }

        if (StatsViewModel.parse(fromText.get()) == null || StatsViewModel.parse(toText.get()) == null) {
            ImGui.textColored(0.9f, 0.5f, 0.4f, 1f, I18n.translated("ui.time_format", TIME_FORMAT_HINT))
        }
        ImGui.spacing()
        ImGui.textColored(0.6f, 0.65f, 0.7f, 1f, I18n.translated("ui.series"))
        ImGui.sameLine()
        view.group.types.forEach { type ->
            val box = metricBoxes.getValue(type)
            box.set(view.metrics.contains(type))
            if (ImGui.checkbox(I18n.translated("type.${type.id}") + "##metric", box)) {
                view.metric(type, box.get())
            }
            ImGui.sameLine()
        }
        if (view.group.sourceSelectable) {
            ImGui.spacing()
            ImGui.textColored(0.6f, 0.65f, 0.7f, 1f, I18n.translated("ui.source"))
            ImGui.sameLine()
            view.availableSources().forEach { source ->
                val box = sourceBoxes.getValue(source)
                box.set(view.sources.contains(source))
                if (ImGui.checkbox(I18n.translated("source.${source.id}") + "##source", box)) {
                    view.source(source, box.get())
                }
                ImGui.sameLine()
            }
        }
    }

    private var resetZoom = false

    /**
     * The window the plot shows, the export follows it.
     */
    private fun windowFrom(): Long = if (window.valid) window.from else view.from

    private fun windowTo(): Long = if (window.valid) window.to else view.to

    private fun chart() {
        val data = view.chart
        if (view.selectedKeys.isEmpty() || data == null || data.times.isEmpty() || data.series.isEmpty()) {
            ImGui.textColored(0.7f, 0.7f, 0.7f, 1f, I18n.translated("ui.no_data"))
            noDataHint()
            return
        }
        val id = viewId()
        if (id != plotId) {
            plotId = id
            window.reset()
        }
        ImPlotChart.draw(
            id = id,
            data = data,
            group = view.group,
            type = view.chartType,
            showSource = view.group.sourceSelectable && view.selectedKeys.size == 1,
            showKey = view.selectedKeys.size > 1,
            window = window,
            resetZoom = resetZoom,
        )
        resetZoom = false
        ImGui.textColored(0.5f, 0.55f, 0.6f, 1f, I18n.translated("ui.zoom_hint"))
    }

    /**
     * The identity of the current view, it changes whenever the shown counters or the resolution
     * change, so that the plot is fitted again.
     */
    private fun viewId(): String = listOf(
        view.worldId,
        view.playerUuid,
        view.group.id,
        view.selectedKeys.joinToString(","),
        view.chartType.id,
        view.granularity.id,
        view.from,
        view.to,
        view.metrics.joinToString(",") { it.id },
        view.sources.joinToString(",") { it.id },
    ).joinToString("-").replace(' ', '_')

    /**
     * Says where the newest counter of the shown world is, a range that ends before it is empty.
     */
    private fun noDataHint() {
        val last = view.worlds.firstOrNull { it.worldId == view.worldId }?.lastBucket ?: 0L
        if (last > 0L && (last < view.from || last >= view.to)) {
            ImGui.textColored(
                0.7f,
                0.65f,
                0.5f,
                1f,
                I18n.translated("ui.no_data_in_range", StatsViewModel.format(last)),
            )
        }
    }

    private fun summary() {
        val data = view.chart ?: return
        if (data.series.isEmpty()) {
            return
        }
        ImGui.textColored(0.6f, 0.65f, 0.7f, 1f, I18n.translated("ui.summary"))
        ImGui.spacing()
        if (ImGui.beginTable("##summary", 3)) {
            ImGui.tableSetupColumn(I18n.translated("ui.series"), ImGuiTableColumnFlags.WidthFixed, 420f)
            ImGui.tableSetupColumn(I18n.translated("ui.total"), ImGuiTableColumnFlags.WidthFixed, 140f)
            ImGui.tableSetupColumn(I18n.translated("ui.peak"), ImGuiTableColumnFlags.WidthFixed, 140f)
            ImGui.tableHeadersRow()
            val showKey = view.selectedKeys.size > 1
            val showSource = view.group.sourceSelectable && view.selectedKeys.size == 1
            data.series.forEach { series ->
                ImGui.tableNextRow()
                ImGui.tableNextColumn()
                ImGui.textUnformatted(StatNames.seriesLabel(series, view.group, showSource, showKey))
                ImGui.tableNextColumn()
                ImGui.textUnformatted(formatNumber(series.total))
                ImGui.tableNextColumn()
                ImGui.textUnformatted(formatNumber(series.peak))
            }
            ImGui.endTable()
        }
        if (view.group.sourceSelectable && view.selectedKeys.size == 1) {
            val totals = view.totals
            if (totals.isNotEmpty()) {
                ImGui.spacing()
                ImGui.textColored(0.6f, 0.65f, 0.7f, 1f, I18n.translated("ui.source"))
                totals.entries.sortedByDescending { it.value }.forEach { (identity, total) ->
                    ImGui.textUnformatted(
                        "${I18n.translated("type.${identity.first.id}")} / " +
                            "${I18n.translated("source.${identity.second.id}")}: ${formatNumber(total.toDouble())}"
                    )
                }
            }
        }
    }

    /**
     * The text of the "from" field, only used by the verification harness.
     */
    private val TIME_FORMAT_HINT = "yyyy-MM-dd HH:mm"
    private val HOUR_MILLIS = 60L * 60 * 1000
    private val DAY_MILLIS = 24 * HOUR_MILLIS

    internal fun fromField(): String = fromText.get()

    internal fun searchField(): String = searchText.get()

    /**
     * The text of the range follows the range, a preset or a shifted range updates it as well. A
     * field that is being edited is left alone.
     */
    private fun syncRangeText() {
        if (view.from == syncedFrom && view.to == syncedTo) {
            return
        }
        if (ImGui.isAnyItemActive()) {
            return
        }
        syncedFrom = view.from
        syncedTo = view.to
        fromTextValue = StatsViewModel.format(view.from)
        toTextValue = StatsViewModel.format(view.to)
        fromText.set(fromTextValue)
        toText.set(toTextValue)
    }

    private fun syncBoxes() {
        view.metrics.forEach { metricBoxes.getValue(it).set(true) }
        view.sources.forEach { sourceBoxes.getValue(it).set(true) }
    }

    private fun languageKey(language: String): String = when (language) {
        "zh_cn" -> "中文"
        else -> "English"
    }

    private fun formatNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else String.format("%.2f", value)
}
