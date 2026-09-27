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
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every use of a tool consumes durability, the amount and the break of a tool are counted here. The
 * `damage` of this version does not report the equipment slot, so the stack is compared with the
 * hands of the player by identity, otherwise the armour of a player (this overload is used for the
 * armour too) would be counted as well.
 *
 * @author Enaium
 */
@Mixin(ItemStack.class)
public class ItemStackMixin {
    @Unique
    private String allinstats$damagedItem;

    @Inject(method = "damage(ILnet/minecraft/entity/LivingEntity;)V", at = @At("HEAD"))
    private void allinstats$beforeDamage(int amount, LivingEntity entity, CallbackInfo ci) {
        allinstats$damagedItem = null;
        if (!(entity instanceof ServerPlayerEntity) || amount <= 0) {
            return;
        }
        final ServerPlayerEntity player = (ServerPlayerEntity) entity;
        final ItemStack stack = (ItemStack) (Object) this;
        if (stack != player.getMainHandStack() && stack != player.getOffHandStack()) {
            return;
        }
        allinstats$damagedItem = Environment.INSTANCE.itemId(stack);
    }

    @Inject(method = "damage(ILnet/minecraft/entity/LivingEntity;)V", at = @At("RETURN"))
    private void allinstats$afterDamage(int amount, LivingEntity entity, CallbackInfo ci) {
        final String item = allinstats$damagedItem;
        allinstats$damagedItem = null;
        if (item == null || !(entity instanceof ServerPlayerEntity)) {
            return;
        }
        final ServerPlayerEntity player = (ServerPlayerEntity) entity;
        final String uuid = Environment.INSTANCE.playerUuid(player);
        AllInStats.record(StatType.TOOL_USED, item, StatSource.NONE, amount, uuid);
        if (((ItemStack) (Object) this).isEmpty()) {
            AllInStats.record(StatType.TOOL_BROKEN, item, StatSource.NONE, 1L, uuid);
        }
    }
}
