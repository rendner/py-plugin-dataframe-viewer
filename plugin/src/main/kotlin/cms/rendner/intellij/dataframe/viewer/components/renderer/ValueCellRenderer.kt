/*
 * Copyright 2021 cms.rendner (Daniel Schmidt)
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

import cms.rendner.intellij.dataframe.viewer.models.StyledValue
import cms.rendner.intellij.dataframe.viewer.models.TextAlign
import cms.rendner.intellij.dataframe.viewer.models.Value
import java.awt.Color
import java.awt.Component
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableCellRenderer

class ValueCellRenderer : DefaultTableCellRenderer() {

    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val textValue: String? = if (value is Value) value.text() else null

        border = null
        background = null
        foreground = null
        horizontalAlignment = SwingConstants.LEFT

        super.getTableCellRendererComponent(table, textValue, isSelected, hasFocus, row, column)

        when (value) {
            is StyledValue -> {
                value.styles.let {
                    if (!isSelected) {
                        if (it.backgroundColor != null) {
                            background = it.backgroundColor
                            foreground = it.textColor ?: if (background == Color.BLACK) Color.WHITE else Color.BLACK
                        } else {
                            foreground = it.textColor
                        }
                    }

                    horizontalAlignment = when (it.textAlign) {
                        TextAlign.center -> SwingConstants.CENTER
                        TextAlign.right -> SwingConstants.RIGHT
                        else -> SwingConstants.LEFT
                    }
                }
            }
        }

        return this
    }
}