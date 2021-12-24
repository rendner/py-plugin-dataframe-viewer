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
package cms.rendner.intellij.dataframe.viewer.components

import cms.rendner.intellij.dataframe.viewer.components.renderer.CustomizedCellRenderer
import cms.rendner.intellij.dataframe.viewer.components.renderer.MultiLineCellRenderer
import cms.rendner.intellij.dataframe.viewer.components.renderer.ValueCellRenderer
import cms.rendner.intellij.dataframe.viewer.components.renderer.styling.header.CenteredHeaderLabelStyler
import cms.rendner.intellij.dataframe.viewer.components.renderer.text.header.DefaultHeaderTextProvider
import cms.rendner.intellij.dataframe.viewer.components.renderer.text.header.LegendHeaderTextProvider
import cms.rendner.intellij.dataframe.viewer.components.renderer.text.header.LeveledDisplayMode
import cms.rendner.intellij.dataframe.viewer.models.*
import cms.rendner.intellij.dataframe.viewer.models.chunked.events.ChunkTableModelEvent
import com.intellij.ui.ColorUtil
import com.intellij.ui.table.JBTable
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.TableModelEvent
import javax.swing.table.*
import kotlin.math.max
import kotlin.math.min

// see styleguide: https://jetbrains.github.io/ui/controls/table/
private const val MIN_COLUMN_WIDTH = 65
private const val MIN_TABLE_WIDTH = 350

private const val MAX_AUTO_EXPAND_COLUMN_WIDTH = 350

// https://stackoverflow.com/questions/1434933/jtable-row-header-implementation
// similar to "com.jetbrains.python.debugger.array::JBTableWithRowHeader"
class DataFrameTable : JScrollPane() {

    private val myIndexTable: MyIndexTable
    private val myValueTable: MyValueTable = MyValueTable()

    init {
        myIndexTable = MyIndexTable(
            myValueTable,
            max(MIN_COLUMN_WIDTH, MIN_TABLE_WIDTH - MIN_COLUMN_WIDTH)
        )

        setViewportView(myValueTable)
        minimumSize = Dimension(MIN_TABLE_WIDTH, 250)
    }

    fun setDataFrameModel(dateFrameModel: IDataFrameModel) {
        myValueTable.setModel(dateFrameModel.getValueDataModel())
        myValueTable.model?.let {
            if (it.shouldHideHeaders()) {
                setColumnHeaderView(null)
            } else {
                setColumnHeaderView(myValueTable.tableHeader)
            }
        }

        myIndexTable.setModel(dateFrameModel.getIndexDataModel())
        myIndexTable.model?.let {
            if (it.columnCount == 0) {
                setRowHeaderView(null)
                setCorner(ScrollPaneConstants.UPPER_LEFT_CORNER, null)
            } else {
                setRowHeaderView(myIndexTable)
                if (!it.shouldHideHeaders()) {
                    setCorner(ScrollPaneConstants.UPPER_LEFT_CORNER, myIndexTable.tableHeader)
                }
            }
        }
    }

    fun getRowHeight(): Int {
        return myValueTable.rowHeight
    }
}

private class MyValueTable : MyTable<ITableValueDataModel>() {
    /**
    This flag is needed because "setModel" is called in the JTable constructor before
    the private property "myEmptyText" from [JBTable] is initialized. The getter of "emptyText" does
    a "not null"-check before returning the property. Therefore, we have to use this workaround
    to identify if we can access the "emptyText" property.
     */
    private val emptyTextIsInitialized: Boolean = true

    init {
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)

        setDefaultRenderer(Object::class.java, ValueCellRenderer())

        getColumnModel().selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                /**
                required to render the selected column with a bold label - see [CenteredHeaderLabelStyler]
                 */
                tableHeader?.repaint()
            }
        }
    }

    override fun setModel(tableModel: TableModel) {
        super.setModel(tableModel)

        if (emptyTextIsInitialized) {
            emptyText.text = if (tableModel.rowCount > 0) {
                "loading DataFrame"
            } else {
                "DataFrame is empty"
            }
        }
    }

    override fun getScrollableTracksViewportWidth(): Boolean {
        return preferredSize.width < parent.width
    }

    override fun createLeveledTableHeaderRenderer(): TableCellRenderer {
        /**
         Note:
         each [CustomizedCellRenderer] needs an own instance of a defaultHeaderRenderer (can't be shared)
         */
        return MultiLineCellRenderer(
            listOf(
                CustomizedCellRenderer(
                    createDefaultHeaderRenderer(),
                    DefaultHeaderTextProvider(LeveledDisplayMode.LEADING_LEVELS_ONLY),
                    CenteredHeaderLabelStyler()
                ),
                CustomizedCellRenderer(
                    createDefaultHeaderRenderer(),
                    DefaultHeaderTextProvider(LeveledDisplayMode.LAST_LEVEL_ONLY),
                    CenteredHeaderLabelStyler()
                )
            )
        )
    }

    override fun createTableHeaderRenderer(): TableCellRenderer {
        return CustomizedCellRenderer(
            createDefaultHeaderRenderer(),
            DefaultHeaderTextProvider(),
            CenteredHeaderLabelStyler()
        )
    }

    override fun createDefaultDataModel(): ITableValueDataModel {
        return EmptyValueModel()
    }
}

