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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts what a player killed and what killed a player.
 *
 * @author Enaium
 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"))
    private void allinstats$die(DamageSource source, CallbackInfo ci) {
        final LivingEntity victim = (LivingEntity) (Object) this;
        if (victim.level().isClientSide()) {
            return;
        }
        final Entity attacker = source.getEntity();
        if (attacker instanceof ServerPlayer player) {
            AllInStats.record(
                    StatType.ENTITY_KILLED,
                    Environment.INSTANCE.entityId(victim),
                    StatSource.NONE,
                    1L,
                    Environment.INSTANCE.playerUuid(player)
            );
        }
        if (victim instanceof ServerPlayer player && attacker != null && !(attacker instanceof Player)) {
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
