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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks that are placed by a player. The item is placed on the client too, only the server side is
 * counted. In this version the placement of an item is `Item.method_3355` (`ItemBlock` overrides
 * it), the placement was accepted when it returned `SUCCESS`.
 *
 * @author Enaium
 */
@Mixin(BlockItem.class)
public class BlockItemMixin {
    @Inject(
            method = "method_3355(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/Hand;Lnet/minecraft/util/math/Direction;FFF)Lnet/minecraft/util/ActionResult;",
            at = @At("RETURN")
    )
    private void allinstats$place(
            ItemStack stack,
            PlayerEntity player,
            World world,
            BlockPos pos,
            Hand hand,
            Direction direction,
            float x,
            float y,
            float z,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        if (cir.getReturnValue() != ActionResult.SUCCESS) {
            return;
        }
        if (!(player instanceof ServerPlayerEntity) || world.isClient) {
            return;
        }
        final BlockItem item = (BlockItem) (Object) this;
        AllInStats.record(
                StatType.BLOCK_PLACED,
                Environment.INSTANCE.blockId(item.getBlock().getDefaultState()),
                StatSource.NONE,
                1L,
                Environment.INSTANCE.playerUuid(player)
        );
    }
}
