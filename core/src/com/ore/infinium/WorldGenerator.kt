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

package com.ore.infinium

import com.artemis.ComponentMapper
import com.artemis.annotations.Wire
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.RandomXS128
import com.badlogic.gdx.utils.PerformanceCounter
import com.ore.infinium.components.FloraComponent
import com.ore.infinium.components.SpriteComponent
import com.sudoplay.joise.module.*
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
import java.util.concurrent.CountDownLatch
import javax.imageio.ImageIO
import kotlin.concurrent.thread

@Wire
class WorldGenerator(private val m_world: OreWorld) {
    private lateinit var spriteMapper: ComponentMapper<SpriteComponent>

    fun generateWorld() {

        Gdx.app.log("server world gen", "worldgen starting")
        val counter = PerformanceCounter("test")
        counter.start()

        generateGrassTiles()
        generateTrees()

        counter.stop()
        val s = "total world gen took (incl transitioning, etc): $counter.current seconds"
        Gdx.app.log("", s)
    }

    private fun generateTrees() {
        /*
        var bottomY = (pos.y + (size.y * 0.5f)).toInt()
        val leftX = (pos.x - (size.x * 0.5f)).toInt().coerceIn(0, WORLD_SIZE_X)
        val rightX = (pos.x + (size.x * 0.5f)).toInt().coerceIn(0, WORLD_SIZE_Y)
        for (y in bottomY..WORLD_SIZE_Y)
        {
            for (x in leftX..rightX) {

                if (isBlockSolid(x, y)) {
                    //can't proceed, we'll leave it where it last wasn't solid
                    return
                }

            }

            //full row was found to be lying on empty stuff,  move down
            //until we hit solid, and then abort
            pos.y = (y.toFloat() - size.y * 0.5f) + 1
        }
        */

        val rand = RandomXS128()
        //        for (int y = 0; y < WORLD_SIZE_Y; ++y) {
        //       }
        //randomRange(, 20, rand)

        /*
        we want to start at the top, at a random sample, work our way down,
        checking each block we move the potential tree down, looking for
         */

        var treePlanted = true
        var tree: Int? = null
        for (x in 0..OreWorld.WORLD_SIZE_X - 50 step 4) {

            //we reuse the previous tree, if not planted, since we have to spawn them to know how big they
            //may end up being. but we have to know the size to know where to place them,
            //or if their placement is even valid!!
            if (treePlanted) {
                //todo randomize tree sizes
                tree = m_world.createWoodenTree(FloraComponent.TreeSize.Large)
            }

            val spriteComponent = spriteMapper.get(tree!!)
            val halfTreeHeight = spriteComponent.sprite.height

            treeY@ for (y in 0..OreWorld.WORLD_SIZE_Y - 50) {
                val treeX = x.toFloat()
                val treeY = y.toFloat()

                when (m_world.isEntityFullyGrounded(entityX = treeX, entityY = treeY,
                                                    entityWidth = spriteComponent.sprite.width,
                                                    entityHeight = spriteComponent.sprite.height)) {
                    OreWorld.EntitySolidGroundStatus.FullyEmpty -> {
                    }

                    OreWorld.EntitySolidGroundStatus.PartiallyGrounded -> {
                        //fail here. abort, can't grow a tree
                        break@treeY
                    }

                    OreWorld.EntitySolidGroundStatus.FullySolid -> {
                        spriteComponent.sprite.setPosition(treeX, treeY)
                        treePlanted = true
                        //found our tree, already planted at this y value. skip
                        break@treeY
                    }
                }
            }
        }

        if (!treePlanted) {
            //last tree, couldn't find a spot for it..delete
            m_world.m_artemisWorld.delete(tree!!)
        }

    }

    /**
     * world gen, generates the initial grass of the world
     */
    private fun generateGrassTiles() {
        for (x in 0 until OreWorld.WORLD_SIZE_X) {
            var y = 0
            while (y < OreWorld.WORLD_SIZE_Y) {
                val blockType = m_world.blockType(x, y)

                //fixme check biomes and their ranges
                //fill the surface/exposed dirt blocks with grass blocks
                if (blockType == OreBlock.BlockType.Dirt.oreValue) {
                    val topBlockType = m_world.blockTypeSafely(x, y - 1)

                    if (topBlockType == OreBlock.BlockType.Air.oreValue) {
                        m_world.setBlockFlag(x, y, OreBlock.BlockFlags.GrassBlock)

                        y = OreWorld.WORLD_SIZE_Y
                    }
                }
                ++y
            }
        }

        for (x in 0 until OreWorld.WORLD_SIZE_X) {
            for (y in 0 until OreWorld.WORLD_SIZE_Y) {
                val blockType = m_world.blockType(x, y)

                if (blockType == OreBlock.BlockType.Dirt.oreValue && m_world.blockHasFlag(x, y,
                                                                                          OreBlock.BlockFlags.GrassBlock)) {

                    val topBlockType = m_world.blockTypeSafely(x, y - 1)
                    //OreBlock bottomBlock = blockTypeSafely(x, y + 1);
                    //OreBlock bottomLeftBlock = blockTypeSafely(x - 1, y + 1);
                    //OreBlock bottomRightBlock = blockTypeSafely(x + 1, y + 1);

                    //                    boolean leftEmpty =

                    //grows grass here
                    if (topBlockType == OreBlock.BlockType.Air.oreValue) {
                        m_world.setBlockFlag(x, y, OreBlock.BlockFlags.GrassBlock)
                    }
                }
            }
        }
    }

