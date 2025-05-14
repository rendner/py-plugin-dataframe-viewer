/*
 * Copyright 2021-2025 cms.rendner (Daniel Schmidt)
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
package cms.rendner.intellij.dataframe.viewer.components.renderer.styling.cells

import java.awt.Color
import kotlin.math.*

private val transM: Array<DoubleArray> = arrayOf(
    doubleArrayOf(0.4124564, 0.2126729, 0.0193339),
    doubleArrayOf(0.3575761, 0.7151522, 0.1191920),
    doubleArrayOf(0.1804375, 0.0721750, 0.9503041)
)

private val inverseTransM: Array<DoubleArray> = arrayOf(
    doubleArrayOf(3.24045484, -0.96926639, 0.05564342),
    doubleArrayOf(-1.53713885, 1.87601093, -0.20402585),
    doubleArrayOf(-0.49853155, 0.04155608, 1.05722516)
)

// Reference white-point D65
private const val Xn = 95.047
private const val Yn = 100.0
private const val Zn = 108.883

class Colors(val background: Color, val foreground: Color)
data class RGB(var r: Int, var g: Int, var b: Int)
private data class Msh(val m: Double, val s: Double, val h: Double)

/**
 * Generate reasonable diverging colors using the technique presented in
 * ["Diverging Color Maps for Scientific Visualization (Expanded)"](https://www.kennethmoreland.com/color-maps/ColorMapsExpanded.pdf) by Kenneth Moreland.
 */
class DivergingColorMapper(minRGB: RGB, maxRGB: RGB) {
    private val minColor: Msh
    private val maxColor: Msh
    private val midColor: Msh
    private val minMaxAreSaturatedAndDistinct: Boolean

    init {
        minColor = convertToMsh(minRGB)
        maxColor = convertToMsh(maxRGB)
        midColor = Msh(m = max(max(minColor.m, maxColor.m), 88.0), s = 0.0, h = 0.0) // white
        minMaxAreSaturatedAndDistinct = minColor.s > 0.05 && maxColor.s > 0.05 && abs(minColor.h - maxColor.h) > (PI / 3.0)
    }

    /**
     * Computes the diverging background color, incl. foreground color, for a given value.
     * @param value value in the closed interval of `[0, 1]`.
     */
    fun interpolateColors(value: Double): Colors {
        var min = minColor
        var max = maxColor
        var v = value

        if (minMaxAreSaturatedAndDistinct) {
            // place white in middle
            if (v < 0.5) {
                max = midColor
                v = 2 * v
            } else {
                min = midColor
                v = 2 * v - 1
            }
        }

        if (min.s < 0.05 && max.s > 0.05) {
            min = min.copy(h = adjustHue(max, min.m))
        } else if (max.s < 0.05 && min.s > 0.05) {
            max = max.copy(h = adjustHue(min, max.m))
        }

        return convertToColors(
            Msh(
                m = (1 - v) * min.m + v * max.m,
                s = (1 - v) * min.s + v * max.s,
                h = (1 - v) * min.h + v * max.h,
            )
        )
    }

    private fun adjustHue(saturated: Msh, unsaturated: Double): Double {
        if (saturated.m >= unsaturated || saturated.m == 0.0) return saturated.h

        val spin = saturated.s * sqrt(unsaturated.pow(2) - saturated.m.pow(2)) / (saturated.m * sin(saturated.s))
        return if (saturated.h > -PI / 3.0) {
            saturated.h + spin
        } else {
            saturated.h - spin
        }
    }

    private fun convertToColors(msh: Msh): Colors {
        // L*a*b*
        val L = msh.m * cos(msh.s)
        val a = msh.m * sin(msh.s) * cos(msh.h)
        val b = msh.m * sin(msh.s) * sin(msh.h)

        val X = Xn * fInverse((a / 500.0) + (L + 16.0) / 116.0)
        val Y = Yn * fInverse((L + 16.0) / 116.0)
        val Z = Zn * fInverse((L + 16.0) / 116.0 - (b / 200.0))

        val R = linear_to_sRGB(max(0.0, inverseTransM[0][0] * X + inverseTransM[1][0] * Y + inverseTransM[2][0] * Z))
        val G = linear_to_sRGB(max(0.0, inverseTransM[0][1] * X + inverseTransM[1][1] * Y + inverseTransM[2][1] * Z))
        val B = linear_to_sRGB(max(0.0, inverseTransM[0][2] * X + inverseTransM[1][2] * Y + inverseTransM[2][2] * Z))

        return Colors(
            background = Color(R, G, B),
            foreground = if (L < 50) Color.WHITE else Color.BLACK,
        )
    }

    private fun convertToMsh(rgb: RGB): Msh {
        val linearR = sRGB_to_linear(rgb.r)
        val linearG = sRGB_to_linear(rgb.g)
        val linearB = sRGB_to_linear(rgb.b)

        val X = transM[0][0] * linearR + transM[1][0] * linearG + transM[2][0] * linearB
        val Y = transM[0][1] * linearR + transM[1][1] * linearG + transM[2][1] * linearB
        val Z = transM[0][2] * linearR + transM[1][2] * linearG + transM[2][2] * linearB

        // L*a*b*
        val L = 116.0 * (f(Y / Yn) - (16.0 / 116.0))
        val a = 500.0 * (f(X / Xn) - f(Y / Yn))
        val b = 200.0 * (f(Y / Yn) - f(Z / Zn))

        val M = sqrt(L.pow(2) + a.pow(2) + b.pow(2))
        val s = acos(if (M == 0.0) M else L / M)
        val h = atan2(b, a)

        return Msh(M, s, h)
    }

    private fun fInverse(x: Double): Double {
        val a = 7.787
        val b = 16.0 / 116.0
        val xlim = 0.008856
        val ylim = a * xlim + b
        return if (x > ylim) {
            x.pow(3)
        } else {
            (x - b) / a
        }
    }

    private fun f(x: Double): Double {
        return if (x > 0.008856) {
            x.pow(1.0 / 3.0)
        } else {
            7.787 * x + 16.0 / 116.0
        }
    }

    /**
     * Convert a linear value to a sRGB gamma encoded value.
     * @param channelValue the linear R, G or B channel in the approximate closed interval of `[0, 100]`.
     * @return a sRGB gamma encoded value in the closed interval of `[0, 255]`
     */
    private fun linear_to_sRGB(channelValue: Double): Int {
        var v = channelValue / 100.0
        // The algorithm described in the paper uses '0.00313080495356037152'
        // but this value leads to a 'Floating-point literal cannot be represented with the required precision'
        v = if (v > 0.0031308049535603713) {
            (1.055 * v.pow(1.0 / 2.4)) - 0.055
        } else {
            v * 12.92
        }
        return min(255, round(v * 255.0).toInt())
    }

    /**
     * Convert a sRGB gamma encoded value to a linear value.
     * @param channelValue the gamma-encoded R, G or B channel in the closed interval of `[0, 255]`.
     * @return the linear rgb value in the closed interval of `[0, 100]`
     */
    private fun sRGB_to_linear(channelValue: Int): Double {
        var v = channelValue / 255.0
        v = if (v > 0.04045) {
            ((v + 0.055) / 1.055).pow(2.4)
        } else {
            v / 12.92
        }
        return v * 100.0
    }
}
