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

package com.ore.infinium.util

import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.RandomXS128
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Align


/**
 * convenience function
 *
 * @return true if the given milliseconds time is past
 * the given last milliseconds time, by the specified
 * threshold/interval
 * @param currentMillis
 * @param intervalMs
 * @param lastMs
 */
fun timeMsSurpassed(currentMs: Long, lastMs: Long, intervalMs: Int) = (currentMs - lastMs) > intervalMs

val Sprite.rect: Rectangle get() = Rectangle(x - (width * 0.5f), y - (height * 0.5f), width, height)

fun RandomXS128.nextInt(start: Int, end: Int): Int {
    return start + nextInt(end - start + 1)
}

val SpriteBatch.MAX_SPRITES_PER_BATCH: Int get() = 5460

/**
 * get some type safety around libgdx's alignments
 */
enum class GdxAlign(val alignValue: Int) {
    Bottom(Align.bottom),
    BottomLeft(Align.bottomLeft),
    BottomRight(Align.bottomRight),
    Center(Align.center),
    Left(Align.left),
    Right(Align.right),
    Top(Align.top),
    TopLeft(Align.topLeft),
    TopRight(Align.topRight)
}
