/**
MIT License

Copyright (c) 2016 Shaun Reich <sreich02@gmail.com>

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

package com.ore.infinium.systems.server

import com.artemis.BaseSystem
import com.artemis.annotations.Wire
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.TimeUtils
import com.ore.infinium.OreBlock
import com.ore.infinium.OreWorld
import com.ore.infinium.components.ItemComponent
import com.ore.infinium.components.PlayerComponent
import com.ore.infinium.components.SpriteComponent
import com.ore.infinium.components.ToolComponent
import com.ore.infinium.systems.GameTickSystem
import com.ore.infinium.util.mapper
import com.ore.infinium.util.opt
import com.ore.infinium.util.system

@Wire(failOnNull = false)
class ServerBlockDiggingSystem(private val oreWorld: OreWorld) : BaseSystem() {

    private val mPlayer by mapper<PlayerComponent>()
    private val mSprite by mapper<SpriteComponent>()
    private val mItem by mapper<ItemComponent>()
    private val mTool by mapper<ToolComponent>()

    private val serverNetworkSystem by system<ServerNetworkSystem>()
    private val tileLightingSystem by system<TileLightingSystem>()
    private val gameTickSystem by system<GameTickSystem>()

    class BlockToDig(
            val x: Int,
            val y: Int,
            /**
             * the tick when the dig request for this block was started
             */
            val digStartTick: Long,

            /**
             * player id (connection id) associated with this dig request
             * NOT entity id.
             * so we can e.g. know if a connection is no longer alive for a player,
             * and remove all such pending requests
             */
            val playerId: Int,

            /**
             * whether or not we've received from client as this block
             * being finished digging. if this gets sent to soon, it's disregarded
             * and will eventually timeout. if it sent too late, that too, will time out.
             * The client might lie too, so be sure to double check.
             */
            var clientSaysItFinished: Boolean = false
    )

    //todo verify that there aren't too many blocks all at once from the same player
    //she could in theory send 500 block updates..requiring only the time for 1 block dig

    private val m_blocksToDig = mutableListOf<BlockToDig>()

    override fun dispose() {
    }

    /**
     * @return true if it was processed (and should be removed)
     */
    fun processAndRemoveDigRequests(blockToDig: BlockToDig): Boolean {
        val blockType = oreWorld.blockType(blockToDig.x, blockToDig.y)
        if (blockType == OreBlock.BlockType.Air.oreValue) {
            return true
        }

        val playerEntityId = oreWorld.playerEntityForPlayerConnectionID(blockToDig.playerId)
        val playerComponent = mPlayer.get(playerEntityId)
        val equippedItemEntityId = playerComponent.equippedPrimaryItem

        //player no longer even has an item that can break stuff, equipped.
        //this queued request will now be canceled.
        val toolComponent = mTool.opt(equippedItemEntityId) ?: return true

        val totalBlockHealth = OreBlock.blockAttributes[blockType]!!.blockTotalHealth

        val damagePerTick = toolComponent.blockDamage * getWorld().getDelta()

        //this many ticks after start tick, it should have already been destroyed
        val expectedTickEnd = blockToDig.digStartTick + (totalBlockHealth / damagePerTick).toInt()

        if (blockToDig.clientSaysItFinished && gameTickSystem.ticks >= expectedTickEnd) {
            //todo tell all clients that it was officially dug--but first we want to implement chunking
            // though!!

            OreWorld.log("server, block digging system", "processSystem block succeeded. sending")

            val x = blockToDig.x
            val y = blockToDig.y

            serverNetworkSystem.sendPlayerSingleBlock(playerEntityId, x, y)

            val droppedBlock = oreWorld.createBlockItem(blockType)
            mSprite.get(droppedBlock).apply {
                sprite.setPosition(x + 0.5f, y + 0.5f)
                sprite.setSize(0.5f, 0.5f)
            }

            mItem.get(droppedBlock).apply {
                sizeBeforeDrop = Vector2(1f, 1f)
                stackSize = 1
                state = ItemComponent.State.DroppedInWorld
                playerIdWhoDropped = playerComponent.connectionPlayerId
                timeOfDropMs = TimeUtils.millis()
            }

            //hack this isnt 'needed i don't think because the server network entity system
            //auto finds and spawns it
            // m_networkServerSystem.sendSpawnEntity(droppedBlock, playerComponent.connectionPlayerId);

            oreWorld.destroyBlock(x, y)

            //update lighting in the area
            val lightLevel = oreWorld.blockLightLevel(x, y)

            tileLightingSystem.updateTileLighting(x, y, lightLevel)

            //hack, this is a big region, and we'd have to calculate actual lights in this, as well i think.
            //but we wouldn't want it to be bigger than the affected region
            serverNetworkSystem.sendPlayerBlockRegion(playerEntityId, x - 20, y - 20, x + 20, y + 20)

            //remove fulfilled request from our queue.
            return true
        }

        //when actual ticks surpass our expected ticks, by so much
        //we assume this request times out
        if (gameTickSystem.ticks > expectedTickEnd + 10) {

            OreWorld.log("server, block digging system",
                    "processSystem block digging request timed out. this could be normal.")
            return true
        }

        return false
    }

    //todo when the equipped item changes, abort all active digs for that player
    override fun processSystem() {
        m_blocksToDig.removeAll { blockToDig -> processAndRemoveDigRequests(blockToDig) }
    }

    override fun initialize() {
    }

    /**
     * @param x
     * *
     * @param y
     */
    fun blockDiggingFinished(x: Int, y: Int) {
        for (blockToDig in m_blocksToDig) {
            if (blockToDig.x == x && blockToDig.y == y) {
                //this is our block, mark it as the client thinking/saying(or lying) it finished
                blockToDig.clientSaysItFinished = true
                OreWorld.log("server, block digging system",
                        "blockDiggingFinished - client said so it finished")

                return
            }
        }

        //if it was never found, forget about it.
        OreWorld.log("server, block digging system",
                "blockDiggingFinished message received from a client, but this block dig queued " +
                        "request " +
                        "doesn't exist. either the player is trying to cheat, or it expired (arrived too late)")
    }

    fun blockDiggingBegin(x: Int, y: Int, playerEntity: Int) {
        if (oreWorld.blockType(x, y) == OreBlock.BlockType.Air.oreValue) {
            //odd. they sent us a block pick request, but it is already null on our end.
            //perhaps just a harmless latency thing. ignore.
            OreWorld.log("server, block digging system",
                    "blockDiggingBegin we got the request to dig a block that is already null/dug. " +
                            "this is " +
                            "likely just a latency issue ")
            return
        }

        val blockToDig = BlockToDig(x = x, y = y,
                playerId = mPlayer.get(playerEntity).connectionPlayerId,
                digStartTick = gameTickSystem.ticks)

        m_blocksToDig.add(blockToDig)
    }
}
