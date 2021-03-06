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
import com.ore.infinium.OreBlock
import com.ore.infinium.OreWorld

@Wire
class TileLightingSystem(private val oreWorld: OreWorld) : BaseSystem() {

    private var initialized = false

    override fun initialize() {
    }

    companion object {
        /**
         * the max number of light levels we have for each tile.
         * we could do way more, but it'd probably cost more a lot
         * more calculation wise..which isn't a big deal, but
         * we might want to use the rest of the byte for something else.
         * haven't decided what for
         */
        const val MAX_TILE_LIGHT_LEVEL: Byte = 5
    }

    /**
     * for first-run initialization,
     * runs through the entire world tiles and finds and sets light levels
     * of each tile according to access to sunlight.
     *
     * doesn't factor in actual lights, just the global light (sunlight)
     */
    private fun computeWorldTileLighting() {
        //TODO incorporate sunlight..this is all theoretical approaches.
        //check if light is greater than sunlight and if so don't touch it..
        //sets the flag to indicate it is caused by sunlight

        //todo max y should be a reasonable base level, not far below ground
        for (y in 0 until 200) {
            for (x in 0 until OreWorld.WORLD_SIZE_X) {
                if (!oreWorld.isBlockSolid(x, y) && oreWorld.blockWallType(x, y) == OreBlock.WallType.Air.oreValue) {
                    //including the one that we first find that is solid
//                    if (oreWorld.blockLightLevel(x, y) == 0.toByte()) {
                    oreWorld.setBlockLightLevel(x, y, MAX_TILE_LIGHT_LEVEL)
                    //                   }

                    //ambient/sunlight
                    //            diamondSunlightFloodFill(x, y, MAX_TILE_LIGHT_LEVEL)
                }
            }
        }
        for (y in 0 until 200) {
            for (x in 0 until OreWorld.WORLD_SIZE_X) {
                if (!oreWorld.isBlockSolid(x, y) && oreWorld.blockWallType(x, y) == OreBlock.WallType.Air.oreValue) {
                    //including the one that we first find that is solid
                    val lightLevel = oreWorld.blockLightLevel(x, y)

                    //ambient/sunlight
                    updateTileLighting(x, y, lightLevel)

//                    diamondSunlightFloodFill(x, y, lightLevel)
                    //                   diamondSunlightFloodFill(x, y, lightLevel)
                }
            }
        }
    }

    fun updateTileLighting(x: Int, y: Int, lightLevel: Byte) {
        diamondSunlightFloodFill(x - 1, y, lightLevel)
        diamondSunlightFloodFill(x + 1, y, lightLevel)
        diamondSunlightFloodFill(x, y + 1, lightLevel)
        diamondSunlightFloodFill(x, y - 1, lightLevel)
    }

    // todo find a good number, this is a complete guess.
    // this happens when the world is mostly air, stack overflow otherwise
    val MAX_LIGHTING_DEPTH = 20
    //fixme create a max depth so we don't stack overflow when there's too many air blocks
    /**
     * @param depth current depth the function is going to (so we blowing out the stack)
     */
    private fun diamondSunlightFloodFill(x: Int, y: Int, lastLightLevel: Byte, firstRun: Boolean = true, depth: Int = 0) {
        if (oreWorld.blockXSafe(x) != x || oreWorld.blockXSafe(y) != y) {
            //out of world bounds, abort
            return
        }

        val blockType = oreWorld.blockType(x, y)

        var lightAttenuation = when (blockType) {
            OreBlock.BlockType.Air.oreValue -> 0
            else -> 1
        }

        if (firstRun) {
            lightAttenuation = 0
        }

        //light bleed off value
        val newLightLevel = (lastLightLevel - lightAttenuation).toByte()

        val currentLightLevel = oreWorld.blockLightLevel(x, y)

        //don't overwrite previous light values that were greater
//        if (newLightLevel <= currentLightLevel - 1) {
        if (newLightLevel <= currentLightLevel) {
            return
        }

        oreWorld.setBlockLightLevel(x, y, newLightLevel)

        if (depth == MAX_LIGHTING_DEPTH) {
            return
        }

        val newDepth = depth + 1
        diamondSunlightFloodFill(x - 1, y, newLightLevel, firstRun = false, depth = newDepth)
        diamondSunlightFloodFill(x + 1, y, newLightLevel, firstRun = false, depth = newDepth)
        diamondSunlightFloodFill(x, y - 1, newLightLevel, firstRun = false, depth = newDepth)
        diamondSunlightFloodFill(x, y + 1, newLightLevel, firstRun = false, depth = newDepth)
    }

    override fun processSystem() {
        if (!initialized) {
                computeWorldTileLighting()
                initialized = true
        }
    }

}
