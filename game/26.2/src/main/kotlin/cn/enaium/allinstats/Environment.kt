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

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import java.nio.file.Path

/**
 * The Minecraft 26.2 side of the statistics: the identifiers that are stored in the database and
 * the identity of the world and the players.
 *
 * @author Enaium
 */
object Environment {
    fun itemId(stack: ItemStack): String = BuiltInRegistries.ITEM.getKey(stack.item).toString()

    fun blockId(state: BlockState): String = BuiltInRegistries.BLOCK.getKey(state.block).toString()

    fun entityId(entity: Entity): String = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).toString()

    /**
     * The save of the running server, every world is tracked on its own.
     */
    fun worldId(server: MinecraftServer): String = server.worldData.levelName

    fun gameDir(server: MinecraftServer): Path = server.serverDirectory

    fun playerUuid(player: Player): String = player.stringUUID

    fun playerName(player: Player): String = player.gameProfile.name

    /**
     * Registers every player that is online, called once per tick.
     */
    fun registerPlayers(server: MinecraftServer) {
        server.playerList.players.forEach { player ->
            AllInStats.player(playerUuid(player), playerName(player))
        }
    }
}
