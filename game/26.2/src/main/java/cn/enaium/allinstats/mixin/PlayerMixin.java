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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Items that are thrown away by a player.
 *
 * @author Enaium
 */
@Mixin(Player.class)
public class PlayerMixin {
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"))
    private void allinstats$drop(ItemStack stack, boolean throwRandomly, CallbackInfoReturnable<ItemEntity> cir) {
        final Player player = (Player) (Object) this;
        if (!(player instanceof ServerPlayer) || stack.isEmpty()) {
            return;
        }
        AllInStats.record(
                StatType.ITEM_DROPPED,
                Environment.INSTANCE.itemId(stack),
                StatSource.NONE,
                stack.getCount(),
                Environment.INSTANCE.playerUuid(player)
        );
    }
}
