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

import cn.enaium.allinstats.gui.StatsGui
import cn.enaium.allinstats.gui.StatsHost
import cn.enaium.fabric.imgui.ImGuiRenderable
import imgui.ImGuiIO
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import java.nio.file.Path

/**
 * The screen that shows the interface of this mod. The interface itself is drawn by the ImGui
 * module of this game, only the button that returns to the game is handled here. The screens of this
 * version do not carry a title, the interface of this mod is drawn over everything anyway.
 *
 * @author Enaium
 */
class StatsImGuiScreen(private val parent: Screen?) : Screen(),
    ImGuiRenderable, StatsHost {

    override fun render(io: ImGuiIO) {
        StatsGui.render(io)
    }

    override fun init() {
        ClientEnvironment.prepare()
        StatsGui.open(this)
    }

    override fun openVanillaStatistics() {
        ClientEnvironment.openVanillaStatistics()
    }

    override fun close() {
        MinecraftClient.getInstance().setScreen(parent)
    }

    override fun exportDirectory(): Path = ClientEnvironment.gameDir.resolve(AllInStats.MOD_ID).resolve("exports")
}
