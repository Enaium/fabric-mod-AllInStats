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
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts what a player killed and what killed a player. The death of an entity of this version is
 * `onKilled`.
 *
 * @author Enaium
 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "onKilled", at = @At("HEAD"))
    private void allinstats$onDeath(DamageSource source, CallbackInfo ci) {
        final LivingEntity victim = (LivingEntity) (Object) this;
        if (victim.getWorld().isClient) {
            return;
        }
        final Entity attacker = source.getAttacker();
        if (attacker instanceof ServerPlayerEntity) {
            final ServerPlayerEntity player = (ServerPlayerEntity) attacker;
            AllInStats.record(
                    StatType.ENTITY_KILLED,
                    Environment.INSTANCE.entityId(victim),
                    StatSource.NONE,
                    1L,
                    Environment.INSTANCE.playerUuid(player)
            );
        }
        if (victim instanceof ServerPlayerEntity && attacker != null && !(attacker instanceof PlayerEntity)) {
            final ServerPlayerEntity player = (ServerPlayerEntity) victim;
            AllInStats.record(
                    StatType.ENTITY_KILLED_BY,
                    Environment.INSTANCE.entityId(attacker),
                    StatSource.NONE,
                    1L,
                    Environment.INSTANCE.playerUuid(player)
            );
        }
    }
}
