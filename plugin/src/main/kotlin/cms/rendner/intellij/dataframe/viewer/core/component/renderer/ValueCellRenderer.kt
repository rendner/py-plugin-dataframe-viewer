package cms.rendner.intellij.dataframe.viewer.core.component.renderer

import cms.rendner.intellij.dataframe.viewer.core.component.models.StyledValue
import cms.rendner.intellij.dataframe.viewer.core.component.models.TextAlign
import cms.rendner.intellij.dataframe.viewer.core.component.models.Value
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