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
import cn.enaium.allinstats.db.PlayerRow
import cn.enaium.allinstats.db.StatsDatabase
import cn.enaium.allinstats.db.WorldRow
import cn.enaium.allinstats.export.ChartImageExport
import cn.enaium.allinstats.export.CsvExport
import cn.enaium.allinstats.i18n.I18n
import cn.enaium.allinstats.model.ChartType
import cn.enaium.allinstats.model.Granularity
import cn.enaium.allinstats.model.StatGroup
import cn.enaium.allinstats.model.StatSource
import cn.enaium.allinstats.model.StatType
import cn.enaium.allinstats.query.ChartData
import cn.enaium.allinstats.query.KeyEntry
import cn.enaium.allinstats.query.StatsRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Holds the state of the interface. Everything that reads the database runs in the background and
 * the result is applied at the beginning of the next frame, so the immediate mode interface never
 * waits for a query.
 */
class StatsViewModel {
    private val queue = ConcurrentLinkedQueue<() -> Unit>()
    private val keysToken = AtomicInteger()
    private val chartToken = AtomicInteger()

    var host: StatsHost? = null

    // selection
    var worldId: String? = null
    var worldName: String = ""
    var playerUuid: String? = null
    var group: StatGroup = StatGroup.BLOCK
    val selectedKeys = LinkedHashSet<String>()
    var search: String = ""
    var sortByTotal: Boolean = true

    // chart settings
    val metrics = LinkedHashSet<StatType>()
    val sources = LinkedHashSet<StatSource>()
    var chartType: ChartType = ChartType.LINE
    var granularity: Granularity = Granularity.DAY
    var from: Long = 0L
    var to: Long = 0L

    /**
     * The counters are read again every [REFRESH_MILLIS] while the interface is open, so what was
     * just recorded shows up without reopening the screen.
     */
    var autoRefresh: Boolean = true
    private var lastRefresh = 0L

    // results
    var worlds: List<WorldRow> = emptyList()
    var players: List<PlayerRow> = emptyList()
    var keys: List<KeyEntry> = emptyList()
    var chart: ChartData? = null
    var totals: Map<Pair<StatType, StatSource>, Long> = emptyMap()
    var loading: Boolean = false
        private set
    var message: String? = null
    var ready: Boolean = false
        private set

    private val keysDirty = AtomicInteger(1)
    private val chartDirty = AtomicInteger(1)
    private var totalsDirty = true
    private var searchDirtyAt = 0L

    private val database: StatsDatabase? get() = AllInStats.db

    /**
     * Called every time the interface is opened.
     */
    fun initialize(host: StatsHost) {
        this.host = host
        val live = AllInStats.worldId
        if (live != null) {
            worldId = live
            worldName = AllInStats.worldName.ifEmpty { live }
        }
        if (metrics.isEmpty()) {
            metrics.addAll(group.types)
        }
        if (sources.isEmpty()) {
            sources.addAll(availableSources())
        }
        if (from == 0L || to == 0L) {
            preset(24L * 60 * 60 * 1000)
        }
        // what was just recorded is written before the interface reads it, otherwise the first view
        // would be up to one flush interval old
        AllInStats.flushBlocking()
        lastRefresh = System.currentTimeMillis()
        loadWorlds()
    }

    /**
     * Called on every frame.
     */
    fun update() {
        var task = queue.poll()
        while (task != null) {
            task()
            task = queue.poll()
        }
        if (!ready) {
            return
        }
        val now = System.currentTimeMillis()
        if (autoRefresh && now - lastRefresh > REFRESH_MILLIS && !loading) {
            lastRefresh = now
            reload()
        }
        if (searchDirtyAt != 0L && now - searchDirtyAt > 400L) {
            searchDirtyAt = 0L
            keysDirty.incrementAndGet()
        }
        if (keysDirty.get() > 0) {
            keysDirty.set(0)
            loadKeys()
        } else if (chartDirty.get() > 0) {
            chartDirty.set(0)
            loadChart()
        }
    }

