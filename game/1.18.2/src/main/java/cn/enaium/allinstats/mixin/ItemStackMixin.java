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
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Every use of a tool consumes durability, the amount and the break of a tool are counted here. Only
 * the items in the hands are counted, otherwise the armour of a player would be counted as well. The
 * game of this version does not tell the slot to the damage method, the stack that is damaged is
 * compared with the stacks of the hands instead.
 *
 * @author Enaium
 */
@Mixin(ItemStack.class)
public class ItemStackMixin {
    @Unique
    private String allinstats$damagedItem;

    @Inject(method = "damage(ILnet/minecraft/entity/LivingEntity;Ljava/util/function/Consumer;)V", at = @At("HEAD"))
    private void allinstats$beforeDamage(int amount, LivingEntity entity, Consumer<LivingEntity> breakCallback, CallbackInfo ci) {
        if (!(entity instanceof ServerPlayerEntity) || amount <= 0) {
            allinstats$damagedItem = null;
            return;
        }
        final ItemStack stack = (ItemStack) (Object) this;
        if (stack != entity.getMainHandStack() && stack != entity.getOffHandStack()) {
            allinstats$damagedItem = null;
            return;
        }
        allinstats$damagedItem = Environment.INSTANCE.itemId(stack);
    }

    @Inject(method = "damage(ILnet/minecraft/entity/LivingEntity;Ljava/util/function/Consumer;)V", at = @At("RETURN"))
    private void allinstats$afterDamage(int amount, LivingEntity entity, Consumer<LivingEntity> breakCallback, CallbackInfo ci) {
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
