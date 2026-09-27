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
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The loot of a fishing rod is generated when the bobber is reeled in, which is exactly what was
 * caught. This version has no criterion for it, the catch is spawned as an item entity and is counted
 * before it hits the ground. The bobber can also pull an entity towards the player, that is not a
 * catch.
 *
 * @author Enaium
 */
@Mixin(FishingBobberEntity.class)
public class FishingBobberEntityMixin {
    @Redirect(
            method = "retract",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z")
    )
    private boolean allinstats$spawnEntity(World world, Entity entity) {
        if (entity instanceof ItemEntity) {
            final ItemStack stack = ((ItemEntity) entity).getItemStack();
            final PlayerEntity thrower = ((FishingBobberEntity) (Object) this).thrower;
            if (stack != null && stack.getItem() != null && stack.count > 0 && thrower instanceof ServerPlayerEntity) {
                AllInStats.record(
                        StatType.ITEM_ACQUIRED,
                        Environment.INSTANCE.itemId(stack),
                        StatSource.FISHING,
                        (long) stack.count,
                        Environment.INSTANCE.playerUuid(thrower)
                );
            }
        }
        return world.spawnEntity(entity);
    }
}
