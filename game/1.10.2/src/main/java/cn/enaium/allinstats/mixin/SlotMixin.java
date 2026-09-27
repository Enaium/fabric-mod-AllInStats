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
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.slot.CraftingResultSlot;
import net.minecraft.inventory.slot.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Items that are taken out of a container or out of the crafting result. Moving items inside the own
 * inventory is not counted. The stack of this version is empty when it holds no item.
 *
 * @author Enaium
 */
@Mixin(Slot.class)
public class SlotMixin {
    @Shadow
    @Final
    public Inventory inventory;

    @Inject(method = "onTakeItem(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/ItemStack;)V", at = @At("HEAD"))
    private void allinstats$onTakeItem(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity) || stack == null || stack.count <= 0) {
            return;
        }
        if (inventory instanceof PlayerInventory) {
            return;
        }
        final StatSource source = ((Object) this) instanceof CraftingResultSlot
                ? StatSource.CRAFT
                : StatSource.CONTAINER;
        AllInStats.record(
                StatType.ITEM_ACQUIRED,
                Environment.INSTANCE.itemId(stack),
                source,
                (long) stack.count,
                Environment.INSTANCE.playerUuid(player)
        );
    }
}
