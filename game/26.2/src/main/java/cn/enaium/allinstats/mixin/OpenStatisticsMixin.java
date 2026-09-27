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

package cn.enaium.allinstats.mixin;

import cn.enaium.allinstats.StatsImGuiScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The statistics button of the pause menu opens the interface of this mod instead of the statistics
 * screen of the game. The game screen is still reachable from the interface.
 *
 * @author Enaium
 */
@Mixin(Gui.class)
public abstract class OpenStatisticsMixin {
    @Shadow
    public abstract Screen screen();

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen allinstats$openStatistics(Screen screen) {
        if (screen instanceof StatsScreen && screen() instanceof PauseScreen) {
            return new StatsImGuiScreen(screen());
        }
        return screen;
    }
}