    class WorldGenOutputInfo(val worldSize: OreWorld.WorldSize, val seed: Long, val useUniqueImageName: Boolean) {
    }


    /**
     * integer values of ores, only for the scope of
     * the world generation. outside of here, it's referred
     * to as way different values. this is because of our
     * noise mapping/generation.
     * The mapping of ores to colors also is only useful for
     * world generation output image.
     *
     * TODO in the future it might be useful for rendering a mini map?
     * but..we'd have to refactor this all and hopefully share more code
     * but i'd have to see if the noise functions appreciate that. i do
     * not suspect they would.
     *
     * WARNING: if you add to this, you must change OreBlock, also changing
     * values would be considered backwards incompatible. OreBlock references
     * these, so changing this changes in-memory and thus on-disk format.
     * (which is the only thing that would be hard-coded, is the world save)
     */


    /**
     * how many worker threads are running write now.
     * 0 indicates we are all done, each one counted
     * down their latch and returned
     */
    var workerThreadsRemainingLatch: CountDownLatch? = null

    /**
     * generates @param numberOfImages number of worlds to generate, and each one
     * will get output as a unique image. for batch testing of world gen
     */
    /*
    HACK no longer used since we hooked it up to physically generating the blocks world/array
    fun generateWorldAndOutputMultipleImages(worldSize: OreWorld.WorldSize = OreWorld.WorldSize.Small,
                                             threadCount: Int = 8,
                                             numberOfImages: Int) {

        for (i in 1..numberOfImages) {
            generateWorldAndOutputImage(worldSize, useUniqueImageName = true)
        }
    }
    */

    /**
     * Performs all world generation according to parameters
     * Multithreaded to the number of cpus (logical) the system has, automatically
     */
    fun generateWorld(worldSize: OreWorld.WorldSize) {
        val threadCount = Runtime.getRuntime().availableProcessors()

        workerThreadsRemainingLatch = CountDownLatch(threadCount)

        val random = Random()

        var seed = random.nextLong()
        seed = -789257892798191

        //inputSeed = -1918956776030106261 //awesome volcanos, lakes

        //inputSeed = 5731577342163850638 at least 3 sizeable possible lakes
        /*
               */

        // inputSeed = -7198005506662559321 //HACK come back tot his one

        //inputSeed = -1035968868854334198 //nice

        //        inputSeed = -2508926092370260247

        //inputSeed = 5428724783975243130
        // -8419318201523289748 // looks fine
        //inputSeed = 1259463552345147173 too mountainy???
        // inputSeed = 5528222012793640519 //really good looking, 1 big, 1 med, 1 small lake, rather mountainy terrain

        //inputSeed = -6138229519190689039 looks good, pretty lakey

        //inputSeed = -8923710370920184611 seems fine, multiple smaller lakes?

        //inputSeed = 4102601002453631916 //showcase of it being broken (and hopefully fixed)


        //inputSeed = -12798241782634058 DEFINITELY WORKS!! 2 lakes baby!!
        //-7257021391824154752 WORKS with lake i think?
        //inputSeed = -4058144727897976167 //problematic caves, caves are too..long and..pipey/flat
        //inputSeed = 5243159850199723543

        OreWorld.log("world gen", "inputSeed was $seed")

        OreWorld.log("world gen", "worldgen starting on $threadCount threads")

        val counter = PerformanceCounter("world gen")
        counter.start()

        for (thread in 1..threadCount) {
            thread { generateWorldThreaded(worldSize, thread, threadCount, seed) }
        }

        //halt until all threads come back up. remember, for client-hosted server,
        //user clicks button on client thread, client thread calls into server code, drops off
        // (not network) params for world gen, server eventually picks it up,
        // calls into this main generation method,
        //passes parameters the client gave it. client then wants to know the status, for loading bars etc.
        //it'll want to know how many threads are going on. it can probably just call right into the
        //server code, then into the world generator, and find that information, and set appropriate
        //ui values
        workerThreadsRemainingLatch!!.await()

        //hack, set block wall type for each part that's underground!
        //obviously will need replaced with something less stupid
        for (y in 0 until worldSize.height) {
            for (x in 0 until worldSize.width) {
                if (m_world.blockType(x, y) != OreBlock.BlockType.Air.oreValue) {
                    m_world.setBlockWallType(x, y, OreBlock.WallType.DirtUnderground.oreValue)
                }
            }
        }

        generateLakesAndVolcanoes(worldSize)

        counter.stop()
        val s = "total world generation finished after ${counter.current} seconds"
        OreWorld.log("world gen", s)

        val worldGenInfo = WorldGenOutputInfo(worldSize, seed, useUniqueImageName = false)
        writeWorldImage(worldGenInfo)
    }

