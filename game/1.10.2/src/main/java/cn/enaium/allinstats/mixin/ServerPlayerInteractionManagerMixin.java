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
import cn.enaium.allinstats.model.StatSource;
import cn.enaium.allinstats.model.StatType;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks that are broken by a player. The state is read before the block is removed, at that time
 * the block at the position is already air.
 *
 * @author Enaium
 */
@Mixin(ServerPlayerInteractionManager.class)
public class ServerPlayerInteractionManagerMixin {
    @Shadow
    public World world;

    @Shadow
    public ServerPlayerEntity player;

    @Unique
    private BlockState allinstats$broken;

    @Inject(method = "tryBreakBlock(Lnet/minecraft/util/math/BlockPos;)Z", at = @At("HEAD"))
    private void allinstats$startBreaking(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        allinstats$broken = world.getBlockState(pos);
    }

    @Inject(method = "tryBreakBlock(Lnet/minecraft/util/math/BlockPos;)Z", at = @At("RETURN"))
    private void allinstats$breakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        final BlockState state = allinstats$broken;
        allinstats$broken = null;
        if (state == null || !cir.getReturnValueZ()) {
            return;
        }
        AllInStats.record(
                StatType.BLOCK_BROKEN,
                Environment.INSTANCE.blockId(state),
                StatSource.NONE,
                1L,
                Environment.INSTANCE.playerUuid(player)
        );
    }
}
