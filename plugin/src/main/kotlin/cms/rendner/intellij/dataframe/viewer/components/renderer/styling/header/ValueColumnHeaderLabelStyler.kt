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
package cms.rendner.intellij.dataframe.viewer.components.renderer.styling.header

import cms.rendner.intellij.dataframe.viewer.components.MyValuesTable
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.GraphicsUtil
import java.awt.*
import java.awt.font.FontRenderContext
import java.awt.geom.Rectangle2D
import javax.swing.*
import javax.swing.border.Border
import javax.swing.border.MatteBorder

class ValueColumnHeaderLabelStyler: CommonHeaderLabelStyler(false) {

    private val mySortArrowIconRenderer = MyCachingSortArrowIconRenderer()
    private val myFixedColumnIndicator = MyFixedColumnBorder()

    override fun applyStyle(
        component: Component,
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ) {
        super.applyStyle(component, table, value, isSelected, hasFocus, row, column)

        if (component is JLabel) {
            if (table?.rowSorter != null) {
                component.icon = getPreparedSortArrowIconRenderer(component, table, column)
            }
        }

        paintFixedColumnIndicator(component, table, column)
    }

    private fun paintFixedColumnIndicator(component: Component, table: JTable?, column: Int) {
        if (component is JComponent && table is MyValuesTable) {
            if (table.getColumnResizeBehavior().isFixed(column)) {
                myFixedColumnIndicator.updateColor(component.foreground)
                component.border = myFixedColumnIndicator
            }
        }
    }

    private fun getPreparedSortArrowIconRenderer(component: Component, table: JTable?, column: Int): Icon {
        table?.rowSorter.let {
            if (it == null) mySortArrowIconRenderer.setValues(component, SortOrder.UNSORTED)
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

    private class MyFixedColumnBorder : Border {

        private var myOrgColor: Color? = null
        private val myEmptyInsets = Insets(0, 0, 0, 0)
        private val myDelegate = MutableMatteBorder(1, 0, 0, 0, Color.PINK)

        fun updateColor(color: Color) {
            if (myOrgColor != color) {
                myOrgColor = color
                myDelegate.updateColor(ColorUtil.withAlpha(color, .7))
            }
        }

        override fun paintBorder(c: Component?, g: Graphics?, x: Int, y: Int, width: Int, height: Int) {
            // make it 1 pixel shorter to not paint over the vertical grid lines between the columns
            myDelegate.paintBorder(c, g, x, y, width - 1, height)
        }

        override fun getBorderInsets(c: Component?): Insets {
            // exclude border from component height
            return myEmptyInsets
        }

        override fun isBorderOpaque(): Boolean {
            return myDelegate.isBorderOpaque
        }

        private class MutableMatteBorder(top: Int, left: Int, bottom: Int, right: Int, matteColor: Color) :
            MatteBorder(top, left, bottom, right, matteColor) {
            fun updateColor(color: Color) {
                this.color = color
            }
        }
    }

    private class MyCachingSortArrowIconRenderer : Icon {
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

        private data class IconInfo(
            val iconWidth: Int,
            val iconHeight: Int,
            val arrowPolygons: List<Polygon>,
            val color: Color
        )

        private val myIconInfoCache: MutableMap<SortOrder, IconInfo> = mutableMapOf()
        private var myCurrentIconInfo: IconInfo? = null

        fun setValues(component: Component, sortOrder: SortOrder, priorityLabel: String = "") {
            if (priorityLabel.length > 1) throw java.lang.IllegalArgumentException("Only labels with max 1 char are supported.")
            mySortOrder = sortOrder
            myPriorityLabel = priorityLabel
            var clearIconInfo = false
            if (myCachedLabelFont != component.font) {
                clearIconInfo = true
                myCachedLabelFont = component.font
                myFont =
                    component.font.deriveFont(java.lang.Float.max(11f, (component.font.size * .6).toFloat())).also {
                        mySingleDigitTextBounds = it.getStringBounds("0", myFontRenderContext).toRectangle()
                        myArrowPolygonSize = computeArrowPolygonSize()
                    }
            }
            if (myCachedForeground != component.foreground && myCachedBackground != component.background) {
                clearIconInfo = true
                myCachedForeground = component.foreground
                myCachedBackground = component.background
            }
            if (clearIconInfo) myIconInfoCache.clear()
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

                g2d.color = iconInfo.color
                g2d.font = myFont

                if (mySortOrder != SortOrder.UNSORTED) {
                    val metrics = g.getFontMetrics(myFont)
                    g2d.drawString(myPriorityLabel, x, y + (arrowHeight - metrics.height) / 2 + metrics.ascent)
                }
                // always adjust x - otherwise the arrows jump left/right when toggled
                x += mySingleDigitTextBounds.width + myGapBetweenTextAndArrow

                iconInfo.arrowPolygons.forEach {
                    it.translate(x, y)
                    g2d.fillPolygon(it)
                    it.translate(-x, -y)
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
            return IconInfo(
                computeIconWidth(),
                computeIconHeight(),
                computeArrowPolygons(),
                ColorUtil.mix(
                    myCachedBackground!!,
                    myCachedForeground!!,
                    if (mySortOrder == SortOrder.UNSORTED) .2 else .7,
                ),
            )
        }

        private fun computeArrowPolygons(): List<Polygon> {
            return when (mySortOrder) {
                SortOrder.UNSORTED -> createUpDownArrow(0, 0, myArrowPolygonSize)
                SortOrder.ASCENDING -> listOf(createUpArrow(0, myArrowPolygonSize / 2, myArrowPolygonSize))
                SortOrder.DESCENDING -> listOf(createDownArrow(0, myArrowPolygonSize / 2, myArrowPolygonSize))
            }
        }

        private fun computeIconWidth(): Int {
            return myInsets.left + mySingleDigitTextBounds.width + myGapBetweenTextAndArrow + myArrowPolygonSize + myInsets.right
        }

        private fun computeIconHeight(): Int {
            // always use height of the double arrow to prevent jumping header height
            return myInsets.top + (2 * myArrowPolygonSize) + myInsets.bottom
        }

        private fun computeArrowPolygonSize(): Int {
            var h = Integer.max(8, (mySingleDigitTextBounds.height * .7).toInt())
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