    private fun generateLakesAndVolcanoes(worldSize: OreWorld.WorldSize) {
        val terrainContour = ArrayList <Int>()
        for (x in 0 until worldSize.width) {
            for (y in 0 until worldSize.height) {
                if (m_world.isBlockSolid(x, y)) {
                    //x is implied via index
                    terrainContour.add(y)
                    break
                }
            }
        }

        val delta = 10//7
        val peakResult = findPeaks(terrainContour, delta)

        //NOTE: we swap minima and maxima, because when we're going downward
        //on the map, from top y, a mountain would appear smaller.
        //but it'd be more confusing to subtract the world height, and then
        //readd it back afterwards. so, minimas would be mountains..where
        //lava is and stuff
        for ((x, y) in peakResult.minima) {
            m_world.setBlockType(x, y.toInt(), OreBlock.BlockType.Lava.oreValue)
        }

        for ((x, y) in peakResult.maxima) {
            m_world.setBlockType(x, y.toInt(), OreBlock.BlockType.Water.oreValue)
        }


    }

//public Font(@Nullable java.lang.String s,
//            @org.intellij.lang.annotations.MagicConstant(flags={java.awt.Font.PLAIN, java.awt.Font.BOLD, java.awt.Font.ITALIC}) int i,
//            int i1)


    /**
     * threaded behind the scenes implementation of generating the world,
     * nobody outside of us should need to know that detail
     */
    private fun generateWorldThreaded(worldSize: OreWorld.WorldSize,
                                      threadNumber: Int,
                                      threadCount: Int,
                                      seed: Long) {
        val counter = PerformanceCounter("world gen thread $threadNumber")
        counter.start()

        val (groundSelect, highlandLowlandSelectCache) = generateTerrain(seed)

        val cavesModule = generateCavesThreaded(worldSize, seed,
                                                highlandLowlandSelectCache = highlandLowlandSelectCache,
                                                groundSelect = groundSelect)

        val finalOreModule: Module

        //hack, debug
        val noCaves = false
        if (noCaves) {
            finalOreModule = generateOresThreaded(worldSize, seed, groundSelect)
        } else {
            finalOreModule = generateOresThreaded(worldSize, seed, cavesModule)
        }

        val finalModule: Module = finalOreModule

        outputGeneratedWorldToBlockArrayThreaded(finalModule, worldSize, threadCount, threadNumber)

        counter.stop()

        workerThreadsRemainingLatch!!.countDown()
    }

    data class GenerateTerrainResult(val groundSelect: Module, val highlandLowlandSelectCache: Module)

