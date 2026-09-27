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

import cn.enaium.allinstats.gui.StatsFonts
import cn.enaium.fabric.imgui.DefaultImGui
import imgui.ImGuiIO
import imgui.flag.ImGuiConfigFlags

/**
 * The ImGui context of the game is created by this class, the font of the interface is set up here
 * because the default font of ImGui cannot render Chinese.
 *
 * @author Enaium
 */
class StatsImGui : DefaultImGui(null) {
    override fun configure(data: ImGuiIO) {
        data.iniFilename = null
        data.configFlags = ImGuiConfigFlags.DockingEnable
        StatsFonts.install(data)
    }
}
