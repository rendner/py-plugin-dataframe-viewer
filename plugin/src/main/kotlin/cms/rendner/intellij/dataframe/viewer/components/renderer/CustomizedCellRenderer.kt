/*
 * Copyright 2023 cms.rendner (Daniel Schmidt)
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
import cms.rendner.intellij.dataframe.viewer.models.IHeaderLabel
import cms.rendner.intellij.dataframe.viewer.models.LeveledHeaderLabel
import cms.rendner.intellij.dataframe.viewer.models.Value
import java.awt.Component
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

class CustomizedCellRenderer(
    private var cellRenderer: TableCellRenderer,
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
        return cellRenderer.getTableCellRendererComponent(table, getLabel(value), isSelected, hasFocus, row, column).also {
            componentStyler.applyStyle(it, table, value, isSelected, hasFocus, row, column)
        }
    }

    private fun getLabel(value: Any?): String {
        return when (value) {
            is LeveledHeaderLabel -> value.text()
            is IHeaderLabel -> value.text()
            is Value -> value.text()
            is String -> value
            else -> null
        }.let {
            // cellRenderer with an empty label have a preferred size of (width=0, height=0).
            // To prevent this empty labels are replaced with a non-empty string.
            if (it.isNullOrEmpty()) " " else it
        }
    }
}