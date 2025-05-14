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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Color

internal class DivergingColorMapperTest {
    @Test
    fun testInterpolation() {
        val minColor = RGB(59, 76, 192)
        val maxColor = RGB(180, 4, 38)
        val mapper = DivergingColorMapper(minColor, maxColor)

        assertThat(mapper.interpolateColors(0.0).background.toRGB()).isEqualTo(RGB(59, 76, 192))
        assertThat(mapper.interpolateColors(0.5).background.toRGB()).isEqualTo(RGB(221, 221, 221))
        assertThat(mapper.interpolateColors(1.0).background.toRGB()).isEqualTo(RGB(180, 4, 38))
    }

    @Test
    fun testReadableForegroundColor() {
        val black = RGB(0, 0, 0)
        val white = RGB(255, 255, 255)
        val mapper = DivergingColorMapper(black, white)

        val start = mapper.interpolateColors(0.0)
        assertThat(start.background.toRGB()).isEqualTo(black)
        assertThat(start.foreground.toRGB()).isEqualTo(white)

        val end = mapper.interpolateColors(1.0)
        assertThat(end.background.toRGB()).isEqualTo(white)
        assertThat(end.foreground.toRGB()).isEqualTo(black)
    }

    private fun Color.toRGB(): RGB {
        return RGB(red, green, blue)
    }
}