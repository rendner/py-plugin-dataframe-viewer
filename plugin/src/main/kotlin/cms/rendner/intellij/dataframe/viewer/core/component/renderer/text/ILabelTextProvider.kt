package cms.rendner.intellij.dataframe.viewer.core.component.renderer.text

import javax.swing.JTable

interface ILabelTextProvider {

    fun getLabel(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): String?
}