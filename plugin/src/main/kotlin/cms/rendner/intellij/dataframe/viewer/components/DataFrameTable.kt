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
import java.awt.event.*
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.RowSorterEvent
import javax.swing.event.RowSorterListener
import javax.swing.event.TableModelEvent
import javax.swing.table.*
import kotlin.math.max
import kotlin.math.min

// see styleguide: https://jetbrains.github.io/ui/controls/table/
private const val MIN_COLUMN_WIDTH = 65
private const val MIN_TABLE_WIDTH = 350

private const val MAX_AUTO_EXPAND_COLUMN_WIDTH = 350

class DataFrameTable : JScrollPane(), RowSorterListener, PropertyChangeListener {

    private var myDataFrameModel: IDataFrameModel? = null
    private val myIndexTable: MyIndexTable
    private val myValueTable: MyValueTable = MyValueTable()

    init {
        myIndexTable = MyIndexTable(
            myValueTable,
            max(MIN_COLUMN_WIDTH, MIN_TABLE_WIDTH - MIN_COLUMN_WIDTH)
        )

        setViewportView(myValueTable)
        minimumSize = Dimension(MIN_TABLE_WIDTH, 250)

        myValueTable.addPropertyChangeListener(this)
    }

    fun setDataFrameModel(model: IDataFrameModel) {
        if (model == myDataFrameModel) return
        myDataFrameModel = model

        model.getValueDataModel().let {
            myValueTable.setModel(it)
            myValueTable.rowSorter = if (model is IExternalSortableDataFrameModel) MyExternalDataRowSorter(it) else null
            setColumnHeaderView(if (it.shouldHideHeaders()) null else myValueTable.tableHeader)
        }
        model.getIndexDataModel().let {
            myIndexTable.setModel(it)
            setRowHeaderView(if (it.columnCount == 0) null else myIndexTable)
            setCorner(
                ScrollPaneConstants.UPPER_LEFT_CORNER,
                if (it.columnCount == 0 || it.shouldHideHeaders()) null else myIndexTable.tableHeader
            )
        }

        if (myValueTable.rowCount > 0) {
            SwingUtilities.invokeLater {
                // to focus first cell and allow immediately to use key bindings like sort
                myValueTable.changeSelection(0, 0, false, false)
                myValueTable.requestFocus()
            }
        }
    }

    fun getRowHeight(): Int {
        return myValueTable.rowHeight
    }

    override fun sorterChanged(event: RowSorterEvent) {
        if (event.type != RowSorterEvent.Type.SORT_ORDER_CHANGED) return
        myDataFrameModel?.let { model ->
            if (model !is IExternalSortableDataFrameModel) return
            model.setSortKeys(event.source.sortKeys)

            // Whenever the sorting changes the selection has to be cleared.
            // It is unknown in which row the selected cell will be after sorting.
            myIndexTable.selectionModel?.clearSelection()
            myValueTable.selectionModel?.clearSelection()
        }
    }