    fun reload() {
        keysDirty.incrementAndGet()
        chartDirty.incrementAndGet()
        totalsDirty = true
    }

    fun world(worldId: String) {
        if (this.worldId == worldId) {
            return
        }
        this.worldId = worldId
        worldName = worlds.firstOrNull { it.worldId == worldId }?.name ?: worldId
        playerUuid = null
        selectedKeys.clear()
        loadPlayers()
        reload()
    }

    fun player(uuid: String?) {
        if (playerUuid == uuid) {
            return
        }
        playerUuid = uuid
        selectedKeys.clear()
        reload()
    }

    fun group(group: StatGroup) {
        if (this.group == group) {
            return
        }
        this.group = group
        selectedKeys.clear()
        metrics.clear()
        metrics.addAll(group.types)
        sources.clear()
        sources.addAll(availableSources())
        if (chartType == ChartType.CANDLE && selectedKeys.size != 1) {
            chartType = ChartType.LINE
        }
        reload()
    }

    /**
     * Adds or removes a counter from the selection. More than one counter is drawn as more than one
     * series in the same chart.
     */
    fun toggleKey(key: String) {
        if (!selectedKeys.remove(key)) {
            if (selectedKeys.size >= MAX_KEYS) {
                message = I18n.translated("ui.max_keys", MAX_KEYS)
                return
            }
            selectedKeys.add(key)
        }
        if (chartType == ChartType.CANDLE && selectedKeys.size != 1) {
            chartType = ChartType.LINE
        }
        totalsDirty = true
        chartDirty.incrementAndGet()
    }

    fun clearKeys() {
        selectedKeys.clear()
        totalsDirty = true
        chartDirty.incrementAndGet()
    }

    /**
     * Selects the counters of the current list, up to [MAX_KEYS].
     */
    fun selectAll() {
        keys.take(MAX_KEYS).forEach { selectedKeys.add(it.key) }
        if (keys.size > MAX_KEYS) {
            message = I18n.translated("ui.max_keys", MAX_KEYS)
        }
        totalsDirty = true
        chartDirty.incrementAndGet()
    }

    fun search(value: String) {
        if (search == value) {
            return
        }
        search = value
        searchDirtyAt = System.currentTimeMillis()
    }

    fun chartType(type: ChartType) {
        if (chartType == type) {
            return
        }
        if (type == ChartType.CANDLE && selectedKeys.size != 1) {
            message = I18n.translated("ui.candle_single")
            return
        }
        chartType = type
        chartDirty.incrementAndGet()
    }

    fun granularity(granularity: Granularity) {
        if (this.granularity == granularity) {
            return
        }
        this.granularity = granularity
        chartDirty.incrementAndGet()
    }

    fun metric(type: StatType, enabled: Boolean) {
        if (enabled == metrics.contains(type)) {
            return
        }
        if (enabled) metrics.add(type) else metrics.remove(type)
        if (metrics.isEmpty()) {
            metrics.add(group.type)
        }
        chartDirty.incrementAndGet()
        totalsDirty = true
    }

    fun source(source: StatSource, enabled: Boolean) {
        if (enabled == sources.contains(source)) {
            return
        }
        if (enabled) sources.add(source) else sources.remove(source)
        if (sources.isEmpty()) {
            sources.add(availableSources().first())
        }
        chartDirty.incrementAndGet()
        totalsDirty = true
    }

    /**
     * Moves the whole range, so that it can be browsed without typing.
     */
    fun shiftRange(millis: Long) {
        setRange(from + millis, to + millis)
    }

    fun setRange(from: Long, to: Long) {
        if (this.from == from && this.to == to) {
            return
        }
        this.from = from
        this.to = to
        chartDirty.incrementAndGet()
        totalsDirty = true
    }

