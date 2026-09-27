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
import cn.enaium.allinstats.db.CounterRow
import cn.enaium.allinstats.model.ChartType
import imgui.ImGui
import imgui.ImGuiIO
import imgui.extension.implot.ImPlot
import imgui.gl3.ImGuiImplGl3
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.math.sin
import kotlin.system.exitProcess

/**
 * Renders the interface of the mod without Minecraft and writes screenshots, so the layout, the
 * fonts and the plot can be checked without starting the game.
 *
 * ```
 * ./gradlew :core:screenshot
 * ```
 */
fun main() {
    val output = File("build/reports/allinstats")
    output.mkdirs()

    val directory = Files.createTempDirectory("allinstats").toFile()
    check(AllInStats.initialize(directory.toPath())) { "the database cannot be opened" }
    seed(AllInStats.db!!)

    val width = 1600
    val height = 900
    if (!GLFW.glfwInit()) {
        error("GLFW cannot be initialized")
    }
    GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE)
    GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3)
    GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2)
    GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE)
    GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE)
    val window = GLFW.glfwCreateWindow(width, height, "AllInStats", 0L, 0L)
    check(window != 0L) { "the window cannot be created" }
    GLFW.glfwMakeContextCurrent(window)
    GL.createCapabilities()

    ImGui.createContext()
    ImPlot.createContext()
    val renderer = ImGuiImplGl3()
    renderer.init("#version 150")
    StatsFonts.install(ImGui.getIO())

    val host = object : StatsHost {
        override fun openVanillaStatistics() = println("vanilla statistics opened")
        override fun close() = println("closed")
        override fun exportDirectory() = output.toPath()
    }
    println("worlds in the database: " + AllInStats.db!!.worldIds())
    println("players in the database: " + AllInStats.db!!.players("New World"))
    StatsGui.open(host)

    val io = ImGui.getIO()
    // the harness must not write a layout file into the repository
    io.setIniFilename(null)

    fun frame(): BufferedImage {
        io.setDisplaySize(width.toFloat(), height.toFloat())
        io.deltaTime = 1f / 60f
        renderer.newFrame()
        ImGui.newFrame()
        StatsGui.render(io)
        ImGui.render()
        renderer.renderDrawData(ImGui.getDrawData())
        val pixels = ByteBuffer.allocateDirect(width * height * 4)
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = ((height - 1 - y) * width + x) * 4
                val r = pixels.get(index).toInt() and 0xFF
                val g = pixels.get(index + 1).toInt() and 0xFF
                val b = pixels.get(index + 2).toInt() and 0xFF
                image.setRGB(x, y, (r shl 16) or (g shl 8) or b)
            }
        }
        return image
    }

    fun settle(frames: Int = 20) {
        repeat(frames) { frame() }
    }

    // typing into the fields of the interface, exactly as the game would deliver it
    run {
        settle(5)
        val before = StatsGui.fromField()
        io.addMousePosEvent(510f, 110f)
        settle(2)
        io.addMouseButtonEvent(0, true)
        settle(1)
        io.addMouseButtonEvent(0, false)
        settle(2)
        println("from field active: " + ImGui.isAnyItemActive() + " text='" + StatsGui.fromField() + "'")
        io.addInputCharacter('9'.code)
        settle(2)
        val typed = StatsGui.fromField()
        io.addKeyEvent(imgui.flag.ImGuiKey.Backspace, true)
        settle(1)
        io.addKeyEvent(imgui.flag.ImGuiKey.Backspace, false)
        settle(2)
        println("text field: before='$before' typed='$typed' erased='${StatsGui.fromField()}'")
        // and the search field
        io.addMousePosEvent(150f, 320f)
        settle(2)
        io.addMouseButtonEvent(0, true)
        settle(1)
        io.addMouseButtonEvent(0, false)
        settle(2)
        io.addInputCharacter('s'.code)
        settle(2)
        println("search field: '${StatsGui.searchField()}'")
        // the search is only there to prove that typing works
        view().search("")
        settle(3)
    }

    // an empty range must not move the window to the epoch, the next data has to be visible
    run {
        view().setRange(0L, 60_000L)
        settle(6)
        println("window while the range is empty: ${StatsGui.window.from}..${StatsGui.window.to} valid=${StatsGui.window.valid}")
        view().preset(24L * 60 * 60 * 1000)
        settle(6)
        val chart = view().chart
        println(
            "window after the data came back: ${StatsGui.window.from}..${StatsGui.window.to} " +
                "data=${chart?.dataFrom}..${chart?.dataTo}"
        )
    }

    // the font of the interface is pushed around its windows, the default font of the context (used
    // by every other window of the game) stays untouched
    run {
        settle(3)
        val defaultFont = ImGui.getIO().fontDefault
        println(
            "font of the context: default=${defaultFont.ptr} interface=${StatsFonts.fontPointer()} " +
                "current outside of the interface=${ImGui.getFont().ptr}"
        )
    }

    // switching the range and the category back and forth
    run {
        fun state(label: String) {
            val v = view()
            println(
                "[range] $label: range=${v.from}..${v.to} granularity=${v.granularity} group=${v.group} " +
                    "keys=${v.keys.size} selected=${v.selectedKeys.size} chart=${v.chart?.let { c -> "${c.times.size} buckets, ${c.series.size} series" }} " +
                    "window=${StatsGui.window.from}..${StatsGui.window.to} valid=${StatsGui.window.valid}"
            )
        }
        view().apply {
            group(cn.enaium.allinstats.model.StatGroup.BLOCK)
            preset(24L * 60 * 60 * 1000)
        }
        settle(6)
        state("last day")
        view().preset(60L * 60 * 1000)
        settle(6)
        state("last hour")
        view().preset(0L)
        settle(6)
        state("all after last hour")
        view().group(cn.enaium.allinstats.model.StatGroup.ITEM)
        settle(6)
        state("other category")
        view().preset(60L * 60 * 1000)
        settle(6)
        state("last hour again")
        view().preset(0L)
        settle(6)
        state("all again")
        view().apply {
            group(cn.enaium.allinstats.model.StatGroup.BLOCK)
            preset(7L * 24 * 60 * 60 * 1000)
        }
        settle(6)
        state("back to blocks, last 7 days")
    }

    var screenshot = 1
    fun capture(name: String) {
        settle()
        val v = view()
        println(
            "state: worlds=${v.worlds.map { it.worldId }} world=${v.worldId} keys=${v.selectedKeys} metrics=${v.metrics} from=${v.from} to=${v.to} " +
                "entries=${v.keys.size} chart=${v.chart?.let { c -> "${c.times.size} buckets, " + c.series.map { it.total } }} " +
                "window=${cn.enaium.allinstats.gui.StatsGui.window.from}..${cn.enaium.allinstats.gui.StatsGui.window.to}"
        )
        val file = File(output, "%02d-$name.png".format(screenshot++))
        ImageIO.write(frame(), "png", file)
        println("written ${file.absolutePath}")
    }

    capture("line-blocks")

    // the metrics of the item category, including the sources
    view().apply {
        group(cn.enaium.allinstats.model.StatGroup.ITEM)
        preset(7L * 24 * 60 * 60 * 1000)
        chartType(ChartType.BAR)
    }
    capture("bar-items")

    view().apply {
        chartType(ChartType.CANDLE)
        granularity(cn.enaium.allinstats.model.Granularity.DAY)
    }
    capture("candle-items")

    view().apply {
        group(cn.enaium.allinstats.model.StatGroup.FISHING)
        chartType(ChartType.AREA)
    }
    capture("area-fishing")

    view().apply {
        group(cn.enaium.allinstats.model.StatGroup.ENTITY)
        chartType(ChartType.SCATTER)
    }
    capture("scatter-entities")

    // several counters in the same chart
    view().apply {
        group(cn.enaium.allinstats.model.StatGroup.BLOCK)
        preset(7L * 24 * 60 * 60 * 1000)
        clearKeys()
        toggleKey("minecraft:stone")
        toggleKey("minecraft:dirt")
        toggleKey("minecraft:oak_log")
        chartType(ChartType.LINE)
    }
    capture("multi-blocks-line")
    // the export of a chart with several counters
    view().exportCsv(StatsGui.window.from, StatsGui.window.to)
    view().exportImage(StatsGui.window.from, StatsGui.window.to)

    // the wheel zooms the time axis, which has to stay labelled with dates
    run {
        val plotCenterX = 900f
        val plotCenterY = 400f
        io.addMousePosEvent(plotCenterX, plotCenterY)
        settle(3)
        println("window before the wheel: ${StatsGui.window.from}..${StatsGui.window.to}")
        io.addMouseWheelEvent(0f, 1f)
        settle(3)
        println("window after the wheel: ${StatsGui.window.from}..${StatsGui.window.to}")
    }
    capture("zoomed-blocks")

    // one bucket only (a month) has to be visible as well
    view().apply {
        group(cn.enaium.allinstats.model.StatGroup.BLOCK)
        preset(0L)
    }
    capture("month-single-bucket")

    // a range without data has to say where the newest data is
    view().setRange(0L, 86_400_000L)
    capture("empty-range")

    // the exact report: an empty range, then another category - the controls have to stay
    view().apply {
        group(cn.enaium.allinstats.model.StatGroup.ITEM)
        preset(60L * 60 * 1000)
        setRange(0L, 86_400_000L)
    }
    settle(6)
    view().group(cn.enaium.allinstats.model.StatGroup.ENTITY)
    capture("empty-range-other-category")

    // the interface in Chinese, the glyphs of the font have to be there for it
    cn.enaium.allinstats.i18n.I18n.setLanguage("zh_cn")
    view().apply {
        group(cn.enaium.allinstats.model.StatGroup.TOOL)
        preset(7L * 24 * 60 * 60 * 1000)
        clearKeys()
        chartType(ChartType.LINE)
    }
    settle(3)
    view().apply {
        toggleKey("minecraft:iron_pickaxe")
        chartType(ChartType.CANDLE)
    }
    capture("chinese-candle-tools")

    view().exportCsv(StatsGui.window.from, StatsGui.window.to)
    view().exportImage(StatsGui.window.from, StatsGui.window.to)
    var waited = 0
    while (view().message?.endsWith(".png") != true && waited < 200) {
        frame()
        waited++
    }
    println("message after the export: " + view().message + " (after $waited frames)")
    println("exports: " + output.listFiles()?.map { it.name }.orEmpty())

    renderer.shutdown()
    GLFW.glfwDestroyWindow(window)
    GLFW.glfwTerminate()
    AllInStats.flushNow()
    exitProcess(0)
}

