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
package cms.rendner.intellij.dataframe.viewer.components.renderer.styling

import cms.rendner.intellij.dataframe.viewer.models.TextAlign
import java.awt.Color
import java.awt.Component
import javax.swing.JTable

interface IRendererComponentStyler {
    fun applyStyle(
        component: Component,
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    )
}

data class StyleProperties(
    val textColor: Color? = null,
    val backgroundColor: Color? = null,
    val textAlign: TextAlign? = null,
) {
    fun isEmpty(): Boolean {
        return textColor == null && backgroundColor == null && textAlign == null
    }
}

enum class CellStylingMode {
    Off,
    ColorMap,
    DivergingColorMap,
    HighlightMin,
    HighlightMax,
    HighlightNull,

}

interface ICellStyleComputer {
    fun computeStyling(packedMeta: String): StyleProperties?
}