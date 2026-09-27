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

import cn.enaium.allinstats.gui.StatsGui
import cn.enaium.allinstats.gui.StatsHost
import cn.enaium.fabric.imgui.ImGuiRenderable
import imgui.ImGuiIO
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.nio.file.Path

/**
 * The screen that shows the interface of this mod. The interface itself is drawn by the ImGui module
 * of this game, only the button that returns to the game is handled here.
 *
 * @author Enaium
 */
class StatsImGuiScreen(private val parent: Screen?) : Screen(Component.translatable("allinstats.button.open")),
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
        Minecraft.getInstance().setScreen(parent)
    }

    override fun exportDirectory(): Path = ClientEnvironment.gameDir.resolve(AllInStats.MOD_ID).resolve("exports")
}
