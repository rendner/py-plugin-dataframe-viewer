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
import cms.rendner.intellij.dataframe.viewer.components.renderer.styling.header.CommonHeaderLabelStyler
import cms.rendner.intellij.dataframe.viewer.components.renderer.styling.header.ValueColumnHeaderLabelStyler
import cms.rendner.intellij.dataframe.viewer.models.IHeaderLabel
import cms.rendner.intellij.dataframe.viewer.models.Value
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*
import javax.swing.table.TableCellRenderer

abstract class AbstractHeaderRenderer: TableCellRenderer {
    protected fun getLabel(value: Any?): String
    {
        return when (value) {
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

class IndexRowHeaderRenderer(private var delegate: TableCellRenderer): AbstractHeaderRenderer() {
    private val myStyler = CommonHeaderLabelStyler(true)

    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val text = getLabel(value)
        return delegate.getTableCellRendererComponent(table, text, isSelected, hasFocus, row, column).also {
            myStyler.applyStyle(it, table, value, isSelected, hasFocus, row, column)
        }
    }
}

class ValueColumnHeaderRenderer(private var delegate: TableCellRenderer): AbstractHeaderRenderer() {
    private val myStyler = ValueColumnHeaderLabelStyler()

    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        return delegate.getTableCellRendererComponent(table, getLabel(value), isSelected, hasFocus, row, column).also {
            myStyler.applyStyle(it, table, value, isSelected, hasFocus, row, column)
        }
    }
}

private class DTypeRenderer(private var delegate: TableCellRenderer): AbstractHeaderRenderer() {
    private val myStyler = CommonHeaderLabelStyler(false)
    private val leftPadding = JBUI.Borders.emptyLeft(4)
    private var myCachedComponentFont: Font? = null

    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        return delegate.getTableCellRendererComponent(table, getLabel(table, column), isSelected, hasFocus, row, column).also {
            myStyler.applyStyle(it, table, value, isSelected, hasFocus, row, column)
            if (it is JLabel) {
                it.icon = null
                it.border = leftPadding
                if (myCachedComponentFont != it.font) {
                    myCachedComponentFont = it.font
                    it.font = it.font.deriveFont(it.font.size - 1)
                }
                it.horizontalAlignment = SwingConstants.LEFT
                it.foreground = ColorUtil.mix(it.background, it.foreground, 0.3)
            }
        }
    }

    private fun getLabel(table: JTable?, column: Int): String {
        // cellRenderer with an empty label have a preferred size of (width=0, height=0).
        // To prevent this empty labels are replaced with a non-empty string.
        val fallback = " "
        return if (table is MyValuesTable) {
            table.model?.getColumnDtypeAt(table.convertColumnIndexToModel(column)) ?: fallback
        } else fallback
    }
}

class ValueColumnHeaderRendererWithDtype(
    labelRenderer: TableCellRenderer,
    dtypeRenderer: TableCellRenderer,
) : AbstractHeaderRenderer() {
    private val myLabelRenderer = ValueColumnHeaderRenderer(labelRenderer)
    private val myDtypeRenderer = DTypeRenderer(dtypeRenderer)

    private val myContainer = JPanel(GridBagLayout()).apply {
        border = BorderFactory.createEmptyBorder(0, 0, 0, 1)
    }
    private val myLayoutConstraint = GridBagConstraints().apply {
        fill = GridBagConstraints.HORIZONTAL
        weightx = 1.0
    }

    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {

        val labelComp = myLabelRenderer.getTableCellRendererComponent(table, getLabel(value), isSelected, hasFocus, row, column)
        val dtypeComp = myDtypeRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

        myContainer.removeAll()
        try {
            UIManager.getColor("TableHeader.separatorColor")?.let { myContainer.background = it }
        } catch (ignore: NullPointerException) { }
        myLayoutConstraint.gridy = 0

        myContainer.add(labelComp, myLayoutConstraint)
        myLayoutConstraint.gridy++
        myContainer.add(dtypeComp, myLayoutConstraint)

        return myContainer
    }
}