    private fun generateTerrain(inputSeed: Long): GenerateTerrainResult {
        //initial ground
        val groundGradient = ModuleGradient().apply {
            setGradient(0.0, 0.0, 0.0, 1.0)
        }

        ////////////////////////// lowland

        val lowlandShapeFractal = ModuleFractal(ModuleFractal.FractalType.BILLOW,
                                                ModuleBasisFunction.BasisType.GRADIENT,
                                                ModuleBasisFunction.InterpolationType.QUINTIC).apply {
            setNumOctaves(8)
            setFrequency(8.85)
            seed = inputSeed
        }

        val lowlandAutoCorrect = ModuleAutoCorrect(0.0, 1.0).apply {
            setSource(lowlandShapeFractal)
            calculate()
        }

        val lowlandScale = ModuleScaleOffset().apply {
            setScale(0.155)
            setOffset(-0.13)
            setSource(lowlandAutoCorrect)
        }

        val lowlandYScale = ModuleScaleDomain().apply {
            setScaleY(0.0)
            setSource(lowlandScale)
        }

        val lowlandTerrain = ModuleTranslateDomain().apply {
            setAxisYSource(lowlandYScale)
            setSource(groundGradient)
        }

        ////////////////////////// highland
        val highlandShapeFractal = ModuleFractal(ModuleFractal.FractalType.FBM,
                                                 ModuleBasisFunction.BasisType.GRADIENT,
                                                 ModuleBasisFunction.InterpolationType.QUINTIC).apply {
            setNumOctaves(8)
            setFrequency(9.0)
            seed = inputSeed + 1
        }

        val highlandAutoCorrect = ModuleAutoCorrect(-1.0, 1.0).apply {
            setSource(highlandShapeFractal)
            calculate()
        }

        val highlandScale = ModuleScaleOffset().apply {
            setScale(0.015)
            setOffset(0.0)
            setSource(highlandAutoCorrect)
        }

        val highlandYScale = ModuleScaleDomain().apply {
            setScaleY(0.0)
            setSource(highlandScale)
        }

        val highlandTerrain = ModuleTranslateDomain().apply {
            setAxisYSource(highlandYScale)
            setSource(groundGradient)
        }


        /////////////////// mountain 1

        val mountainShapeFractal1 = ModuleFractal(ModuleFractal.FractalType.RIDGEMULTI,
                                                  ModuleBasisFunction.BasisType.GRADIENT,
                                                  ModuleBasisFunction.InterpolationType.QUINTIC).apply {
            setNumOctaves(8)
            setFrequency(2.0)
            seed = inputSeed + 100
        }

        val mountainAutoCorrect1 = ModuleAutoCorrect(-1.0, 1.0).apply {
            setSource(mountainShapeFractal1)
            calculate()
        }

        val mountainScale1 = ModuleScaleOffset().apply {
            setScale(0.10)
            setOffset(0.0)
            setSource(mountainAutoCorrect1)
        }

        val mountainYScale1 = ModuleScaleDomain().apply {
            setScaleY(0.5)
            setSource(mountainScale1)
        }

        val mountainTerrain1 = ModuleTranslateDomain().apply {
            setAxisYSource(mountainYScale1)
            setSource(groundGradient)
        }
        ////////////////////////////////

        /////////////////// mountain 2

        val mountainShapeFractal2 = ModuleFractal(ModuleFractal.FractalType.RIDGEMULTI,
                                                  ModuleBasisFunction.BasisType.GRADIENT,
                                                  ModuleBasisFunction.InterpolationType.QUINTIC).apply {
            setNumOctaves(8)
            setFrequency(2.0)
            seed = inputSeed + 2
        }

        val mountainAutoCorrect2 = ModuleAutoCorrect(-1.0, 1.0).apply {
            setSource(mountainShapeFractal2)
            calculate()
        }

        val mountainScale2 = ModuleScaleOffset().apply {
            setScale(0.10)
            setOffset(0.0)
            setSource(mountainAutoCorrect2)
        }

        val mountainYScale2 = ModuleScaleDomain().apply {
            setScaleY(0.5)
            setSource(mountainScale2)
        }

        val mountainTerrain2 = ModuleTranslateDomain().apply {
            setAxisYSource(mountainYScale2)
            setSource(groundGradient)
        }


        //////////////// terrain

        val terrainTypeFractal = ModuleFractal(ModuleFractal.FractalType.FBM,
                                               ModuleBasisFunction.BasisType.GRADIENT,
                                               ModuleBasisFunction.InterpolationType.QUINTIC).apply {
            setNumOctaves(9)
            setFrequency(1.825)
            seed = inputSeed + 3
        }

        val terrainAutoCorrect = ModuleAutoCorrect(0.0, 1.0).apply {
            setSource(terrainTypeFractal)
            calculate()
        }

        val terrainTypeYScale = ModuleScaleDomain().apply {
            setScaleY(0.0)
            setSource(terrainAutoCorrect)
        }

        val terrainTypeCache = ModuleCache()
        terrainTypeCache.setSource(terrainTypeYScale)

        ///////////////////////////////


        /////////////// lakes
        val lakeFBM = ModuleFractal(ModuleFractal.FractalType.FBM,
                                    ModuleBasisFunction.BasisType.GRADIENT,
                                    ModuleBasisFunction.InterpolationType.QUINTIC).apply {
            setNumOctaves(6)
            setFrequency(1.005)
            seed = inputSeed + 4
        }

        /*
        val highlandLakeSelect = ModuleSelect()
        highlandLakeSelect.setLowSource(lakeFBM)
        highlandLakeSelect.setHighSource(1.0)
        highlandLakeSelect.setThreshold(0.1)
        highlandLakeSelect.setControlSource(highlandTerrain)
        */

        val lakeAutoCorrect = ModuleAutoCorrect(0.0, 1.0).apply {
            setSource(lakeFBM)
            calculate()
        }

        val lakeScale = ModuleScaleOffset().apply {
            setScale(-0.100) //-.150
            setOffset(0.00)
            setSource(lakeAutoCorrect)
        }

        val lakeYScale = ModuleScaleDomain().apply {
            setScaleY(0.0)
            setSource(lakeScale)
        }

        val lakeTerrain = ModuleTranslateDomain().apply {
            setAxisYSource(lakeYScale)
            setSource(groundGradient)
        }

        ////////////////// end lake

        //HACK, debug only
        val selectLakes = true

        val highlandLakeSelect = ModuleSelect().apply {
//        highlandMountainSelect.setLowSource(highlandTerrain) //WARNING this is where we're interested? for lakes
            //setLowSource(highlandLakeSelect)
            setLowSource(lakeTerrain)
            setHighSource(highlandTerrain)
            setControlSource(terrainTypeCache)
            setThreshold(0.21)//51 seemed decent
            //or .31
            setFalloff(0.1) // 0.1 is good, 0 is for testing
            //setFalloff(0.5)
        }

        val highlandMountainSelect1 = ModuleSelect().apply {
            if (selectLakes) {
                setLowSource(highlandLakeSelect)
            } else {
                setLowSource(highlandTerrain)
            }
            setHighSource(mountainTerrain2)
            setControlSource(terrainTypeCache)
            setThreshold(0.45)//.35 //.65
            //small falloffs give us nice occasional mountainy cliffs
            setFalloff(0.40)
        }

        val highlandMountainSelect2 = ModuleSelect().apply {
            setLowSource(mountainTerrain2)
            setHighSource(highlandMountainSelect1)
            setControlSource(terrainTypeCache)
            setThreshold(0.35)//.35 //.65
            setFalloff(0.1)
        }


        val highlandLowlandSelect = ModuleSelect().apply {
            setLowSource(lowlandTerrain)
//          setLowSource(lakeSelect) HACK
//          setHighSource(highlandMountainSelect)
            setHighSource(highlandMountainSelect2)
            setControlSource(terrainTypeCache)
            setThreshold(0.09)//.15 //.19 ?
            setFalloff(0.1) // .5
        }

        val highlandLowlandSelectCache = ModuleCache()
        highlandLowlandSelectCache.setSource(highlandLowlandSelect)

        val groundSelect = ModuleSelect().apply {
            setLowSource(0.0)
            setHighSource(1.0)
            setThreshold(0.14)
            setControlSource(highlandLowlandSelectCache)
        }

        return GenerateTerrainResult(groundSelect = groundSelect,
                                     highlandLowlandSelectCache = highlandLowlandSelectCache)
    }