    override fun propertyChange(event: PropertyChangeEvent) {
        if (event.source == myValueTable) {
            if (event.propertyName == "rowSorter") {
                event.oldValue?.let { if (it is RowSorter<*>) it.removeRowSorterListener(this) }
                myDataFrameModel?.let { model ->
                    if (model !is IExternalSortableDataFrameModel) return
                    event.newValue?.let {
                        if (it is RowSorter<*>) {
                            it.addRowSorterListener(this)
                            model.setSortKeys(it.sortKeys)
                        }
                    }
                }
            }
        }
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
                required to render the header for selected column in another style - see [CenteredHeaderLabelStyler]
                 */
                tableHeader?.repaint()
            }
        }

        // set property to 1 - to automatically determine the required row height
        // 1 because we use the same cell renderer for all cells
        setMaxItemsForSizeCalculation(1)

        registerSortKeyBindings()
        disableProblematicKeyBindings()
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

    private fun registerSortKeyBindings() {
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).apply {
            put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0), "sortColumnAscending")
            put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0), "sortColumnDescending")
            put(KeyStroke.getKeyStroke(KeyEvent.VK_C, 0), "clearColumnSorting")
            (KeyEvent.ALT_DOWN_MASK).let {
                put(KeyStroke.getKeyStroke(KeyEvent.VK_A, it), "addColumnAscendingToMultiSort")
                put(KeyStroke.getKeyStroke(KeyEvent.VK_D, it), "addColumnDescendingToMultiSort")
                put(KeyStroke.getKeyStroke(KeyEvent.VK_C, it), "removeColumnFromMultiSort")
            }
        }
        actionMap.apply {
            put("sortColumnAscending", SortColumnsAction(SortOrder.ASCENDING, false))
            put("sortColumnDescending", SortColumnsAction(SortOrder.DESCENDING, false))
            put("clearColumnSorting", SortColumnsAction(SortOrder.UNSORTED, false))

            put("addColumnAscendingToMultiSort", SortColumnsAction(SortOrder.ASCENDING, true))
            put("addColumnDescendingToMultiSort", SortColumnsAction(SortOrder.DESCENDING, true))
            put("removeColumnFromMultiSort", SortColumnsAction(SortOrder.UNSORTED, true))
        }
    }

    private fun disableProblematicKeyBindings() {
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).apply {
            put(KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.CTRL_DOWN_MASK), "doNothing")
        }
        actionMap.put("doNothing", DoNothingAction())
    }

    private class DoNothingAction : AbstractAction() {
        override fun actionPerformed(e: ActionEvent?) {}
    }

    private class SortColumnsAction(
        private val sortOrder: SortOrder,
        private val shiftKeyIsDown: Boolean
    ) : AbstractAction() {
        override fun accept(sender: Any?) = sender is MyValueTable

        override fun actionPerformed(event: ActionEvent) {
            (event.source as MyValueTable).let { table ->
                val column = table.selectedColumn
                if (column == -1) return
                table.rowSorter?.let {
                    if (it is MyExternalDataRowSorter) {
                        if (sortOrder == SortOrder.UNSORTED && !shiftKeyIsDown) {
                            it.sortKeys = emptyList()
                        } else it.setSortOrder(column, sortOrder, shiftKeyIsDown)
                    }
                }
            }
        }
    }
}

private class MyExternalDataRowSorter(private val model: ITableValueDataModel) : RowSorter<ITableValueDataModel?>() {

    private var mySortKeys: List<SortKey> = emptyList()
    private var myShiftKeyIsDown: Boolean = false
    private val myMaxSortKeys = 9

    override fun getModel() = model

    fun setShiftKeyIsDownDuringAction(isDown: Boolean) {
        myShiftKeyIsDown = isDown
    }

    fun setSortOrder(column: Int, order: SortOrder, shiftKeyIsDown: Boolean) {
        val sortKeyIndex = sortKeys.indexOfFirst { it.column == column }
        if (sortKeyIndex == -1 && order == SortOrder.UNSORTED) return
        if (sortKeyIndex != -1 && sortKeys[sortKeyIndex].sortOrder == order && sortKeys.size > 1 && shiftKeyIsDown) return
        myShiftKeyIsDown = shiftKeyIsDown
        updateSortKey(sortKeyIndex, SortKey(column, order))
        myShiftKeyIsDown = false
    }

    override fun toggleSortOrder(column: Int) {
        val sortKeyIndex = sortKeys.indexOfFirst { it.column == column }
        val updatedSortKey = if (sortKeyIndex == -1) {
            SortKey(column, SortOrder.ASCENDING)
        } else {
            if (myShiftKeyIsDown) {
                toggle(sortKeys[sortKeyIndex])
            } else {
                if (sortKeys.size > 1) {
                    // was multi sort - clear sort and start from scratch
                    SortKey(column, SortOrder.ASCENDING)
                } else {
                    toggle(sortKeys[sortKeyIndex])
                }
            }
        }

        updateSortKey(sortKeyIndex, updatedSortKey)
    }

    override fun setSortKeys(keys: List<SortKey>?) {
        val old = mySortKeys
        mySortKeys = keys?.toList() ?: emptyList()
        if (mySortKeys.size > myMaxSortKeys) {
            mySortKeys = mySortKeys.subList(0, myMaxSortKeys)
        }
        if (mySortKeys != old) {
            fireSortOrderChanged()
        }
    }

