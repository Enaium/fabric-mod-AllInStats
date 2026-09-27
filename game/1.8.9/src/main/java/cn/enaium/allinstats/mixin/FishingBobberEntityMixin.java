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
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * This version has no criterion for a caught fish, the loot is rolled in `getResult` when the bobber
 * is reeled in with a bite, which is exactly what was caught.
 *
 * @author Enaium
 */
@Mixin(FishingBobberEntity.class)
public class FishingBobberEntityMixin {
    @Shadow
    public PlayerEntity thrower;

    @Inject(method = "getResult()Lnet/minecraft/item/ItemStack;", at = @At("RETURN"))
    private void allinstats$getResult(CallbackInfoReturnable<ItemStack> cir) {
        final ItemStack stack = cir.getReturnValue();
        if (stack == null || !(thrower instanceof ServerPlayerEntity)) {
            return;
        }
        AllInStats.record(
                StatType.ITEM_ACQUIRED,
                Environment.INSTANCE.itemId(stack),
                StatSource.FISHING,
                stack.count,
                Environment.INSTANCE.playerUuid(thrower)
        );
    }
}
