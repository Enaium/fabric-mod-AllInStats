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

import cn.enaium.allinstats.gui.StatNames
import cn.enaium.allinstats.gui.Translator
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.achievement.StatsScreen
import net.minecraft.network.chat.Component
import java.nio.file.Path

/**
 * The parts of the game that only exist on the client, the interface is drawn there.
 *
 * @author Enaium
 */
object ClientEnvironment {
    val gameDir: Path get() = Minecraft.getInstance().gameDirectory.toPath()

    val language: String get() = Minecraft.getInstance().options.languageCode

    fun translate(key: String): String = Component.translatable(key).string

    /**
     * Called before the interface of this mod is opened.
     */
    fun prepare() {
        AllInStats.initialize(gameDir)
        AllInStats.language(language)
        StatNames.translator = StatNames.translator ?: Translator { translate(it) }
    }

    /**
     * Opens the statistics screen of the game. It is opened on top of the interface of this mod, so
     * that it returns here when it is closed.
     */
    fun openVanillaStatistics() {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val screen = client.gui.screen() ?: return
        client.setScreenAndShow(StatsScreen(screen, player.stats))
    }
}