    override fun convertRowIndexToModel(index: Int) = index
    override fun convertRowIndexToView(index: Int) = index
    override fun getSortKeys() = mySortKeys
    override fun getViewRowCount() = model.rowCount
    override fun getModelRowCount() = model.rowCount
    override fun modelStructureChanged() {}
    override fun allRowsChanged() {}
    override fun rowsInserted(firstRow: Int, endRow: Int) {}
    override fun rowsDeleted(firstRow: Int, endRow: Int) {}
    override fun rowsUpdated(firstRow: Int, endRow: Int) {}
    override fun rowsUpdated(firstRow: Int, endRow: Int, column: Int) {}

    private fun toggle(key: SortKey): SortKey {
        return if (myShiftKeyIsDown) {
            // shift is down - only cycle between asc, desc
            when (key.sortOrder) {
                SortOrder.ASCENDING -> SortKey(key.column, SortOrder.DESCENDING)
                else -> SortKey(key.column, SortOrder.ASCENDING)
            }
        } else {
            when (key.sortOrder) {
                SortOrder.ASCENDING -> SortKey(key.column, SortOrder.DESCENDING)
                SortOrder.DESCENDING -> SortKey(key.column, SortOrder.UNSORTED)
                else -> SortKey(key.column, SortOrder.ASCENDING)
            }
        }
    }

    private fun updateSortKey(sortKeyIndex: Int, updatedSortKey: SortKey) {
        sortKeys = if (sortKeyIndex == -1) {
            // column isn't sorted
            if (myShiftKeyIsDown) {
                if (sortKeys.size >= myMaxSortKeys) return
                sortKeys.toMutableList().also { it.add(updatedSortKey) }
            } else {
                listOf(updatedSortKey)
            }
        } else {
            // column is already sorted
            if (myShiftKeyIsDown) {
                sortKeys.toMutableList().also {
                    if (updatedSortKey.sortOrder == SortOrder.UNSORTED) {
                        it.removeAt(sortKeyIndex)
                    } else {
                        it[sortKeyIndex] = updatedSortKey
                    }
                }
            } else {
                if (sortKeys.size > 1) {
                    // was multi sort - clear sort and start from scratch
                    listOf(updatedSortKey)
                } else {
                    updatedSortKey.let { if (it.sortOrder == SortOrder.UNSORTED) emptyList() else listOf(it) }
                }
            }
        }
    }
}

private class MyIndexTable(
    private val mainTable: MyValueTable,
    private val maxColumnWidth: Int
) : MyTable<ITableIndexDataModel>() {

    init {
        // disable automatic row height adjustment - use the value from the mainTable
        setMaxItemsForSizeCalculation(0)
        rowHeight = mainTable.rowHeight

        setSelectionModel(mainTable.selectionModel)
        isFocusable = false
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

    override fun addMouseMotionListener(l: MouseMotionListener?) {
        // Workaround to disable unwanted hovered-background behavior of the JBTable.
        // The feature can be disabled by setting "putClientProperty(RenderingUtil.PAINT_HOVERED_BACKGROUND, false)"
        // on the table, but then the IDE gives the following warning:
        // 'PAINT_HOVERED_BACKGROUND' is marked unstable with @ApiStatus.Experimental
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

    override fun convertRowIndexToModel(viewRowIndex: Int): Int {
        return mainTable.convertRowIndexToModel(viewRowIndex)
    }

    override fun convertRowIndexToView(modelRowIndex: Int): Int {
        return mainTable.convertRowIndexToView(modelRowIndex)
    }

    override fun addMouseListener(l: MouseListener?) {
        // do nothing
        // table should not be clickable but tooltip should work
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

        override fun processMouseEvent(e: MouseEvent?) {
            if (e?.id == MouseEvent.MOUSE_RELEASED) setRowSorterShiftKeyFlag(e.isShiftDown)
            super.processMouseEvent(e)
            if (e?.id == MouseEvent.MOUSE_CLICKED) setRowSorterShiftKeyFlag(false)
        }

        private fun setRowSorterShiftKeyFlag(isDown: Boolean) {
            table.rowSorter?.let {
                if (it is MyExternalDataRowSorter) {
                    it.setShiftKeyIsDownDuringAction(isDown)
                }
            }
        }

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