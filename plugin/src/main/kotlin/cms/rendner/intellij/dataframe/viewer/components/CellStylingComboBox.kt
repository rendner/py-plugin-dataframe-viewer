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
package cms.rendner.intellij.dataframe.viewer.components

import cms.rendner.intellij.dataframe.viewer.components.renderer.styling.CellStylingMode
import com.intellij.openapi.ui.ComboBox
import java.awt.Component
import javax.swing.DefaultComboBoxModel
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer

class ItemRender(private val delegate: ListCellRenderer<in CellStylingMode>): ListCellRenderer<CellStylingMode> {

    override fun getListCellRendererComponent(
        list: JList<out CellStylingMode>?,
        value: CellStylingMode?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        return delegate.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
            if (this is JLabel) {
                when (value) {
                    CellStylingMode.Off -> {
                        this.text = "off"
                        this.toolTipText = "No styling"
                    }
                    CellStylingMode.HighlightMin -> {
                        this.text = "highlight min"
                        this.toolTipText = "Highlight the minimum (in each column) with a different background color"
                    }
                    CellStylingMode.HighlightMax -> {
                        this.text = "highlight max"
                        this.toolTipText = "Highlight the maximum (in each column) with a different background color"
                    }
                    CellStylingMode.HighlightNull -> {
                        this.text = "highlight null"
                        this.toolTipText = "Highlight missing values with a different background color"
                    }
                    CellStylingMode.ColorMap -> {
                        this.text = "cmap"
                        this.toolTipText = "Color the background in a gradient according to the data in each column"
                    }
                    CellStylingMode.DivergingColorMap -> {
                        this.text = "cmap (diverging)"
                        this.toolTipText = "Color the background in a gradient according to the data in each column"
                    }
                    else -> {
                        this.text = null
                        this.toolTipText = null
                    }
                }
            }
        }
    }
}

class CellStylingComboBox: ComboBox<CellStylingMode>(DefaultComboBoxModel(CellStylingMode.entries.toTypedArray())) {

    init {
        setRenderer(ItemRender(getRenderer()))
    }
}