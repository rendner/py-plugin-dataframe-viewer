/*
 * Copyright 2022 cms.rendner (Daniel Schmidt)
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
import com.intellij.ui.ColorUtil
import com.intellij.ui.ExpandableItemsHandler
import com.intellij.util.ui.GraphicsUtil
import java.awt.*
import java.awt.font.FontRenderContext
import java.awt.geom.Rectangle2D
import javax.swing.*

class CenteredHeaderLabelStyler(
    private val isRowHeader: Boolean = false
) : IRendererComponentStyler {

    private val mySortArrowIconRenderer = MyFastSortArrowIconRenderer()

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
            component.putClientProperty(ExpandableItemsHandler.RENDERER_DISABLED, true)
        }

        if (component is JLabel) {
            component.horizontalAlignment = SwingConstants.CENTER
            component.horizontalTextPosition = SwingConstants.LEADING
            component.verticalAlignment = SwingConstants.CENTER

            if (table?.rowSorter != null) {
                component.icon = getPreparedSortArrowIconRenderer(component, table, column)
            }
        }

        val rowOrColumnSelected = if (isRowHeader) {
            table?.isRowSelected(row) == true
        } else {
            table?.isColumnSelected(column) == true
        }

        if (rowOrColumnSelected) {
            try {
                UIManager.getColor("TableHeader.separatorColor")?.let { component.background = it }
            } catch(ignore: NullPointerException) {}
        }
    }

    private fun getPreparedSortArrowIconRenderer(component: Component, table: JTable?, column: Int): Icon {
        table?.rowSorter.let {
            if(it == null) mySortArrowIconRenderer.setValues(component, SortOrder.UNSORTED)
            else {
                val modelIndex = table?.convertColumnIndexToModel(column) ?: column
                val sortKeyIndex = it.sortKeys.indexOfFirst { k -> k.column == modelIndex }
                if (sortKeyIndex == -1) {
                    mySortArrowIconRenderer.setValues(component, SortOrder.UNSORTED)
                } else {
                    mySortArrowIconRenderer.setValues(
                        component,
                        it.sortKeys[sortKeyIndex].sortOrder,
                        "${sortKeyIndex + 1}",
                    )
                }
            }
        }
        return mySortArrowIconRenderer
    }

    private class MyFastSortArrowIconRenderer: Icon {
        private val myInsets = Insets(2, 4, 2, 4)
        private val myGapBetweenTextAndArrow = 2
        private val myFontRenderContext = FontRenderContext(null, true, false)
        private var mySingleDigitTextBounds: Rectangle = Rectangle()
        private var myArrowPolygonSize: Int = 0

        private var mySortOrder = SortOrder.UNSORTED
        private var myPriorityLabel: String = ""
        private var myFont: Font? = null
        private var myCachedLabelFont: Font? = null
        private var myCachedForeground: Color? = null
        private var myCachedBackground: Color? = null
        private var myColor: Color? = null

        private data class IconInfo(val iconWidth: Int, val iconHeight: Int, val arrowPolygons: List<Polygon>)
        private val myIconInfoCache: MutableMap<SortOrder, IconInfo> = mutableMapOf()
        private var myCurrentIconInfo: IconInfo? = null

        fun setValues(component: Component, sortOrder: SortOrder, priorityLabel: String = "") {
            if (priorityLabel.length > 1) throw java.lang.IllegalArgumentException("Only labels with max 1 char are supported.")
            mySortOrder = sortOrder
            myPriorityLabel = priorityLabel
            if (myCachedLabelFont != component.font) {
                myCachedLabelFont = component.font
                myFont = component.font.deriveFont(java.lang.Float.max(11f, (component.font.size * .6).toFloat())).also {
                    mySingleDigitTextBounds = it.getStringBounds("0", myFontRenderContext).toRectangle()
                    myArrowPolygonSize = computeArrowPolygonSize()
                }
                myIconInfoCache.clear()
            }
            if (myCachedForeground != component.foreground && myCachedBackground != component.background) {
                myCachedForeground = component.foreground
                myCachedBackground = component.background
                myColor = ColorUtil.mix(component.background, component.foreground, .6)
            }
            myCurrentIconInfo = getIconInfo(mySortOrder)
        }

        override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
            val g2d = g.create()
            try {
                GraphicsUtil.setupAAPainting(g2d)

                val iconInfo = myCurrentIconInfo!!
                val arrowHeight = iconInfo.iconHeight - myInsets.top - myInsets.bottom
                @Suppress("NAME_SHADOWING") val y = (c.height - arrowHeight) / 2
                @Suppress("NAME_SHADOWING") var x = x + myInsets.left

                g2d.color = myColor
                g2d.font = myFont

                if (mySortOrder != SortOrder.UNSORTED) {
                    val metrics = g.getFontMetrics(myFont)
                    g2d.drawString(myPriorityLabel, x, y + (arrowHeight - metrics.height) / 2 + metrics.ascent)
                }
                // always adjust x - otherwise the arrows jump left/right when toggled
                x += mySingleDigitTextBounds.width + myGapBetweenTextAndArrow

                iconInfo.arrowPolygons.forEach {
                    it.translate(x , y)
                    g2d.fillPolygon(it)
                    it.translate(-x , -y)
                }
            } finally {
                g2d.dispose()
            }
        }

        override fun getIconWidth(): Int {
            return myCurrentIconInfo!!.iconWidth
        }

        override fun getIconHeight(): Int {
            return myCurrentIconInfo!!.iconHeight
        }

        private fun getIconInfo(sortOrder: SortOrder): IconInfo {
            return myIconInfoCache.getOrPut(sortOrder, this::computeIconInfo)
        }

        private fun computeIconInfo(): IconInfo {
            return IconInfo(computeIconWidth(), computeIconHeight(), computeArrowPolygons())
        }

        private fun computeArrowPolygons(): List<Polygon> {
            return when (mySortOrder) {
                SortOrder.UNSORTED -> createUpDownArrow(0, 0, myArrowPolygonSize)
                SortOrder.ASCENDING -> listOf(createUpArrow(0, 0, myArrowPolygonSize))
                SortOrder.DESCENDING -> listOf(createDownArrow(0, 0, myArrowPolygonSize))
            }
        }

        private fun computeIconWidth(): Int {
            return myInsets.left + mySingleDigitTextBounds.width + myGapBetweenTextAndArrow + myArrowPolygonSize + myInsets.right
        }

        private fun computeIconHeight(): Int {
            val arrowSize = if (mySortOrder == SortOrder.UNSORTED) 2 * myArrowPolygonSize else myArrowPolygonSize
            return myInsets.top + arrowSize + myInsets.bottom
        }

        private fun computeArrowPolygonSize(): Int {
            var h = Integer.max(8, (mySingleDigitTextBounds.height * .5).toInt())
            if (h and 0x01 == 1) h += 1 // make it even
            return h
        }

        private fun Rectangle2D.toRectangle(): Rectangle {
            return Rectangle(x.toInt(), y.toInt(), width.toInt(), height.toInt())
        }

        private fun createUpDownArrow(x: Int, y: Int, size: Int): List<Polygon> {
            return listOf(
                createUpArrow(x, y, size),
                createDownArrow(x, y + size, size)
            )
        }

        private fun createUpArrow(x: Int, y: Int, size: Int): Polygon {
            return Polygon().apply {
                addPoint(x, y + size - 2) // bottom left
                addPoint(x + size, y + size - 2) // bottom right
                addPoint(x + size / 2, y + 1) // top
            }
        }

        private fun createDownArrow(x: Int, y: Int, size: Int): Polygon {
            return Polygon().apply {
                addPoint(x, y + 2) // top left
                addPoint(x + size, y + 2) // top right
                addPoint(x + size / 2, y + size - 1) // bottom
            }
        }
    }
}