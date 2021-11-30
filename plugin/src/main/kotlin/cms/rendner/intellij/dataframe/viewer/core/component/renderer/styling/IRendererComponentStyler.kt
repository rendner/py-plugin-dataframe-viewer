package cms.rendner.intellij.dataframe.viewer.core.component.renderer.styling

import java.awt.Component
import javax.swing.JTable

interface IRendererComponentStyler {
    fun applyStyle(
        component: Component,
        table: JTable?, value: Any?,
        isSelected: Boolean, hasFocus: Boolean,
        row: Int, column: Int
    )
}