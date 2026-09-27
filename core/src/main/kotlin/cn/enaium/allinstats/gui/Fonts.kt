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

import imgui.ImFont
import imgui.ImFontAtlas
import imgui.ImFontConfig
import imgui.ImFontGlyphRangesBuilder
import imgui.ImGui
import imgui.ImGuiIO
import java.io.File

/**
 * The default ImGui font has no glyphs outside of latin-1, so a font of the system that can render
 * the selected language is added to the atlas.
 *
 * The font of the interface is pushed around the windows of this mod ([push] and [pop]) instead of
 * replacing the default font of the context, otherwise every other window of the game would use it
 * as well.
 *
 * @author Enaium
 */
object StatsFonts {
    private const val FONT_SIZE = 17f

    /**
     * The configurations are kept alive, a temporary one can be collected while ImGui is still
     * reading it.
     */
    private val fontConfig = ImFontConfig()

    /**
     * The font of the interface, `null` while no font of the system could be used.
     */
    private var font: ImFont? = null

    private var size: Float = FONT_SIZE

    fun install(io: ImGuiIO) {
        val atlas = io.fonts
        val ranges = glyphRanges(atlas)
        // the default font stays the default of the context, the interface pushes its own font
        atlas.addFontDefault()
        val system = findFont()
        if (system == null) {
            System.err.println("AllInStats: no system font was found, the interface uses the default font")
        } else {
            try {
                fontConfig.fontNo = system.faceIndex
                font = atlas.addFontFromFileTTF(system.file.absolutePath, FONT_SIZE, fontConfig, ranges)
                size = FONT_SIZE
                println("AllInStats: the interface uses ${system.file} (${system.family})")
            } catch (error: Throwable) {
                System.err.println("AllInStats: unable to load ${system.file}: $error")
                font = null
            }
        }
        if (!atlas.build()) {
            System.err.println("AllInStats: unable to build the font atlas")
        }
    }

    /**
     * Uses the font of the interface for the windows that are drawn until [pop].
     */
    fun push() {
        font?.also { ImGui.pushFont(it, size) }
    }

    fun pop() {
        if (font != null) {
            ImGui.popFont()
        }
    }

    /**
     * The font of the interface, only used to check that it is not the default font of the context.
     */
    internal fun fontPointer(): Long = font?.ptr ?: 0L

    private fun glyphRanges(atlas: ImFontAtlas): ShortArray {
        val builder = ImFontGlyphRangesBuilder()
        builder.addRanges(atlas.glyphRangesDefault)
        // the interface is translated, every language that is shipped has to be renderable
        builder.addRanges(atlas.glyphRangesChineseSimplifiedCommon)
        builder.addText("…·→←↑↓±°×÷≈≤≥■□●○◆◇")
        return builder.buildRanges()
    }

    /**
     * The names are ordered: a font that can render Chinese is preferred because it usually covers
     * latin as well, the latin only fonts are the fallback.
     */
    private fun findFont(): SystemFontFinder.FontFile? {
        candidates.forEach { family ->
            SystemFontFinder.findFontFiles(family).forEach { font ->
                if (SystemFontFinder.supports(font, PROBE)) {
                    return font
                }
            }
        }
        return null
    }

    /**
     * Simplified characters are used, so a traditional only font is skipped.
     */
    private const val PROBE = "中发门见统计方块AllInStats"

    private val candidates = listOf(
        "PingFang SC",
        "PingFang",
        "Hiragino Sans GB",
        "STHeiti",
        "Heiti SC",
        "Songti SC",
        "Songti",
        "Arial Unicode",
        "Microsoft YaHei",
        "msyh",
        "SimSun",
        "simsun",
        "SimHei",
        "Noto Sans CJK SC",
        "Noto Sans SC",
        "Source Han Sans",
        "sourcehan",
        "Segoe UI",
        "Helvetica Neue",
        "Arial",
        "DejaVu Sans",
        "Liberation Sans",
    )
}

/**
 * Finds the files of a font family of the operating system.
 *
 * @author Enaium
 */
object SystemFontFinder {
    private val extensions = listOf("ttf", "ttc", "otf", "otc")

    private val cache = HashMap<String, List<FontFile>>()

    /**
     * A font file and the face to use inside it.
     *
     * @author Enaium
     */
    data class FontFile(val file: File, val faceIndex: Int, val family: String?)

    /**
     * Every font of the system that matches [fontName], in the order of the font directories.
     */
    fun findFontFiles(fontName: String): List<FontFile> {
        cache[fontName]?.let { return it }
        val result = ArrayList<FontFile>()
        fontDirs().forEach { collect(it, fontName, result) }
        cache[fontName] = result
        return result
    }

    /**
     * Whether the face really contains every character of [text], so a font that cannot render the
     * text is never used.
     */
    fun supports(fontFile: FontFile, text: String): Boolean = try {
        val data = FontData(fontFile.file.readBytes())
        val offset = data.faceOffset(fontFile.faceIndex)
        text.all { data.hasGlyph(offset, it.code) }
    } catch (_: Throwable) {
        false
    }

    private fun collect(dir: File, targetName: String, result: MutableList<FontFile>) {
        if (!dir.isDirectory) {
            return
        }
        dir.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { file ->
            if (file.isDirectory) {
                collect(file, targetName, result)
            } else if (file.extension.lowercase() in extensions && matches(file.name, targetName)) {
                face(file, targetName)?.also { result += it }
            }
        }
    }

