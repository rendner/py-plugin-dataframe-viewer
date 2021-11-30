package cms.rendner.intellij.dataframe.viewer.core.component.renderer.text.header

import cms.rendner.intellij.dataframe.viewer.core.component.models.ITableDataModel
import cms.rendner.intellij.dataframe.viewer.core.component.renderer.text.ILabelTextProvider
import javax.swing.JTable

class LegendHeaderTextProvider : ILabelTextProvider {
    override fun getLabel(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): String? {

        return table?.model?.let { model ->
            when (model) {
                is ITableDataModel -> model.getLegendHeader().text()
                else -> null
            }
        }
    }
}