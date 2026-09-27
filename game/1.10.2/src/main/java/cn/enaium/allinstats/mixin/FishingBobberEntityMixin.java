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
 * caught. In this version every catch is spawned as an item entity, the catch is counted before it
 * hits the ground. The bobber can also pull an entity towards the player, that is not a catch.
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
            if (stack != null && stack.count > 0 && thrower instanceof ServerPlayerEntity) {
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