    private fun generateCavesThreaded(worldSize: OreWorld.WorldSize,
                                      inputSeed: Long,
                                      highlandLowlandSelectCache: Module,
                                      groundSelect: Module): Module {
        val caveShape = ModuleFractal(ModuleFractal.FractalType.RIDGEMULTI, ModuleBasisFunction.BasisType.GRADIENT,
                                      ModuleBasisFunction.InterpolationType.QUINTIC).apply {
            setNumOctaves(1)
            setFrequency(8.0)
            seed = inputSeed
        }

        val caveAttenuateBias = ModuleBias(0.95).apply {
            setSource(highlandLowlandSelectCache)
        }

        val caveShapeAttenuate = ModuleCombiner(ModuleCombiner.CombinerType.MULT).apply {
            setSource(0, caveShape)
            setSource(1, caveAttenuateBias)
        }

        val cavePerturbFractal = ModuleFractal(ModuleFractal.FractalType.FBM,
                                               ModuleBasisFunction.BasisType.GRADIENT,
                                               ModuleBasisFunction.InterpolationType.QUINTIC).apply {
            setNumOctaves(6)
            setFrequency(3.0)
            seed = inputSeed + 1
        }

        val cavePerturbScale = ModuleScaleOffset().apply {
            setScale(0.75)
            setOffset(0.0)
            setSource(cavePerturbFractal)
        }

        val cavePerturb = ModuleTranslateDomain().apply {
            setAxisXSource(cavePerturbScale)
            setSource(caveShapeAttenuate)
        }

        val caveSelect = ModuleSelect().apply {
            setLowSource(1.0)
            setHighSource(0.0)
            setControlSource(cavePerturb)
            setThreshold(0.9)
            setFalloff(0.0)
        }

        //final step
        val groundCaveMultiply = ModuleCombiner(ModuleCombiner.CombinerType.MULT).apply {
            setSource(0, caveSelect)
            setSource(1, groundSelect)
        }

        return groundCaveMultiply
    }

