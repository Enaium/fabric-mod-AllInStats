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

import cn.enaium.allinstats.AllInStats;
import cn.enaium.allinstats.Environment;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The world of the statistics is the save of the running server, it starts with the server and every
 * counter belongs to it. The save is known when the world is prepared, before the server starts to
 * tick.
 *
 * @author Enaium
 */
@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    @Inject(method = "prepareWorlds", at = @At("HEAD"))
    private void allinstats$world(CallbackInfo ci) {
        final MinecraftServer server = (MinecraftServer) (Object) this;
        AllInStats.initialize(Environment.INSTANCE.gameDir(server));
        AllInStats.world(Environment.INSTANCE.worldId(server), Environment.INSTANCE.worldId(server));
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void allinstats$tick(CallbackInfo ci) {
        final MinecraftServer server = (MinecraftServer) (Object) this;
        Environment.INSTANCE.registerPlayers(server);
        AllInStats.tick();
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void allinstats$shutdown(CallbackInfo ci) {
        AllInStats.flushNow();
    }
}