private fun view(): StatsViewModel = StatsGui.view

private fun seed(database: cn.enaium.allinstats.db.StatsDatabase) {
    AllInStats.world("New World", "New World")
    AllInStats.player("0c375742-c902-3e90-aa08-6f9c607b7881", "Enaium")
    Thread.sleep(200)

    val rows = ArrayList<CounterRow>()
    val now = System.currentTimeMillis()
    val day = 24 * 60 * 60 * 1000L
    val minute = 60_000L
    val patterns = listOf(
        Triple("block_broken", "minecraft:stone", ""),
        Triple("block_broken", "minecraft:dirt", ""),
        Triple("block_broken", "minecraft:oak_log", ""),
        Triple("block_placed", "minecraft:stone", ""),
        Triple("item_acquired", "minecraft:cobblestone", "pickup"),
        Triple("item_acquired", "minecraft:cobblestone", "craft"),
        Triple("item_dropped", "minecraft:dirt", ""),
        Triple("item_acquired", "minecraft:cod", "fishing"),
        Triple("entity_killed", "minecraft:zombie", ""),
        Triple("entity_killed", "minecraft:creeper", ""),
        Triple("tool_used", "minecraft:iron_pickaxe", ""),
        Triple("tool_broken", "minecraft:iron_pickaxe", ""),
    )
    var index = 0
    for (dayOffset in 0 until 7) {
        for (minutes in 0 until 24 * 60 step 7) {
            val time = now - (6 - dayOffset) * day + minutes * minute
            val bucket = time / minute * minute
            patterns.forEachIndexed { patternIndex, (type, key, source) ->
                val wave = (sin((minutes + patternIndex * 37) / 90.0) + 1.6) * 4
                val amount = (wave * (1 + patternIndex % 3)).toLong()
                if (amount <= 0) {
                    return@forEachIndexed
                }
                index++
                rows.add(
                    CounterRow(
                        worldId = "New World",
                        playerUuid = if (index % 5 == 0) "11111111-1111-1111-1111-111111111111" else
                            "0c375742-c902-3e90-aa08-6f9c607b7881",
                        type = type,
                        statKey = key,
                        source = source,
                        bucket = bucket,
                        amount = amount,
                    )
                )
            }
        }
    }
    database.addCounters(rows)
    AllInStats.db!!.ensurePlayer("New World", "11111111-1111-1111-1111-111111111111", "Alex")
    // a world that was opened but never played must not show up in the world list
    AllInStats.db!!.ensureWorld("Empty World", "Empty World")
    println("seeded ${rows.size} counters")
}
