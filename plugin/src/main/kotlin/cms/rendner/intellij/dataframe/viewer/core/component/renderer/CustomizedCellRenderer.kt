package cms.rendner.intellij.dataframe.viewer.core.component.renderer

import cms.rendner.intellij.dataframe.viewer.core.component.renderer.styling.IRendererComponentStyler
import cms.rendner.intellij.dataframe.viewer.core.component.renderer.text.ILabelTextProvider
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