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
package cms.rendner.intellij.dataframe.viewer.components.renderer

import cms.rendner.intellij.dataframe.viewer.components.MyValuesTable
import cms.rendner.intellij.dataframe.viewer.components.renderer.styling.ICellStyleComputer
import cms.rendner.intellij.dataframe.viewer.models.TextAlign
import java.awt.Color
import java.awt.Component
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableCellRenderer

class ValueCellRenderer(
    private val cellStyleComputer: ICellStyleComputer? = null,
): DefaultTableCellRenderer() {

    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val textValue: String? = value?.toString()

        border = null
        background = null
        foreground = null
        horizontalAlignment = SwingConstants.LEFT

        super.getTableCellRendererComponent(table, textValue, isSelected, hasFocus, row, column)

        if (table !is MyValuesTable || cellStyleComputer == null) return this

        table.model?.let { model ->
            val modelRowIndex = table.convertRowIndexToModel(row)
            val modelColumnIndex = table.convertColumnIndexToModel(column)

            var textAlign = model.getColumnTextAlignAt(modelColumnIndex)

            model.getCellMetaAt(modelRowIndex, modelColumnIndex)?.let { meta ->
                cellStyleComputer.computeStyling(meta)?.let { styling ->
                    if (styling.textAlign != null) {
                        textAlign = styling.textAlign
                    }
                    if (!isSelected) {
                        if (styling.backgroundColor != null) {
                            background = styling.backgroundColor
                            foreground = styling.textColor ?: if (background == Color.BLACK) Color.WHITE else Color.BLACK
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
    }
}