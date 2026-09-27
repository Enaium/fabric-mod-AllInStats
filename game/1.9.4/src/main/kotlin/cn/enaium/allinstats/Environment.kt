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

package cn.enaium.allinstats

import cn.enaium.allinstats.model.StatSource
import cn.enaium.allinstats.model.StatType
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.ServerPlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.server.MinecraftServer
import java.nio.file.Path

/**
 * The Minecraft 1.9.4 side of the statistics: the identifiers that are stored in the database and the
 * identity of the world and the players.
 *
 * @author Enaium
 */
object Environment {
    /**
     * The registries of this version are registries of identifiers, an item and a block know their own
     * identifier (`minecraft:stone`).
     */
    fun itemId(stack: ItemStack): String = Item.REGISTRY.getIdentifier(stack.getItem()).toString()

    fun blockId(state: BlockState): String = Block.REGISTRY.getIdentifier(state.getBlock()).toString()

    /**
     * The entities of this version are not registered by an identifier, the game names them with a
     * plain name (`Zombie`), the same name the statistics of the game use. An entity that is not
     * registered (a modded one) is named after its class.
     */
    fun entityId(entity: Entity): String = EntityType.getEntityName(entity) ?: entity.javaClass.simpleName

    /**
     * The save of the running server, every world is tracked on its own. The name is only read while
     * the world is prepared, so it may still be missing on the very first tick.
     */
    fun worldId(server: MinecraftServer): String? = server.levelName

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
