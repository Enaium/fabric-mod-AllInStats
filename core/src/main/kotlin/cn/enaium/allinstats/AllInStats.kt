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

import cn.enaium.allinstats.db.BucketKey
import cn.enaium.allinstats.db.StatsDatabase
import cn.enaium.allinstats.db.StatsRecorder
import cn.enaium.allinstats.i18n.I18n
import cn.enaium.allinstats.model.StatSource
import cn.enaium.allinstats.model.StatType
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The entry point of the statistics. The game side (every Minecraft version has its own module)
 * reports the plain events to this object and everything else - storage, aggregation and the
 * interface - is version independent.
 *
 * @author Enaium
 */
object AllInStats {
    const val MOD_ID = "allinstats"

    private val recorder = StatsRecorder()
    private val opened = AtomicBoolean(false)

    /**
     * Players that are already stored in the database, they are registered once per world.
     */
    private val knownPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var database: StatsDatabase? = null

    /**
     * The world the running server belongs to, `null` while no world is loaded.
     */
    @Volatile
    var worldId: String? = null
        private set

    @Volatile
    var worldName: String = ""
        private set

    /**
     * Database work is executed one statement at a time, off the game thread.
     */
    lateinit var io: ExecutorService
        private set

    /**
     * Exports are written on their own thread, drawing an image can never hold back the interface.
     */
    lateinit var exporter: ExecutorService
        private set

    /**
     * Why the database could not be opened, shown by the interface.
     */
    @Volatile
    var openError: String? = null
        private set

    val db: StatsDatabase? get() = database

    val isReady: Boolean get() = database != null

    /**
     * Opens `<game directory>/allinstats/stats.mv.db`. Called by the game side before the first
     * counter is written or the interface is opened; calling it more than once does nothing.
     */
    @JvmStatic
    fun initialize(gameDir: Path): Boolean {
        if (!opened.compareAndSet(false, true)) {
            return true
        }
        // the interface can export the chart as an image; on macOS the toolkit of the image has to
        // be switched to the headless mode before anything touches it, the game owns the main
        // thread there and the toolkit would wait for it forever
        if (System.getProperty("os.name").lowercase().contains("mac")) {
            System.setProperty("java.awt.headless", "true")
        }
        return try {
            val directory = gameDir.resolve(MOD_ID)
            Files.createDirectories(directory)
            database = StatsDatabase("jdbc:h2:file:${directory.resolve("stats").toAbsolutePath()}")
            io = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "AllInStats IO").apply { isDaemon = true }
            }
            exporter = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "AllInStats Export").apply { isDaemon = true }
            }
            Runtime.getRuntime().addShutdownHook(Thread { shutdown() })
            openError = null
            true
        } catch (error: Throwable) {
            opened.set(false)
            openError = error.message ?: error.toString()
            System.err.println("[AllInStats] cannot open the statistics database: $error")
            val java = System.getProperty("java.specification.version") ?: ""
            if (java.startsWith("1.") || java.toIntOrNull()?.let { it < 11 } == true) {
                System.err.println(
                    "[AllInStats] the H2 database of fabric-database-h2 needs Java 11 or newer, " +
                        "this game runs on Java $java"
                )
            }
            false
        }
    }

    /**
     * Called when a world (save) starts.
     */
    @JvmStatic
    fun world(id: String, name: String) {
        if (worldId != id) {
            knownPlayers.clear()
        }
        worldId = id
        worldName = name
        if (database != null) {
            io.execute { database?.ensureWorld(id, name) }
        }
    }

    /**
     * Called when a player is known to the running server.
     */
    @JvmStatic
    fun player(uuid: String, name: String) {
        val world = worldId ?: return
        if (database == null) {
            return
        }
        val key = "$world/$uuid"
        if (!knownPlayers.add(key)) {
            return
        }
        io.execute { database?.ensurePlayer(world, uuid, name) }
    }

    /**
     * Adds one (or more) event to the counter of the current minute.
     */
    @JvmStatic
    @JvmOverloads
    fun record(
        type: StatType,
        key: String,
        source: StatSource = StatSource.NONE,
        amount: Long = 1L,
        playerUuid: String = "",
    ) {
        val world = worldId ?: return
        if (database == null) {
            return
        }
        recorder.record(
            BucketKey(
                worldId = world,
                playerUuid = playerUuid,
                type = type.id,
                statKey = key,
                source = source.id,
                bucket = 0L,
            ),
            amount,
            System.currentTimeMillis(),
        )
    }

    /**
     * Called on every server tick; the passed minutes are written in the background.
     */
    @JvmStatic
    fun tick() {
        flush(false)
    }

    private fun flush(force: Boolean) {
        val rows = recorder.flush(System.currentTimeMillis(), force)
        if (rows.isEmpty()) {
            return
        }
        val db = database ?: return
        io.execute {
            // a failed write keeps the counters, they are written again with the next flush
            runCatching { db.addCounters(rows) }
                .onSuccess { recorder.written(rows) }
                .onFailure { System.err.println("[AllInStats] cannot write the counters: $it") }
        }
    }

    /**
     * Number of counters that are still in memory.
     */
    @JvmStatic
    fun pending(): Int = recorder.pending()

    /**
     * Writes everything that is still in memory, used when the world stops.
     */
    @JvmStatic
    fun flushNow() {
        flush(true)
    }

    /**
     * Writes the counters that are still in memory on the calling thread, so that a reader (the
     * interface for example) sees them right away. Only a few rows are written at a time.
     */
    @JvmStatic
    fun flushBlocking() {
        val database = database ?: return
        val rows = recorder.flush(System.currentTimeMillis(), true)
        if (rows.isEmpty()) {
            return
        }
        runCatching { database.addCounters(rows) }
            .onSuccess { recorder.written(rows) }
            .onFailure { System.err.println("[AllInStats] cannot write the counters: $it") }
    }

    /**
     * The last chance to write the counters, the game is going down and there is no time left for
     * the background thread.
     */
    private fun shutdown() {
        val rows = recorder.flush(System.currentTimeMillis(), true)
        val database = database ?: return
        if (rows.isNotEmpty()) {
            runCatching { database.addCounters(rows) }
                .onFailure { System.err.println("[AllInStats] cannot write the counters: $it") }
        }
        recorder.written(rows)
        io.shutdown()
        io.awaitTermination(10, TimeUnit.SECONDS)
        exporter.shutdown()
        exporter.awaitTermination(10, TimeUnit.SECONDS)
    }

    /**
     * The language of the interface, set by the game side from the selected Minecraft language.
     */
    @JvmStatic
    fun language(code: String) {
        if (I18n.languages.contains(code)) {
            I18n.setLanguage(code)
        } else {
            I18n.setLanguage(if (code.startsWith("zh")) "zh_cn" else "en_us")
        }
    }
}