    /**
     * The face of [file] that matches [targetName], or the first one, several faces share a file in
     * a collection.
     */
    private fun face(file: File, targetName: String): FontFile? = try {
        val data = FontData(file.readBytes())
        val offsets = data.faceOffsets()
        val index = offsets.indices.firstOrNull { matches(data.faceName(offsets[it]) ?: "", targetName) } ?: 0
        FontFile(file, index, data.faceName(offsets[index]))
    } catch (_: Throwable) {
        null
    }

    private fun matches(a: String, b: String): Boolean {
        val left = normalize(a)
        val right = normalize(b)
        return left == right || left.contains(right) || right.contains(left)
    }

    private fun normalize(name: String): String =
        name.lowercase().replace(" ", "").replace("-", "").replace("_", "")

    private fun fontDirs(): List<File> {
        val os = System.getProperty("os.name").lowercase()
        val home = System.getProperty("user.home")
        return when {
            os.contains("win") -> listOf(
                File("C:\\Windows\\Fonts"),
                File(home, "AppData/Local/Microsoft/Windows/Fonts"),
            )

            os.contains("mac") -> listOf(
                File("/System/Library/Fonts"),
                File("/System/Library/Fonts/Supplemental"),
                File("/Library/Fonts"),
                File(home, "Library/Fonts"),
            )

            else -> listOf(
                File("/usr/share/fonts"),
                File("/usr/local/share/fonts"),
                File(home, ".fonts"),
                File(home, ".local/share/fonts"),
            )
        }
    }

    /**
     * Reads the tables of a font file, both single fonts and collections are supported.
     *
     * @author Enaium
     */
    private class FontData(private val data: ByteArray) {
        fun faceOffsets(): IntArray {
            if (data.size < 12 || tag(0) != "ttcf") {
                return intArrayOf(0)
            }
            val count = u32(8).coerceIn(1, 64)
            return IntArray(count) { u32(12 + it * 4) }.filter { it in 0 until data.size }.toIntArray()
        }

        fun faceOffset(index: Int): Int {
            val offsets = faceOffsets()
            return offsets.getOrElse(index) { offsets.firstOrNull() ?: 0 }
        }

        fun faceName(offset: Int): String? {
            val name = table(offset, "name") ?: return null
            val count = u16(name + 2)
            val storage = name + u16(name + 4)
            var family: String? = null
            for (i in 0 until count) {
                val record = name + 6 + i * 12
                val platform = u16(record)
                val nameId = u16(record + 6)
                val text = readName(storage + u16(record + 10), u16(record + 8), platform) ?: continue
                when (nameId) {
                    // the full name of the face is the most specific one
                    4 -> return text
                    1 -> family = family ?: text
                }
            }
            return family
        }

        fun hasGlyph(offset: Int, codepoint: Int): Boolean {
            val cmap = table(offset, "cmap") ?: return false
            val count = u16(cmap + 2)
            for (i in 0 until count) {
                val subtable = cmap + u32(cmap + 4 + i * 8 + 4)
                when (u16(subtable)) {
                    4 -> if (hasGlyphFormat4(subtable, codepoint)) return true
                    12 -> if (hasGlyphFormat12(subtable, codepoint)) return true
                }
            }
            return false
        }

        private fun hasGlyphFormat4(subtable: Int, codepoint: Int): Boolean {
            if (codepoint > 0xFFFF) {
                return false
            }
            val segments = u16(subtable + 6) / 2
            val end = subtable + 14
            val start = end + segments * 2 + 2
            val delta = start + segments * 2
            val range = delta + segments * 2
            for (i in 0 until segments) {
                if (codepoint > u16(end + i * 2) || codepoint < u16(start + i * 2)) {
                    continue
                }
                val idDelta = u16(delta + i * 2)
                val idRangeOffset = u16(range + i * 2)
                if (idRangeOffset == 0) {
                    return (codepoint + idDelta) and 0xFFFF != 0
                }
                val glyph = u16(range + i * 2 + idRangeOffset + (codepoint - u16(start + i * 2)) * 2)
                return glyph != 0 && (glyph + idDelta) and 0xFFFF != 0
            }
            return false
        }

        private fun hasGlyphFormat12(subtable: Int, codepoint: Int): Boolean {
            val groups = u32(subtable + 12)
            var low = 0
            var high = groups - 1
            while (low <= high) {
                val middle = (low + high) / 2
                val group = subtable + 16 + middle * 12
                when {
                    codepoint < u32(group) -> high = middle - 1
                    codepoint > u32(group + 4) -> low = middle + 1
                    else -> return u32(group + 8) + (codepoint - u32(group)) != 0
                }
            }
            return false
        }

        private fun table(offset: Int, tag: String): Int? {
            val tables = u16(offset + 4)
            for (i in 0 until tables) {
                val record = offset + 12 + i * 16
                if (tag(record) == tag) {
                    return u32(record + 8)
                }
            }
            return null
        }

        private fun readName(offset: Int, length: Int, platform: Int): String? {
            if (offset < 0 || length <= 0 || offset + length > data.size) {
                return null
            }
            val text = if (platform == 1) {
                String(data, offset, length, Charsets.ISO_8859_1)
            } else {
                String(data, offset, length, Charsets.UTF_16BE)
            }
            return text.replace("\u0000", "").trim().ifEmpty { null }
        }

        private fun tag(offset: Int): String = String(data, offset, 4, Charsets.ISO_8859_1)

        private fun u16(offset: Int): Int =
            if (offset < 0 || offset + 2 > data.size) {
                0
            } else {
                ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
            }

        private fun u32(offset: Int): Int =
            if (offset < 0 || offset + 4 > data.size) {
                0
            } else {
                (u16(offset) shl 16) or u16(offset + 2)
            }
    }
}
