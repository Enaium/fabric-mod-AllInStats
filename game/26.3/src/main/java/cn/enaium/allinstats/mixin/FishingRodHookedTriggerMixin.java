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
import net.minecraft.advancements.triggers.FishingRodHookedTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/**
 * The loot of a fishing rod is handed to the player here, which is exactly what was caught.
 *
 * @author Enaium
 */
@Mixin(FishingRodHookedTrigger.class)
public class FishingRodHookedTriggerMixin {
    @Inject(
            method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/projectile/FishingHook;Ljava/util/Collection;)V",
            at = @At("HEAD")
    )
    private void allinstats$trigger(
            ServerPlayer player,
            ItemStack rod,
            FishingHook bobber,
            Collection<ItemStack> loot,
            CallbackInfo ci
    ) {
        if (loot == null || loot.isEmpty()) {
            return;
        }
        final String uuid = Environment.INSTANCE.playerUuid(player);
        for (final ItemStack stack : loot) {
            if (stack.isEmpty()) {
                continue;
            }
            AllInStats.record(
                    StatType.ITEM_ACQUIRED,
                    Environment.INSTANCE.itemId(stack),
                    StatSource.FISHING,
                    stack.getCount(),
                    uuid
            );
        }
    }
}