    /**
     * Selects a range relative to now, [millis] of `0` selects everything.
     */
    fun preset(millis: Long) {
        val now = System.currentTimeMillis()
        setRange(if (millis <= 0L) 0L else now - millis, now)
        granularity = when {
            millis <= 0L -> Granularity.MONTH
            millis <= 2L * 60 * 60 * 1000 -> Granularity.MINUTE
            millis <= 48L * 60 * 60 * 1000 -> Granularity.HOUR
            millis <= 60L * 24 * 60 * 60 * 1000 -> Granularity.DAY
            else -> Granularity.MONTH
        }
    }

    fun exportCsv(from: Long, to: Long) = export("csv", from, to)

    fun exportImage(from: Long, to: Long) = export("png", from, to)

    /**
     * The sources the current group can be counted with.
     */
    fun availableSources(): List<StatSource> =
        if (group.sourceSelectable) StatSource.values().filter { it != StatSource.NONE } else group.sources

    /**
     * Whether the chart can show more than one counter, a candle is built from the minutes of a
     * single counter.
     */
    val candleAllowed: Boolean get() = selectedKeys.size == 1

    private fun loadWorlds() {
        val database = database
        if (database == null) {
            ready = true
            message = I18n.translated("ui.not_ready")
            return
        }
        AllInStats.io.execute {
            val result = runCatching { database.worldIds() }
            queue.add {
                ready = true
                result.onSuccess { all ->
                    // a world without any counter has nothing to show, only the world of the running
                    // server is always listed
                    val rows = visibleWorlds(all, AllInStats.worldId)
                    worlds = rows
                    if (rows.isEmpty()) {
                        worldId = null
                        return@onSuccess
                    }
                    // the world of the running server, otherwise the one that was played last
                    val current = rows.firstOrNull { it.worldId == worldId }
                        ?: rows.firstOrNull { it.lastBucket > 0L }
                        ?: rows.first()
                    worldId = current.worldId
                    worldName = current.name
                    loadPlayers()
                    reload()
                }.onFailure { message = it.toString() }
            }
        }
    }

    private fun loadPlayers() {
        val database = database ?: return
        val world = worldId ?: return
        AllInStats.io.execute {
            val result = runCatching { database.players(world) }
            queue.add {
                result.onSuccess { players = it }.onFailure { message = it.toString() }
            }
        }
    }

    private fun loadKeys() {
        val database = database ?: return
        val world = worldId ?: return
        val token = keysToken.incrementAndGet()
        val group = group
        val search = search
        val sortByTotal = sortByTotal
        val from = from
        val to = to
        val player = playerUuid
        val sources = sources.toList()
        loading = true
        AllInStats.io.execute {
            val result = runCatching {
                val entries = StatsRepository(database).keys(world, player, group, sources, search, from, to)
                if (sortByTotal) entries else entries.sortedBy { it.key }
            }
            queue.add {
                if (token != keysToken.get()) {
                    return@add
                }
                loading = false
                result.onSuccess { entries ->
                    keys = entries
                    // the selection is not narrowed by a search, only a new world, player or group
                    // starts a new one
                    if (selectedKeys.isEmpty()) {
                        entries.firstOrNull()?.also { selectedKeys.add(it.key) }
                        totalsDirty = true
                        chartDirty.incrementAndGet()
                    }
                }.onFailure { message = it.toString() }
            }
        }
    }

