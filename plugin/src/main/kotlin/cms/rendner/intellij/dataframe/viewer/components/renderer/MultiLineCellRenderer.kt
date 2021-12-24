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

import java.awt.Component
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer


class MultiLineCellRenderer(
    private val cellRenderers: List<TableCellRenderer>
) : DefaultTableCellRenderer() {

    init {
        removeAll()
        layout = GridBagLayout()
    }

    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {

        removeAll()

        val gbConstraint = GridBagConstraints()
        gbConstraint.fill = GridBagConstraints.HORIZONTAL
        gbConstraint.weightx = 1.0
        gbConstraint.gridx = 0
        gbConstraint.gridy = 0

        cellRenderers.forEach { renderer ->
            renderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column).also {
                add(it, gbConstraint)
                gbConstraint.gridy++
            }
        }

        return this
    }

    override fun validate() {
        layout.layoutContainer(this)
    }

    override fun getPreferredSize(): Dimension {
        return layout.preferredLayoutSize(this)
    }
}