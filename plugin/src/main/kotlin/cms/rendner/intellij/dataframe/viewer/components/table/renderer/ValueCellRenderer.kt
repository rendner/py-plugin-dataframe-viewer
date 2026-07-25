/*
 * Copyright 2021-2026 cms.rendner (Daniel Schmidt)
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
package cms.rendner.intellij.dataframe.viewer.components.table.renderer

import cms.rendner.intellij.dataframe.viewer.components.table.MyValuesTable
import cms.rendner.intellij.dataframe.viewer.components.table.renderer.styling.ICellStyleComputer
import cms.rendner.intellij.dataframe.viewer.components.table.models.TextAlign
import java.awt.Color
import java.awt.Component
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableCellRenderer

class ValueCellRenderer: DefaultTableCellRenderer() {

    var cellStyleComputer: ICellStyleComputer? = null

    private var myIsOwnModification: Boolean = false

    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        myIsOwnModification = true

        try {
            val textValue: String? = value?.toString()

            border = null
            background = null
            foreground = null
            horizontalAlignment = SwingConstants.LEFT

            super.getTableCellRendererComponent(table, textValue, isSelected, hasFocus, row, column)

            if (table !is MyValuesTable || cellStyleComputer == null) {
                return this
            }

            table.model?.let { model ->
                val modelRowIndex = table.convertRowIndexToModel(row)
                val modelColumnIndex = table.convertColumnIndexToModel(column)

                var textAlign = model.getColumnTextAlignAt(modelColumnIndex)

                model.getCellMetaAt(modelRowIndex, modelColumnIndex)?.let { meta ->
                    cellStyleComputer?.computeStyling(meta)?.let { styling ->
                        if (styling.textAlign != null) {
                            textAlign = styling.textAlign
                        }

                        if (!isSelected) {
                            if (styling.backgroundColor != null) {
                                background = styling.backgroundColor
                                foreground =
                                    styling.textColor ?: if (background == Color.BLACK) Color.WHITE else Color.BLACK
                            } else {
                                foreground = styling.textColor
                            }
                        }
                    }
                }

            horizontalAlignment = when (textAlign) {
                TextAlign.CENTER -> SwingConstants.CENTER
                TextAlign.RIGHT -> SwingConstants.RIGHT
                else -> SwingConstants.LEFT
            }
        }

            return this
        } finally {
            myIsOwnModification = false
        }
    }

    override fun setBackground(c: Color?) {
        if (myIsOwnModification) {
            super.setBackground(c)
        } else {
            if (c != null) {
                // We expect that this is the hover overlay.
                //
                // The renderer supports custom background and foreground colors.
                // Overlays are blend into the defined background color, otherwise the custom colors
                // would be completely replaced by the new color.
                //
                // This approach does not solve all cases. For example when having a white overlay
                // and a white background.
                if (background != null) {
                    super.setBackground(blendColors(background, c))
                } else {
                    super.setBackground(c)
                }

                super.setForeground(if (isDark(background)) Color.WHITE else Color.BLACK)
            }
        }
    }

    private fun blendColors(base: Color, overlay: Color): Color {
        val alpha = overlay.alpha / 255f
        val r = (base.red * (1 - alpha) + overlay.red * alpha).toInt()
        val g = (base.green * (1 - alpha) + overlay.green * alpha).toInt()
        val b = (base.blue * (1 - alpha) + overlay.blue * alpha).toInt()
        return Color(r, g, b)
    }

    /**
     * Calculates the perceived brightness of a color using standard ITU-R BT.601 formula.
     * Returns true if the color is considered "dark".
     */
    private fun isDark(color: Color): Boolean {
        val brightness = (color.red * 299 + color.green * 587 + color.blue * 114) / 1000.0
        return brightness < 128
    }
}