    private fun loadChart() {
        val database = database ?: return
        val world = worldId ?: return
        if (selectedKeys.isEmpty()) {
            chart = null
            return
        }
        val token = chartToken.incrementAndGet()
        val keys = selectedKeys.toList()
        val metrics = metrics.toList().ifEmpty { listOf(group.type) }
        val withTotals = totalsDirty
        totalsDirty = false
        val group = group
        val player = playerUuid
        val sources = sources.toList()
        val granularity = granularity
        val candle = chartType == ChartType.CANDLE && keys.size == 1
        // a candle is built from the minutes of the range, a longer range would read too many rows
        val to = this.to
        val from = if (candle) maxOf(this.from, to - CANDLE_RANGE) else this.from
        AllInStats.io.execute {
            val repository = StatsRepository(database)
            val result = runCatching {
                val data = repository.chart(
                    worldId = world,
                    playerUuid = player,
                    group = group,
                    keys = keys,
                    from = from,
                    to = to,
                    granularity = granularity,
                    metrics = metrics,
                    sources = sources,
                    candle = candle,
                )
                val totals = if (keys.size == 1 && withTotals) {
                    repository.totals(world, player, group, sources, keys.first(), from, to)
                } else {
                    emptyMap()
                }
                data to totals
            }
            queue.add {
                if (token != chartToken.get()) {
                    return@add
                }
                result.onSuccess { (data, total) ->
                    chart = data
                    if (keys.size == 1) {
                        totals = total
                    }
                }.onFailure { message = it.toString() }
            }
        }
    }

    private fun export(format: String, from: Long, to: Long) {
        val host = host ?: return
        val data = chart ?: return
        val file = host.exportDirectory().resolve(fileName(format)).toFile()
        val group = group
        val granularity = granularity
        val chartType = chartType
        val showKey = selectedKeys.size > 1
        val showSource = group.sourceSelectable && selectedKeys.size == 1
        val title = title(showKey)
        AllInStats.exporter.execute {
            val result = runCatching {
                file.parentFile?.mkdirs()
                when (format) {
                    "csv" -> CsvExport.write(file, data, showKey, showSource)
                    else -> ChartImageExport.write(file, data, chartType, title, from, to, showKey, showSource)
                }
            }
            queue.add {
                message = result.fold(
                    { I18n.translated("ui.exported", file.absolutePath) },
                    { I18n.translated("ui.export_failed", it.message ?: it.toString()) },
                )
            }
        }
    }

    /**
     * The name of the chart, it names the shown counters.
     */
    fun title(showKey: Boolean = selectedKeys.size > 1): String {
        val keys = selectedKeys.toList()
        return when {
            keys.isEmpty() -> I18n.translated("category.${group.id}")
            keys.size == 1 -> "${StatNames.display(group, keys.first())} · ${I18n.translated("category.${group.id}")}"
            keys.size <= 3 -> keys.joinToString(" · ") { StatNames.display(group, it) }
            else -> I18n.translated("category.${group.id}") + " (" + I18n.translated("ui.count", keys.size) + ")"
        }
    }

    private fun fileName(format: String): String {
        val keys = selectedKeys.toList()
        val name = if (keys.size == 1) {
            "allinstats-${group.id}-${keys.first().substringAfter(':')}-${granularity.id}"
        } else {
            "allinstats-${group.id}-${keys.size}-counters-${granularity.id}"
        }
        return name.replace(Regex("[^A-Za-z0-9_.-]"), "_") + "." + format
    }

    companion object {
        /**
         * The worlds the interface offers: the ones that hold counters and the world of the running
         * server (a world that was just created has no counters yet).
         */
        fun visibleWorlds(worlds: List<WorldRow>, live: String?): List<WorldRow> =
            worlds.filter { it.lastBucket > 0L || it.worldId == live }

        /**
         * The longest range a candle chart is built from, it reads the minutes of the range.
         */
        const val CANDLE_RANGE = 120L * 24 * 60 * 60 * 1000

        /**
         * The most counters that are drawn in one chart.
         */
        const val MAX_KEYS = 8

        /**
         * How often the interface reads the counters again.
         */
        const val REFRESH_MILLIS = 2_000L

        private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        fun format(time: Long): String =
            LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault()).format(timeFormat)

        fun parse(text: String): Long? = try {
            LocalDateTime.parse(text.trim().replace('T', ' '), timeFormat)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (_: Throwable) {
            null
        }
    }
}