private class MyIndexTable(
    private val mainTable: MyValueTable,
    private val maxColumnWidth: Int
) : MyTable<ITableIndexDataModel>() {

    init {
        isFocusable = false
        setSelectionModel(mainTable.selectionModel)
        rowHeight = mainTable.rowHeight
        autoResizeMode = JTable.AUTO_RESIZE_OFF

        setDefaultRenderer(
            Object::class.java,
            CustomizedCellRenderer(
                createDefaultHeaderRenderer(),
                DefaultHeaderTextProvider(),
                CenteredHeaderLabelStyler(true)
            )
        )
        adjustPreferredScrollableViewportSize()
    }

    override fun updateUI() {
        super.updateUI()
        border = BorderFactory.createMatteBorder(0, 0, 0, 1, colorFromUI("TableHeader.separatorColor", Color.BLACK))
        tableHeader?.border = border
    }

    override fun columnMarginChanged(e: ChangeEvent?) {
        super.columnMarginChanged(e)
        adjustPreferredScrollableViewportSize()
    }

    override fun setModel(tableModel: TableModel) {
        super.setModel(tableModel)
        adjustPreferredScrollableViewportSize()
    }

    override fun createDefaultDataModel(): ITableIndexDataModel {
        return EmptyIndexModel()
    }

    override fun getRowHeight(row: Int): Int {
        return mainTable.getRowHeight(row)
    }

    override fun getRowHeight(): Int {
        return mainTable.rowHeight
    }

    override fun addColumn(column: TableColumn) {
        column.maxWidth = maxColumnWidth
        super.addColumn(column)
    }

    override fun addMouseListener(l: MouseListener?) {
        // do nothing
        // table should not clickable but tooltip should work
    }

    override fun createLeveledTableHeaderRenderer(): TableCellRenderer {
        /**
        Note:
        each [CustomizedCellRenderer] needs an own instance of a defaultHeaderRenderer (can't be shared)
         */
        return MultiLineCellRenderer(
            listOf(
                CustomizedCellRenderer(
                    createDefaultHeaderRenderer(),
                    LegendHeaderTextProvider(),
                    CenteredHeaderLabelStyler()
                ),
                CustomizedCellRenderer(
                    createDefaultHeaderRenderer(),
                    DefaultHeaderTextProvider(),
                    CenteredHeaderLabelStyler()
                )
            )
        )
    }

    override fun createTableHeaderRenderer(): TableCellRenderer {
        return CustomizedCellRenderer(
            createDefaultHeaderRenderer(),
            DefaultHeaderTextProvider(),
            CenteredHeaderLabelStyler()
        )
    }

    private fun adjustPreferredScrollableViewportSize() {
        preferredScrollableViewportSize = preferredSize
    }

}

abstract class MyTable<M : ITableDataModel> : JBTable(null) {
    init {
        emptyText.text = ""
        cellSelectionEnabled = false
        rowSelectionAllowed = false

        rowSorter = null
        tableHeader?.reorderingAllowed = false
        updateDefaultHeaderRenderer()

        this.setMaxItemsForSizeCalculation(0)
    }

    override fun addColumn(column: TableColumn) {
        column.minWidth = MIN_COLUMN_WIDTH
        super.addColumn(column)
    }

    override fun setModel(tableModel: TableModel) {
        if (tableModel !is ITableDataModel) throw IllegalArgumentException("The model has to implement ITableDataModel.")
        val oldModel = model
        super.setModel(tableModel)
        if (oldModel?.isLeveled() != tableModel.isLeveled()) {
            updateDefaultHeaderRenderer()
        }
    }

    override fun getModel(): M? {
        @Suppress("UNCHECKED_CAST")
        return super.getModel() as M?
    }

