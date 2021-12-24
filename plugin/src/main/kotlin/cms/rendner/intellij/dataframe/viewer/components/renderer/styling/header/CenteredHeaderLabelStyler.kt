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
package cms.rendner.intellij.dataframe.viewer.components.renderer.styling.header

import cms.rendner.intellij.dataframe.viewer.components.renderer.styling.IRendererComponentStyler
import com.intellij.ui.ExpandableItemsHandler
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.SwingConstants

class CenteredHeaderLabelStyler(
    private val isRowHeader: Boolean = false
) : IRendererComponentStyler {
    override fun applyStyle(
        component: Component,
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ) {
        if (component is JComponent) {
            /*
            An expandable cell is very useful for truncated cells. If a user hovers
            over a cell the missing text is shown in an expanded cell. But this feature
            doesn't seem to work correctly for table headers.

            There are two different issues:

            A) Value Table
            No expansion is displayed for the table header if truncated.

            B) Index Table
            An expansion is displayed if truncated, but dotted text stays dotted.
            It looks like the table header renderer isn't repainted.

            Therefore, disable it completely for all table headers. To have a consistent behavior.
            */
            UIUtil.putClientProperty(component, ExpandableItemsHandler.RENDERER_DISABLED, true)
        }

        if (component is JLabel) {
            component.horizontalAlignment = SwingConstants.CENTER
            component.horizontalTextPosition = SwingConstants.LEFT
            component.verticalAlignment = SwingConstants.BOTTOM
        }

        val setBold = if (isRowHeader) {
            table?.isRowSelected(row) == true
        } else {
            table?.isColumnSelected(column) == true
        }

        val newFontStyle = if (setBold) {
            component.font.style or Font.BOLD
        } else {
            component.font.style and Font.BOLD.inv()
        }

        if (newFontStyle != component.font.style) {
            component.font = component.font.deriveFont(newFontStyle)
        }
    }
}