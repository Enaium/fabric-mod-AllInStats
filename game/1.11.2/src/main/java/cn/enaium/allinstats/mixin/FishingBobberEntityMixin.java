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
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The loot of a fishing rod is handed to the player here, which is exactly what was caught. This
 * version has no criterion that is triggered with the loot, the bobber reels in its catch itself:
 * `retract` rolls the loot table and spawns every stack that was caught as an item entity, the stack
 * is captured while it is stored. Reeling in without a catch never reaches that loop, and the client
 * returns before it.
 *
 * @author Enaium
 */
@Mixin(FishingBobberEntity.class)
public class FishingBobberEntityMixin {
    @ModifyVariable(method = "retract", at = @At("STORE"), ordinal = 0)
    private ItemStack allinstats$caught(ItemStack stack) {
        final FishingBobberEntity bobber = (FishingBobberEntity) (Object) this;
        final PlayerEntity thrower = bobber.getThrower();
        if (!(thrower instanceof ServerPlayerEntity) || stack.isEmpty()) {
            return stack;
        }
        AllInStats.record(
                StatType.ITEM_ACQUIRED,
                Environment.INSTANCE.itemId(stack),
                StatSource.FISHING,
                stack.getCount(),
                Environment.INSTANCE.playerUuid(thrower)
        );
        return stack;
    }
}