    override fun createDefaultTableHeader(): JTableHeader {
        return MyTableHeader()
    }

    override fun isCellEditable(row: Int, column: Int): Boolean {
        return false
    }

    override fun onTableChanged(event: TableModelEvent) {
        if (event is ChunkTableModelEvent) {
            val payload = event.payload
            if (ChunkTableModelEvent.UpdateType.HEADER_LABELS == payload.type) {
                if (tableHeader == null) return
                model?.let { model ->
                    val columnModel = tableHeader.columnModel
                    for (columnIndex in payload.firstColumn..payload.lastColumn) {
                        columnModel.getColumn(columnIndex).let {
                            it.headerValue = model.getColumnName(it.modelIndex)
                        }
                    }
                    tableHeader.repaint()
                }
            }
        } else {
            super.onTableChanged(event)
        }
    }

    override fun prepareRenderer(renderer: TableCellRenderer, row: Int, column: Int): Component {
        val component = super.prepareRenderer(renderer, row, column)
        tableHeader?.let {
            val resizingColumn = it.resizingColumn
            val adjustable = (tableHeader as MyTable<*>.MyTableHeader).isAdjustableColumn(column)
            if (resizingColumn == null && adjustable) {
                val componentWidth = component.preferredSize.width
                val tableColumn = columnModel.getColumn(column)
                val headerWidth = it.defaultRenderer.getTableCellRendererComponent(
                    this,
                    tableColumn.headerValue,
                    false,
                    false,
                    -1,
                    column
                ).preferredSize.width + 4
                val newWidth: Int = max(componentWidth, headerWidth) + 2 * intercellSpacing.width
                tableColumn.preferredWidth =
                    min(max(newWidth, tableColumn.preferredWidth), MAX_AUTO_EXPAND_COLUMN_WIDTH)
            }
        }
        return component
    }

    protected abstract fun createLeveledTableHeaderRenderer(): TableCellRenderer

    protected abstract fun createTableHeaderRenderer(): TableCellRenderer

    protected fun createDefaultHeaderRenderer(): TableCellRenderer {
        return (getTableHeader() as MyTable<*>.MyTableHeader).createDefaultRenderer()
    }

    private fun updateDefaultHeaderRenderer() {
        tableHeader?.let {
            it.defaultRenderer = if (model?.isLeveled() == true) {
                // todo: __prio_2__ maybe part of the next release
                //createLeveledTableHeaderRenderer()
                createTableHeaderRenderer()
            } else {
                createTableHeaderRenderer()
            }
            it.resizeAndRepaint()
        }
    }

