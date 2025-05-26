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

import cms.rendner.intellij.dataframe.viewer.components.renderer.styling.CellStylingMode
import cms.rendner.intellij.dataframe.viewer.components.renderer.styling.ICellStyleComputer
import cms.rendner.intellij.dataframe.viewer.components.renderer.styling.StyleProperties
import cms.rendner.intellij.dataframe.viewer.models.TextAlign
import org.beryx.awt.color.ColorFactory
import java.awt.Color

private val Black = RGB(0, 0, 0)
private val White = RGB(255, 255, 255)

private val Cold = RGB(59, 76, 192)
private val Warm = RGB(180, 4, 38)

private const val PartEndMarker = '|'

private const val NON_CALCULABLE_CMAP_VALUE = "-1"

class CellStyleComputer: ICellStyleComputer {
    private val myDivergingColorMapper = DivergingColorMapper(Cold, Warm)
    private val myColorMapper = DivergingColorMapper(White, Warm)
    private var myStylingMode: CellStylingMode = CellStylingMode.DivergingColorMap

    fun setStylingMode(mode: CellStylingMode) {
        myStylingMode = mode
    }

    override fun computeStyling(packedMeta: String): StyleProperties? {

        val colorMapMode = myStylingMode == CellStylingMode.DivergingColorMap || myStylingMode == CellStylingMode.ColorMap
        val parts = extractParts(packedMeta, if (colorMapMode) 2 else 4)

        // -1 => invalid value like inf or nan
        // 0 to 100_000 => valid values
        val cmapValue = parts[0]
        val textAlign = TextAlign.unpackOrConvert(parts[1])
        val backgroundColor = parts[2]
        val textColor = parts[3]

        if (myStylingMode == CellStylingMode.HighlightNull) {
            val isNaN = packedMeta[0] == 'T'
            return if (isNaN) StyleProperties(
                backgroundColor = Color.RED,
                textColor = Color.BLACK,
                textAlign = textAlign
                ) else null
        }
        if (myStylingMode == CellStylingMode.HighlightMin) {
            val isMin = packedMeta[1] == 'T'
            return if (isMin) StyleProperties(
                backgroundColor = Color.YELLOW,
                textColor = Color.BLACK,
                textAlign = textAlign
                ) else null
        }
        if (myStylingMode == CellStylingMode.HighlightMax) {
            val isMax = packedMeta[2] == 'T'
            return if (isMax) StyleProperties(
                backgroundColor = Color.YELLOW,
                textColor = Color.BLACK,
                textAlign = textAlign
                ) else null
        }

        if (colorMapMode) {
            if (cmapValue != null) {
                if (cmapValue == NON_CALCULABLE_CMAP_VALUE) {
                    return StyleProperties(
                        textColor = Color.WHITE,
                        backgroundColor = Color.BLACK,
                        textAlign = textAlign
                    )
                }
                val colorMapper = if (myStylingMode == CellStylingMode.ColorMap) myColorMapper else myDivergingColorMapper
                val colors = colorMapper.interpolateColors(normalize(cmapValue.toInt()))
                return StyleProperties(
                    textColor = colors.foreground,
                    backgroundColor = colors.background,
                    textAlign = textAlign
                )
            }
            return null
        } else {
            if (backgroundColor == null && textColor == null && textAlign == null) return null
            return StyleProperties(
                textColor = convertColorValue(textColor),
                backgroundColor = convertColorValue(backgroundColor),
                textAlign = textAlign
            )
        }
    }

    private fun extractParts(packedMeta: String, numPartsToExtract: Int = 100): Array<String?> {
        var partIndex = 0
        val parts: Array<String?> = arrayOfNulls(4)

        var partStartOffset = 3
        for (i in partStartOffset until packedMeta.length) {
            if (packedMeta[i] == PartEndMarker) {
                if (i > partStartOffset) {
                    // 'i == partStartOffset' marks an empty part
                    parts[partIndex] = packedMeta.substring(partStartOffset, i)
                }
                if (partIndex == numPartsToExtract) {
                    break
                }
                partIndex++
                partStartOffset = i + 1
            }
        }

        return parts
    }

    private fun normalize(v: Int): Double {
        // v: in closed range of [0, 100_000]
        return v / 100_000.0
    }

    private fun convertColorValue(value: String?): Color? {
        return if (value.isNullOrEmpty()) {
            null
        } else {
            // https://dzone.com/articles/create-javaawtcolor-from-string-representation
            // https://github.com/beryx/awt-color-factory/blob/master/README.md
            try {
                ColorFactory.web(value, 1.0).let { if (it.alpha == 0) null else it }
            } catch (ignore: IllegalArgumentException) {
                null
            }
        }
    }
}