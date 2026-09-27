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

package cn.enaium.allinstats

import cn.enaium.allinstats.model.StatSource
import cn.enaium.allinstats.model.StatType
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.LightningBoltEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.ServerPlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.server.MinecraftServer
import java.nio.file.Path

/**
 * The Minecraft 1.10.2 side of the statistics: the identifiers that are stored in the database and
 * the identity of the world and the players.
 *
 * @author Enaium
 */
object Environment {
    /**
     * The item registry of this version is a registry of identifiers, an item knows its own
     * identifier (`minecraft:stone`).
     */
    fun itemId(stack: ItemStack): String = Item.REGISTRY.getIdentifier(stack.item).toString()

    fun blockId(state: BlockState): String = Block.REGISTRY.getIdentifier(state.block).toString()

    /**
     * Not every entity of this version is registered, the game itself falls back to a plain name for
     * the player and the lightning bolt (`EntityType.equals`). An unregistered entity (a modded one)
     * is named after its class.
     */
    fun entityId(entity: Entity): String = EntityType.getEntityName(entity)
        ?: when (entity) {
            is PlayerEntity -> "Player"
            is LightningBoltEntity -> "LightningBolt"
            else -> entity.javaClass.simpleName
        }

    /**
     * The save of the running server, every world is tracked on its own.
     */
    fun worldId(server: MinecraftServer): String = server.levelName

    fun gameDir(server: MinecraftServer): Path = server.runDirectory.toPath()

    fun playerUuid(player: PlayerEntity): String = player.uuid.toString()

    fun playerName(player: PlayerEntity): String = player.gameProfile.name

    /**
     * Registers every player that is online, called once per tick.
     */
    fun registerPlayers(server: MinecraftServer) {
        server.playerManager.players.forEach { player ->
            AllInStats.player(playerUuid(player), playerName(player))
        }
    }

    /**
     * Counts what a player killed and what killed a player. The death of a player is not chained to
     * the death of a living entity in this version, the player is reported by its own class.
     */
    fun killed(victim: LivingEntity, source: DamageSource) {
        if (victim.world.isClient) {
            return
        }
        val attacker = source.attacker
        if (attacker is ServerPlayerEntity) {
            AllInStats.record(
                StatType.ENTITY_KILLED,
                entityId(victim),
                StatSource.NONE,
                1L,
                playerUuid(attacker)
            )
        }
        if (victim is ServerPlayerEntity && attacker != null && attacker !is PlayerEntity) {
            AllInStats.record(
                StatType.ENTITY_KILLED_BY,
                entityId(attacker),
                StatSource.NONE,
                1L,
                playerUuid(victim)
            )
        }
    }
}
