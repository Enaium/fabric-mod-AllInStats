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

package cn.enaium.allinstats.i18n

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * The interface of this mod is drawn by ImGui, which does not know anything about Minecraft
 * translations, so the strings are kept next to the mod and switched manually.
 */
object I18n {
    val languages = listOf("zh_cn", "en_us")

    @Volatile
    private var code: String = "en_us"

    @Volatile
    private var strings: Map<String, String> = load("en_us")

    val language: String get() = code

    fun setLanguage(code: String) {
        this.code = code
        strings = load(code)
    }

    fun translated(key: String): String = strings[key] ?: key

    fun translated(key: String, vararg args: Any?): String {
        val format = translated(key)
        return if (args.isEmpty()) format else String.format(format, *args)
    }

    /**
     * Translation of the given key in every shipped language, used to display a readable name that
     * does not depend on the selected language (a world name for example).
     */
    private fun load(code: String): Map<String, String> {
        val stream = I18n::class.java.getResourceAsStream("/lang/$code.json")
            ?: return if (code == "en_us") emptyMap() else load("en_us")
        return stream.use { jacksonObjectMapper().readValue(it) }
    }
}
