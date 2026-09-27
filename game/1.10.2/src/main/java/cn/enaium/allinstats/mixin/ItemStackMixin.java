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
 * hands of the player by identity, otherwise the armour of a player (this method is used for the
 * armour too) would be counted as well. A stack of this version is empty when it holds no item.
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
        AllInStats.record(StatType.TOOL_USED, item, StatSource.NONE, (long) amount, uuid);
        if (((ItemStack) (Object) this).count <= 0) {
            AllInStats.record(StatType.TOOL_BROKEN, item, StatSource.NONE, 1L, uuid);
        }
    }
}
