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

import cms.rendner.intellij.dataframe.viewer.components.renderer.styling.IRendererComponentStyler
import cms.rendner.intellij.dataframe.viewer.components.renderer.text.ILabelTextProvider
import java.awt.Component
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

private const val NON_EMPTY_STRING = " "

class CustomizedCellRenderer(
    private var cellRenderer: TableCellRenderer,
    private var labelTextProvider: ILabelTextProvider,
    private var componentStyler: IRendererComponentStyler
) : TableCellRenderer {

    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val label = labelTextProvider.getLabel(table, value, isSelected, hasFocus, row, column)
        // cellRenderer with an empty label have a preferred size of (width=0, height=0).
        // To prevent this empty labels are replaced with a non-empty string.
        val cellValue = if (label.isNullOrEmpty()) NON_EMPTY_STRING else label
        return cellRenderer.getTableCellRendererComponent(table, cellValue, isSelected, hasFocus, row, column).also {
            componentStyler.applyStyle(it, table, value, isSelected, hasFocus, row, column)
        }
    }
}