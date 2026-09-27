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

package cn.enaium.allinstats.mixin;

import cn.enaium.allinstats.AllInStats;
import cn.enaium.allinstats.Environment;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The world of the statistics is the save of the running server, it starts with the server and every
 * counter belongs to it. The main loop of a server of this version is `run`, but the save is only
 * loaded inside of it, so the world is registered with the first tick. The tick of this version does
 * not take the flag of the main loop yet and the server is left through `stopServer`.
 *
 * @author Enaium
 */
@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    @Unique
    private boolean allinstats$world;

    @Inject(method = "run", at = @At("HEAD"))
    private void allinstats$initialize(CallbackInfo ci) {
        final MinecraftServer server = (MinecraftServer) (Object) this;
        AllInStats.initialize(Environment.INSTANCE.gameDir(server));
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void allinstats$world(CallbackInfo ci) {
        if (allinstats$world) {
            return;
        }
        final MinecraftServer server = (MinecraftServer) (Object) this;
        final String id = Environment.INSTANCE.worldId(server);
        if (id == null) {
            return;
        }
        allinstats$world = true;
        AllInStats.world(id, id);
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
