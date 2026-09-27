/*
 * Copyright 2026 Enaium
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.enaium.allinstats

import cn.enaium.allinstats.gui.StatNames
import cn.enaium.allinstats.gui.Translator
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.StatsScreen
import net.minecraft.client.resource.language.I18n
import java.nio.file.Path

/**
 * The parts of the game that only exist on the client, the interface is drawn there.
 *
 * @author Enaium
 */
object ClientEnvironment {
    val gameDir: Path get() = MinecraftClient.getInstance().runDirectory.toPath()

    val language: String get() = MinecraftClient.getInstance().options.language

    fun translate(key: String): String = I18n.translate(key)

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
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        client.setScreen(StatsScreen(client.currentScreen, player.statHandler))
    }
}
