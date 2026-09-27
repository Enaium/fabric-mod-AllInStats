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

import cn.enaium.allinstats.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts what a player killed and what killed a player. The death of a player is not chained to the
 * death of a living entity in this version, that case is handled by ServerPlayerEntityMixin.
 *
 * @author Enaium
 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "onKilled(Lnet/minecraft/entity/damage/DamageSource;)V", at = @At("HEAD"))
    private void allinstats$onKilled(DamageSource source, CallbackInfo ci) {
        Environment.INSTANCE.killed((LivingEntity) (Object) this, source);
    }
}