    /**
     * @return the final module of the ores
     */
    private fun generateOresThreaded(worldSize: OreWorld.WorldSize,
                                     inputSeed: Long,
                                     groundCaveMultiply: Module): Module {

        /////////////////////////////////////////////////////
        val mainGradient = ModuleGradient()
        mainGradient.setGradient(0.0, 0.0, 0.0, 1.0)

        val copperFBM = ModuleFractal(ModuleFractal.FractalType.FBM, ModuleBasisFunction.BasisType.GRADIENT,
                                      ModuleBasisFunction.InterpolationType.QUINTIC).apply {
            seed = inputSeed
            setNumOctaves(4)
            setFrequency(450.0)
        }

//            val copperFBMRemap = ModuleScaleOffset()
//            copperFBMRemap.setSource(copperFBM)
//            copperFBMRemap.setScale(0.5)
//            copperFBMRemap.setOffset(0.5)

        //copper or stone. higher density == more stone. fuck if i know why.
//            val COPPER_DENSITY = 0.58
        val COPPER_DENSITY = 0.2
        val copperSelect = ModuleSelect().apply {
            setLowSource(OreBlock.BlockType.Stone.oreValue.toDouble())
            setHighSource(OreBlock.BlockType.Copper.oreValue.toDouble())
            setControlSource(copperFBM)
            setThreshold(COPPER_DENSITY)
            setFalloff(0.1)
        }


        //////////////////////////////////////////////// COAL

        val coalFBM = ModuleFractal(ModuleFractal.FractalType.FBM, ModuleBasisFunction.BasisType.GRADIENT,
                                    ModuleBasisFunction.InterpolationType.QUINTIC).apply {
            seed = inputSeed + 1
            setNumOctaves(7)
            setFrequency(250.0)
        }

        val coalSelect = ModuleSelect().apply {
            setLowSource(copperSelect)
            setHighSource(OreBlock.BlockType.Coal.oreValue.toDouble())
            setControlSource(coalFBM)
            setThreshold(0.5)
            setFalloff(0.0)
        }

        /////////////////////////////////////////////// IRON
        val ironFBM = ModuleFractal(ModuleFractal.FractalType.FBM, ModuleBasisFunction.BasisType.GRADIENT,
                                    ModuleBasisFunction.InterpolationType.QUINTIC).apply {
            seed = inputSeed + 2
            setNumOctaves(5)
            setFrequency(250.0)
        }

        val ironSelect = ModuleSelect().apply {
            setLowSource(coalSelect)
            setHighSource(OreBlock.BlockType.Iron.oreValue.toDouble())
            setControlSource(ironFBM)
            setThreshold(0.5)
            setFalloff(0.0)
        }

        ///////////////////////////////////////////////////////////////////////

        ///////////////////////////////////////////////////////// SILVER

        val silverFBM = ModuleFractal(ModuleFractal.FractalType.FBM, ModuleBasisFunction.BasisType.GRADIENT,
                                      ModuleBasisFunction.InterpolationType.QUINTIC).apply {
            seed = inputSeed + 3
            setNumOctaves(5)
            setFrequency(550.0)
        }

        //limit this ore only part way down (vertically) the world, it's a slightly more rare tier
        val silverRestrictSelect = ModuleSelect().apply {
            setLowSource(0.0)
            setHighSource(1.0)
            setControlSource(mainGradient)
            setThreshold(0.5)
            setFalloff(0.0)
        }

        val silverRestrictMult = ModuleCombiner(ModuleCombiner.CombinerType.MULT).apply {
            setSource(0, silverFBM)
            setSource(1, silverRestrictSelect)
        }

        val silverSelect = ModuleSelect().apply {
            setLowSource(ironSelect)
            setHighSource(OreBlock.BlockType.Silver.oreValue.toDouble())
            setControlSource(silverRestrictMult)
            setThreshold(0.5)
            setFalloff(0.0)
        }

        ////////////////////////////////////////////////////////////
        val goldFBM = ModuleFractal(ModuleFractal.FractalType.FBM, ModuleBasisFunction.BasisType.GRADIENT,
                                    ModuleBasisFunction.InterpolationType.QUINTIC).apply {
            seed = inputSeed + 4
            setNumOctaves(5)
            setFrequency(550.0)
        }

        //limit this ore only part way down (vertically) the world, it's a slightly more rare tier
        val goldRestrictSelect = ModuleSelect().apply {
            setLowSource(0.0)
            setHighSource(1.0)
            setControlSource(mainGradient)
            setThreshold(0.7)
            setFalloff(0.0)
        }

        val goldRestrictMult = ModuleCombiner(ModuleCombiner.CombinerType.MULT).apply {
            setSource(0, silverFBM)
            setSource(1, silverRestrictSelect)
        }

        val goldSelect = ModuleSelect().apply {
            setLowSource(silverSelect)
            setHighSource(OreBlock.BlockType.Gold.oreValue.toDouble())
            setControlSource(goldRestrictMult)
            setThreshold(0.55)
            setFalloff(0.0)
        }

        ////////////////////////////////////////////////////////////////////

        val uraniumFBM = ModuleFractal(ModuleFractal.FractalType.FBM, ModuleBasisFunction.BasisType.GRADIENT,
                                       ModuleBasisFunction.InterpolationType.QUINTIC).apply {
            seed = inputSeed + 5
            setNumOctaves(5)
            setFrequency(950.0)
        }

        //limit this ore only part way down (vertically) the world, it's a slightly more rare tier
        val uraniumRestrictSelect = ModuleSelect().apply {
            setLowSource(0.0)
            setHighSource(1.0)
            setControlSource(mainGradient)
            setThreshold(0.7)
            setFalloff(0.6)
        }

        val uraniumRestrictMult = ModuleCombiner(ModuleCombiner.CombinerType.MULT).apply {
            setSource(0, uraniumFBM)
            setSource(1, uraniumRestrictSelect)
        }

        val uraniumSelect = ModuleSelect().apply {
            setLowSource(goldSelect)
            setHighSource(OreBlock.BlockType.Uranium.oreValue.toDouble())
            setControlSource(uraniumRestrictMult)
            setThreshold(0.5)
            setFalloff(0.0)
        }

        ///////////////////////////////////////////////////////////////////////

        val diamondFBM = ModuleFractal(ModuleFractal.FractalType.FBM, ModuleBasisFunction.BasisType.GRADIENT,
                                       ModuleBasisFunction.InterpolationType.QUINTIC).apply {
            seed = inputSeed + 6
            setNumOctaves(5)
            setFrequency(650.0)
        }

        //limit this ore only part way down (vertically) the world, it's a slightly more rare tier
        val diamondRestrictSelect = ModuleSelect().apply {
            setLowSource(0.0)
            setHighSource(1.0)
            setControlSource(mainGradient)
            setThreshold(0.3)
            setFalloff(0.8)
        }

        val diamondRestrictMult = ModuleCombiner(ModuleCombiner.CombinerType.MULT).apply {
            setSource(0, diamondFBM)
            setSource(1, diamondRestrictSelect)
        }

        val diamondSelect = ModuleSelect().apply {
            setLowSource(uraniumSelect)
            setHighSource(OreBlock.BlockType.Diamond.oreValue.toDouble())
            setControlSource(diamondRestrictMult)
            setThreshold(0.65)
            setFalloff(0.0)
        }

        ////////////////////////////// DIRT
        val dirtGradient = ModuleGradient().apply {
            setGradient(0.0, 0.0, 0.0, 1.0)
        }

        val DIRT_THRESHOLD = 0.32

        val dirtRestrict = ModuleSelect().apply {
            setControlSource(dirtGradient)
            setLowSource(0.2)
            setHighSource(1.0)
            setThreshold(DIRT_THRESHOLD)
            setFalloff(0.4)
            //dirtRestrict.setFalloff(0.00)
        }

        val dirtSelect = ModuleSelect().apply {
            setControlSource(dirtRestrict)
            setLowSource(OreBlock.BlockType.Dirt.oreValue.toDouble())
            setHighSource(diamondSelect)
            setThreshold(DIRT_THRESHOLD)
            setFalloff(0.08)
            //setFalloff(0.8)
        }


        /*
        not needed
        val groundSelect = ModuleSelect()
        groundSelect.setControlSource(mainGradientRemap)
        groundSelect.setLowSource(Open.toDouble())
        groundSelect.setHighSource(dirtStoneSelect)
        groundSelect.setThreshold(0.000000)
        groundSelect.setFalloff(0.0)
        */

        //now combine with the cave/world, to cut out all the places where
        //we do not want ores to be
        val oreCaveMultiply = ModuleCombiner(ModuleCombiner.CombinerType.MULT).apply {

            setSource(0, groundCaveMultiply)
            //setSource(1, dirtSelect)
            setSource(1, dirtSelect)
        }

//            val finalGen = rareFBMRemap
//            val finalGen = rareSelect
        //var finalGen: Module = dirtSelect
        var finalGen: Module = dirtSelect
        //var finalGen: Module = coalSelect

        //hack
        val showCaves = true
        if (showCaves) {
            finalGen = oreCaveMultiply
        }

        return finalGen
        //FIXME: at the end of all of this, slap bedrock on the bottom (and sides) with just a simple loop, so they can't dig beyond the edges of the world
    }

