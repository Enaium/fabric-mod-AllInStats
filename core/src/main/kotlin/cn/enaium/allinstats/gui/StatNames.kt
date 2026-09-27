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

import cn.enaium.allinstats.model.StatGroup
import cn.enaium.allinstats.model.StatSource
import cn.enaium.allinstats.model.StatType
import cn.enaium.allinstats.query.ChartSeries

/**
 * Translates a Minecraft translation key, provided by the game side because the language files of
 * the game are only reachable from there.
 */
fun interface Translator {
    fun translate(key: String): String
}

/**
 * Resolves the readable name of a counter key (`minecraft:stone`) with the language of the game.
 *
 * @author Enaium
 */
object StatNames {
    @Volatile
    var translator: Translator? = null

    fun display(group: StatGroup, key: String): String {
        val translated = translationKey(group, key)?.let { translator?.translate(it) }
        return if (translated == null || translated == translationKey(group, key)) key else translated
    }

    /**
     * The counter key of a group is the registry id of the object it belongs to, so the translation
     * key of that object is enough to show it in the language of the game.
     */
    private fun translationKey(group: StatGroup, key: String): String? {
        val path = key.substringAfter(':')
        if (path.isEmpty()) {
            return null
        }
        return when (group) {
            StatGroup.BLOCK -> "block.minecraft.$path"
            StatGroup.ENTITY -> "entity.minecraft.$path"
            StatGroup.ITEM, StatGroup.TOOL, StatGroup.FISHING -> "item.minecraft.$path"
        }
    }

    /**
     * The label of a series. The counter and the source are only added when the chart shows more
     * than one of them.
     */
    fun seriesLabel(series: ChartSeries, group: StatGroup, showSource: Boolean, showKey: Boolean): String {
        val parts = ArrayList<String>(3)
        if (showKey) {
            parts.add(display(group, series.key))
        }
        parts.add(cn.enaium.allinstats.i18n.I18n.translated("type.${series.type.id}"))
        if (showSource && series.source != StatSource.NONE) {
            parts.add(cn.enaium.allinstats.i18n.I18n.translated("source.${series.source.id}"))
        }
        return parts.joinToString(" · ")
    }


}
