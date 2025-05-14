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
import cms.rendner.intellij.dataframe.viewer.components.renderer.styling.StyleProperties
import cms.rendner.intellij.dataframe.viewer.models.TextAlign
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Color

private data class CellMeta(
    val isNaN: Boolean = false,
    val isMin: Boolean = false,
    val isMax: Boolean = false,
    val cmapValue: Int? = null,
    val textColor: String? = null,
    val textAlign: String? = null,
    val backgroundColor: String? = null,
) {
    fun pack(): String {
        // note: packed order has to be in sync with the python part
        return StringBuilder().also {
            appendFlag(it, isNaN)
            appendFlag(it, isMin)
            appendFlag(it, isMax)
            appendOptionalPart(it, cmapValue?.toString())
            appendOptionalPart(it, textAlign)
            appendOptionalPart(it, backgroundColor)
            appendOptionalPart(it, textColor)
        }.toString()
    }

    private fun appendFlag(sb: StringBuilder, flag: Boolean) {
        sb.append(if (flag) "T" else "F")
    }

    private fun appendOptionalPart(sb: StringBuilder, part: String?) {
        sb.append("${if (part.isNullOrEmpty()) "" else part}|")
    }
}

internal class CellStyleComputerTest {
    @Test
    fun testStylingModeOff() {
        val computer = CellStyleComputer().apply { setStylingMode(CellStylingMode.Off) }

        val meta = CellMeta(
            backgroundColor = "pink",
            textColor = "white",
            textAlign = "left",
        )

        val expectedStyling = StyleProperties(
            backgroundColor = Color(255, 192, 203),
            textColor = Color.WHITE,
            textAlign = TextAlign.left,
        )

        assertThat(computer.computeStyling(meta.pack()))
            .isEqualTo(expectedStyling)

        assertThat(computer.computeStyling(meta.copy(cmapValue = 0).pack()))
            .isEqualTo(expectedStyling)

        assertThat(computer.computeStyling(meta.copy(isMin = true).pack()))
            .isEqualTo(expectedStyling)

        assertThat(computer.computeStyling(meta.copy(isMax = true).pack()))
            .isEqualTo(expectedStyling)

        assertThat(computer.computeStyling(meta.copy(isNaN = true).pack()))
            .isEqualTo(expectedStyling)
    }

    @Test
    fun testStylingModeColorMap() {
        val computer = CellStyleComputer().apply { setStylingMode(CellStylingMode.ColorMap) }

        val meta = CellMeta(cmapValue = 0)

        val expectedStyling = StyleProperties(
            backgroundColor = Color.WHITE,
            textColor = Color.BLACK,
        )

        assertThat(computer.computeStyling(meta.pack()))
            .isEqualTo(expectedStyling)

        assertThat(computer.computeStyling(meta.copy(
            backgroundColor = "pink",
            textColor = "white",
            textAlign = "left",
        ).pack()))
            .isEqualTo(expectedStyling.copy(textAlign = TextAlign.left))

        assertThat(computer.computeStyling(meta.copy(isMin = true).pack()))
            .isEqualTo(expectedStyling)

        assertThat(computer.computeStyling(meta.copy(isMax = true).pack()))
            .isEqualTo(expectedStyling)

        assertThat(computer.computeStyling(meta.copy(isNaN = true).pack()))
            .isEqualTo(expectedStyling)
    }

    @Test
    fun testStylingModeDivergingColorMap() {
        val computer = CellStyleComputer().apply { setStylingMode(CellStylingMode.DivergingColorMap) }

        val meta = CellMeta(cmapValue = 0)

        val expectedStyling = StyleProperties(
            backgroundColor = Color(59,76, 192),
            textColor = Color.WHITE,
        )

        assertThat(computer.computeStyling(meta.pack()))
            .isEqualTo(expectedStyling)

        assertThat(computer.computeStyling(meta.copy(
            backgroundColor = "pink",
            textColor = "white",
            textAlign = "left",
        ).pack()))
            .isEqualTo(expectedStyling.copy(textAlign = TextAlign.left))

        assertThat(computer.computeStyling(meta.copy(isMin = true).pack()))
            .isEqualTo(expectedStyling)

        assertThat(computer.computeStyling(meta.copy(isMax = true).pack()))
            .isEqualTo(expectedStyling)

        assertThat(computer.computeStyling(meta.copy(isNaN = true).pack()))
            .isEqualTo(expectedStyling)
    }

    @Test
    fun testStylingModeHighlightNull() {
        val computer = CellStyleComputer().apply { setStylingMode(CellStylingMode.HighlightNull) }

        val meta = CellMeta(isNaN = true, cmapValue = -1)

        val expectedStyling = StyleProperties(
            backgroundColor = Color.RED,
            textColor = Color.BLACK,
        )

        assertThat(computer.computeStyling(meta.pack()))
            .isEqualTo(expectedStyling)

        assertThat(computer.computeStyling(meta.copy(
            backgroundColor = "pink",
            textColor = "white",
            textAlign = "left",
        ).pack()))
            .isEqualTo(expectedStyling.copy(textAlign = TextAlign.left))

        assertThat(computer.computeStyling(meta.copy(isMin = true).pack()))
            .isEqualTo(expectedStyling)

        assertThat(computer.computeStyling(meta.copy(isMax = true).pack()))
            .isEqualTo(expectedStyling)

        assertThat(computer.computeStyling(meta.copy(isNaN = true).pack()))
            .isEqualTo(expectedStyling)
    }

    @Test
    fun testStylingModeHighlightMin() {
        val computer = CellStyleComputer().apply { setStylingMode(CellStylingMode.HighlightMin) }

        val meta = CellMeta(isMin = true, cmapValue = 0)

        val expectedStyling = StyleProperties(
            backgroundColor = Color.YELLOW,
            textColor = Color.BLACK,
        )

        assertThat(computer.computeStyling(meta.pack()))
            .isEqualTo(expectedStyling)

        assertThat(computer.computeStyling(meta.copy(
            backgroundColor = "pink",
            textColor = "white",
            textAlign = "left",
        ).pack()))
            .isEqualTo(expectedStyling.copy(textAlign = TextAlign.left))

        assertThat(computer.computeStyling(meta.copy(isMin = true).pack()))
            .isEqualTo(expectedStyling)

        assertThat(computer.computeStyling(meta.copy(isMax = true).pack()))
            .isEqualTo(expectedStyling)

        assertThat(computer.computeStyling(meta.copy(isNaN = true).pack()))
            .isEqualTo(expectedStyling)
    }

    @Test
    fun testStylingModeHighlightMax() {
        val computer = CellStyleComputer().apply { setStylingMode(CellStylingMode.HighlightMax) }

        val meta = CellMeta(isMax = true, cmapValue = 0)

        val expectedStyling = StyleProperties(
            backgroundColor = Color.YELLOW,
            textColor = Color.BLACK,
        )

        assertThat(computer.computeStyling(meta.pack()))
            .isEqualTo(expectedStyling)

        assertThat(computer.computeStyling(meta.copy(
            backgroundColor = "pink",
            textColor = "white",
            textAlign = "left",
        ).pack()))
            .isEqualTo(expectedStyling.copy(textAlign = TextAlign.left))

        assertThat(computer.computeStyling(meta.copy(isMin = true).pack()))
            .isEqualTo(expectedStyling)

        assertThat(computer.computeStyling(meta.copy(isMax = true).pack()))
            .isEqualTo(expectedStyling)

        assertThat(computer.computeStyling(meta.copy(isNaN = true).pack()))
            .isEqualTo(expectedStyling)
    }
}