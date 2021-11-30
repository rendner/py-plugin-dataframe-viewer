package cms.rendner.intellij.dataframe.viewer.core.component.renderer

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