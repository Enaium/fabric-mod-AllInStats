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
import cn.enaium.allinstats.model.StatSource;
import cn.enaium.allinstats.model.StatType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.BlockItem;
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
 * counted. The placement of this version is `use`, there is no placement context, the player and the
 * world are the first arguments. `ActionResult` is an enum, the placement was accepted when `use`
 * returned `SUCCESS`.
 *
 * @author Enaium
 */
@Mixin(BlockItem.class)
public class BlockItemMixin {
    @Inject(method = "use(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/Hand;Lnet/minecraft/util/math/Direction;FFF)Lnet/minecraft/util/ActionResult;", at = @At("RETURN"))
    private void allinstats$place(
            PlayerEntity player,
            World world,
            BlockPos pos,
            Hand hand,
            Direction facing,
            float hitX,
            float hitY,
            float hitZ,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        final ActionResult result = cir.getReturnValue();
        if (result != ActionResult.SUCCESS) {
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
