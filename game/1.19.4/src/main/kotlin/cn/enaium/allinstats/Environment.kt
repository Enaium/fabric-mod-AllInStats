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

import net.minecraft.block.BlockState
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.server.MinecraftServer
import java.nio.file.Path

/**
 * The Minecraft 1.20.6 side of the statistics: the identifiers that are stored in the database and
 * the identity of the world and the players.
 *
 * @author Enaium
 */
object Environment {
    fun itemId(stack: ItemStack): String = Registries.ITEM.getId(stack.item).toString()

    fun blockId(state: BlockState): String = Registries.BLOCK.getId(state.block).toString()

    fun entityId(entity: Entity): String = Registries.ENTITY_TYPE.getId(entity.type).toString()

    /**
     * The save of the running server, every world is tracked on its own.
     */
    fun worldId(server: MinecraftServer): String = server.saveProperties.levelName

    fun gameDir(server: MinecraftServer): Path = server.runDirectory.toPath()

    fun playerUuid(player: PlayerEntity): String = player.uuidAsString

    fun playerName(player: PlayerEntity): String = player.gameProfile.name

    /**
     * Registers every player that is online, called once per tick.
     */
    fun registerPlayers(server: MinecraftServer) {
        server.playerManager.playerList.forEach { player ->
            AllInStats.player(playerUuid(player), playerName(player))
        }
    }
}