    /*
        Default-Resize-Behavior:
        ========================
        A table has a specific width which isn't adjusted during a column resize.
        Columns have a min width which is respected by the table.
        During a column resize the width of the resizing column can be extended until
        all other columns to the right of the resized column are reduced to there minimum size.
     */
    private inner class MyTableHeader : JBTable.JBTableHeader() {
        private val myNotAdjustableColumns: MutableSet<Int> = java.util.HashSet()

        override fun setResizingColumn(column: TableColumn?) {
            super.setResizingColumn(column)
            if (column != null) {
                myNotAdjustableColumns.add(column.modelIndex)
            }
        }

        fun isAdjustableColumn(columnIndex: Int): Boolean {
            return !myNotAdjustableColumns.contains(columnIndex)
        }

        override fun getToolTipText(event: MouseEvent): String {
            val columnViewIndex = this.columnAtPoint(event.point)
            val columnModelIndex = if (columnViewIndex >= 0) convertColumnIndexToModel(columnViewIndex) else -1
            var tooltipText: String? = null
            if (columnModelIndex >= 0) {
                tooltipText = createHeaderToolTip(columnViewIndex, columnModelIndex)
            }
            // "super.getToolTipText(event)" can return null, so it has to be handled
            return tooltipText ?: super.getToolTipText(event) ?: ""
        }

        private fun createHeaderToolTip(columnViewIndex: Int, columnModelIndex: Int): String? {
            model?.let { model ->
                if (model.isLeveled()) {
                    val sb = StringBuilder("<html>")
                    // todo: escape potential html chars (<>&) in strings
                    val hexColor = ColorUtil.toHtmlColor(
                        ColorUtil.mix(
                            colorFromUI("ToolTip.background", Color.BLACK),
                            colorFromUI("ToolTip.foreground", Color.WHITE),
                            0.6
                        )
                    )
                    val colorizedText = { text: String -> "<font color='${hexColor}'>$text</font>" }
                    val separator = colorizedText("/")

                    if (model is ITableIndexDataModel) {
                        model.getLegendHeaders().let {
                            if (it.column is LeveledHeaderLabel) {
                                sb.append("${colorizedText("index &rarr;: ")} ${it.column.text(separator)}")
                                sb.append("<br/>")
                            }
                            if (it.row is LeveledHeaderLabel) {
                                sb.append("${colorizedText("index &darr;: ")} ${it.row.text(separator)}")
                                sb.append("<br/>")
                            }
                        }
                    } else if (model is ITableValueDataModel) {
                        model.getLegendHeader().let {
                            if (it is LeveledHeaderLabel) {
                                sb.append("${colorizedText("index: ")} ${it.text(separator)}")
                                sb.append("<br/>")
                            }
                        }
                        model.getColumnHeaderAt(columnModelIndex).let {
                            if (it is LeveledHeaderLabel) {
                                sb.append("${colorizedText("label: ")} ${it.text(separator)}")
                                sb.append("<br/>")
                            }
                        }
                    }

                    return sb.append("</html>").toString()
                } else {
                    val label = when (model) {
                        is ITableValueDataModel -> {
                            model.getColumnHeaderAt(columnModelIndex).text()
                        }
                        is ITableIndexDataModel -> {
                            model.getColumnHeader().text()
                        }
                        else -> ""
                    }
                    if (label.isEmpty() || label == EMPTY_TABLE_HEADER_VALUE) {
                        return ""
                    } else {
                        val column = this.getColumnModel().getColumn(columnViewIndex)
                        var renderer = column.headerRenderer
                        if (renderer == null) {
                            renderer = defaultRenderer
                        }
                        val rendererComp = renderer.getTableCellRendererComponent(
                            this.table,
                            column.headerValue,
                            false,
                            false,
                            -1,
                            columnViewIndex
                        )
                        if (rendererComp is JLabel) {
                            val stringWidth = SwingUtilities.computeStringWidth(
                                rendererComp.getFontMetrics(rendererComp.font),
                                rendererComp.text
                            )
                            if (getHeaderRect(columnViewIndex).width < stringWidth) {
                                return label
                            }
                        }
                    }
                }
            }
            return null
        }

        // public, so that multiple instances of the renderer can be created by the table
        public override fun createDefaultRenderer(): TableCellRenderer {
            return super.createDefaultRenderer()
        }
    }
}

private class EmptyValueModel : AbstractTableModel(), ITableValueDataModel {

    override fun getRowCount() = 0
    override fun getColumnCount() = 0
    override fun isLeveled() = false
    override fun shouldHideHeaders() = false

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Value {
        throw UnsupportedOperationException("Operation 'getValueAt' isn't supported.")
    }

    override fun getColumnHeaderAt(columnIndex: Int): IHeaderLabel {
        throw UnsupportedOperationException("Operation 'getColumnHeaderAt' isn't supported.")
    }

    override fun getColumnName(columnIndex: Int): String {
        throw UnsupportedOperationException("Operation 'getColumnName' isn't supported.")
    }

    override fun getLegendHeader(): IHeaderLabel {
        throw UnsupportedOperationException("Operation 'getLegendHeader' isn't supported.")
    }

    override fun getLegendHeaders(): LegendHeaders {
        throw UnsupportedOperationException("Operation 'getLegendHeaders' isn't supported.")
    }
}

private class EmptyIndexModel : AbstractTableModel(), ITableIndexDataModel {

    override fun getRowCount() = 0
    override fun getColumnCount() = 0
    override fun getColumnName(columnIndex: Int) = getColumnName()
    override fun isLeveled() = false
    override fun shouldHideHeaders() = false

    override fun getValueAt(rowIndex: Int): IHeaderLabel {
        throw UnsupportedOperationException("Operation 'getValueAt' isn't supported.")
    }

    override fun getColumnHeader(): IHeaderLabel {
        throw UnsupportedOperationException("Operation 'getColumnHeader' isn't supported.")
    }

    override fun getColumnName(): String {
        throw UnsupportedOperationException("Operation 'getColumnName' isn't supported.")
    }

    override fun getLegendHeader(): IHeaderLabel {
        throw UnsupportedOperationException("Operation 'getLegendHeader' isn't supported.")
    }

    override fun getLegendHeaders(): LegendHeaders {
        throw UnsupportedOperationException("Operation 'getLegendHeaders' isn't supported.")
    }
}