    /**
     * renders a handy legend on the output world image
     */
    private fun writeWorldImageLegendImprint(bufferedImage: BufferedImage) {
        val graphics = bufferedImage.graphics
        graphics.font = Font("SansSerif", Font.PLAIN, 9);

        val leftX = 5
        val startY = 8
        var index = 0
        for ((oreValue, oreColor) in OreBlock.OreNoiseColorMap) {
            val y = startY + index * 8

            val oreLegendRectSize = 2

            graphics.color = oreColor
            graphics.fillRect(leftX, y, oreLegendRectSize, oreLegendRectSize)

            graphics.color = Color.MAGENTA

            val oreName = OreBlock.BlockType.values().first { it -> it.oreValue == oreValue }.name
            graphics.drawString(oreName, leftX + oreLegendRectSize * 2, y + 3)

            ++index
        }
    }

    /**
     * samples from the final module, outputs it to the world array.
     * meant to be called in a threaded fashion. (since each module.get()
     * call on each of the indices in the world, takes a lot of time due
     * to all the module chaining)
     * Meant to be called from multiple threads, automatically partitions
     * each worker into exclusive regions which they operate on the world with
     */
    private fun outputGeneratedWorldToBlockArrayThreaded(finalModule: Module,
                                                         worldSize: OreWorld.WorldSize,
                                                         threadCount: Int,
                                                         threadNumber: Int) {
        //divide the world into n threads, so that each can write to their own region simultaneously
        val partitionedHeight = worldSize.height / threadCount

        val startY = (threadNumber - 1) * partitionedHeight
        val endY = startY + partitionedHeight
        //fixme i'm very skeptical what happens if given an odd number of world size...maybe we need to use the remainder?
        //don't want to be accidentally squishing 1 block per thread, or stretching by 1 block

        for (y in startY until endY) {
            for (x in 0 until worldSize.width) {

                val xRatio = worldSize.width.toDouble() / worldSize.height.toDouble()
                val value = finalModule.get(x.toDouble() / worldSize.width.toDouble() * xRatio,
                                            y.toDouble() / worldSize.height.toDouble())

                //NOTE: we truncate the double to a byte. we don't care if it's 3.0 or 3.1 for an ore value,
                //but obviously we need it to be a flat number.
                //the reasoning for this happening in the first place, is due to falloff and the range of the
                //modules, i believe.

                //           assert (value == value.toInt().toDouble()) {
                //              "output to world array, but units aren't in round numbers -- invalid ore types obtained from noise generator"
                //         }

                //things like water and stuff are never generated by noise. so it'll just be e.g. air
                m_world.setBlockType(x, y, value.toByte())
            }
        }
    }

