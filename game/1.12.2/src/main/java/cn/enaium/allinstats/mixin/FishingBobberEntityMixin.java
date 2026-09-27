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
import net.minecraft.class_2782;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.class_2780;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.Random;

/**
 * The loot of a fishing rod is rolled when the bobber is reeled in. This version has no criterion
 * that reports the catch, the loot is generated inside `retract` and handed to the world as item
 * entities from there, so the roll itself is watched to see what was caught. The thrower picks the
 * item entities up from the ground afterwards.
 *
 * @author Enaium
 */
@Mixin(FishingBobberEntity.class)
public class FishingBobberEntityMixin {
    @Redirect(
            method = "retract",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/loot/class_2780;method_11981(Ljava/util/Random;Lnet/minecraft/class_2782;)Ljava/util/List;"
            )
    )
    private List<ItemStack> allinstats$loot(class_2780 table, Random random, class_2782 context) {
        final List<ItemStack> loot = table.method_11981(random, context);
        final PlayerEntity thrower = ((FishingBobberEntity) (Object) this).getThrower();
        if (!(thrower instanceof ServerPlayerEntity) || loot == null || loot.isEmpty()) {
            return loot;
        }
        final String uuid = Environment.INSTANCE.playerUuid(thrower);
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
        return loot;
    }
}