    //fixme don't use relative upward, fix game so it doesn't require working dir
    //to be set to core/assets. i think, is the proper way??? ...makes that initial
    //ide config easier too
    private val WORLD_OUTPUT_IMAGE_BASE_PATH = "../saveData/worldImages/"

    /**
     * output the entire world to a png.
     *
     * right now only blocks are handled. in the future, more stuff will be done
     */
    private fun writeWorldImage(worldGenInfo: WorldGenOutputInfo) {
//hack         val xRatio = worldGenInfo.worldSize.width.toDouble() / worldSize.height.toDouble()

        val bufferedImage = BufferedImage(worldGenInfo.worldSize.width, worldGenInfo.worldSize.height,
                                          BufferedImage.TYPE_INT_RGB);
        val graphics = bufferedImage.graphics;

        for (x in 0 until worldGenInfo.worldSize.width) {
            for (y in 0 until worldGenInfo.worldSize.height) {
                val blockType = m_world.blockType(x, y)

                //if we fail to match a color, we just output its raw value, something's strange here.
                val colorForOre = OreBlock.OreNoiseColorMap[blockType]!!

                val final = colorForOre
                bufferedImage.setRGB(x, y, final.rgb)
            }
        }

        graphics.color = Color.magenta;
        graphics.drawLine(0, 200, worldGenInfo.worldSize.width, 200)

        graphics.font = Font("SansSerif", Font.PLAIN, 8);
        graphics.drawString("inputSeed: ${worldGenInfo.seed}", 200, 10)

        graphics.drawString("y=200", 10, 190);

        var fileUrl = WORLD_OUTPUT_IMAGE_BASE_PATH
        val dir = File(fileUrl)
        if (!dir.exists()) {
            dir.mkdirs()
        }

        if (worldGenInfo.useUniqueImageName) {
            fileUrl += "worldgeneration-${worldGenInfo.seed}.png"
        } else {
            fileUrl += "worldgeneration.png"
        }

        writeWorldImageLegendImprint(bufferedImage)

        ImageIO.write(bufferedImage, "png", File(fileUrl));
    }

    class PeakResult() {
        //val minima = mutableListOf<XYPair>()
        val minima = HashMap <Int, Int>()
        val maxima = HashMap <Int, Int>()

        //data class XYPair(val x: Int, val y: Int)
    }

    /**
     *
     * @param values list of y values on a contour, to be checked for
     * min and max. X values are currently implicitly the indices
     * of this array
     *
     * @param delta difference between values to consider if it is
     * a local max or min
     *
     * @return list of minima and maxima
     *
     * based on http://billauer.co.il/peakdet.html
     */
    fun findPeaks(values: List<Int>, delta: Int): PeakResult {
        val peakResult = PeakResult()

        var maximum = 0
        var minimum = 0

        var maximumPos = 0
        var minimumPos = 0

        var lookForMax = true

        for ((index, value) in values.withIndex()) {
            if (value > maximum) {
                maximum = value
                maximumPos = index
            }

            if (value < minimum) {
                minimum = value
                minimumPos = index
            }

            if (lookForMax) {
                if (value < maximum - delta) {
                    peakResult.maxima.put(maximumPos, value)

                    minimum = value
                    minimumPos = index
                    lookForMax = false
                }
            } else {
                if (value > minimum + delta) {
                    //peakResult.minima.add(PeakResult.XYPair(x = minimumPos, y = value))
                    peakResult.minima.put(minimumPos, value)

                    maximum = value
                    maximumPos = index
                    lookForMax = true
                }
            }
        }

        return peakResult
    }
}


