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
import org.intellij.lang.annotations.MagicConstant
import java.awt.*
import java.awt.event.*
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.*
import javax.swing.*
import javax.swing.RowSorter.SortKey
import javax.swing.event.ChangeEvent
import javax.swing.event.TableModelEvent
import javax.swing.table.*
import kotlin.math.max
import kotlin.math.min


// see styleguide: https://jetbrains.github.io/ui/controls/table/
private const val MIN_COLUMN_WIDTH = 65
private const val MIN_TABLE_WIDTH = 350

private const val MAX_AUTO_EXPAND_COLUMN_WIDTH = 350

data class CellPosition(val rowIndex: Int, val columnIndex: Int)

class DataFrameTable : JScrollPane() {

    private var myDataFrameModel: IDataFrameModel? = null
    private val myIndexTable: MyIndexTable
    private val myValueTable: MyValueTable = MyValueTable()

    init {
        myIndexTable = MyIndexTable(
            myValueTable,
            max(MIN_COLUMN_WIDTH, MIN_TABLE_WIDTH - MIN_COLUMN_WIDTH)
        )

        setViewportView(myValueTable)
        minimumSize = Dimension(MIN_TABLE_WIDTH, 200)
    }

    fun getValueTable() = myValueTable

    fun getPreferredFocusedComponent(): JComponent = myValueTable

    fun getRowHeight() = myValueTable.rowHeight

    fun getRowCount() = myValueTable.rowCount

    fun getColumnCount() = myValueTable.columnCount

    fun setDataFrameModel(model: IDataFrameModel) {
        if (model == myDataFrameModel) return

        myDataFrameModel = model

        /*
        Reuse previous focused/selected cell in case of the same underlying data source.

        Focused cell has to be retrieved before assigning the new models otherwise the focused/selected
        cell is cleared because the tables share the same selection model.

        The previous focused cell is also highlighted even if the sorting has changed.
        It is OK that there could be another row at the focused cell. Since the data
        is fetched on demand, it is not known in which row the previous focused value is located
        in the re-sorted data model.

        In case of filtered data, the selection will not be adjusted either.
        The current filter implementation supports filtering out columns and not only rows. Therefore, it is
        also unknown in which row/col the previous focused value is located. It could even be that the previous
        focused row/col is filtered out.

        Handling all these edge cases would result in an implementation that is too complex and
        no longer comprehensible for the user.

        Maintainable solutions are:
            - keep selection as it is
            - clear the selection
         */
        val valueModel = model.getValueDataModel()
        val isSameDataSource = valueModel.getDataSourceFingerprint() == myValueTable.model?.getDataSourceFingerprint()
        val cellToFocus = if (isSameDataSource) getFocusedCell() else CellPosition(0, 0)

        model.getIndexDataModel().let {
            myIndexTable.setModel(it)
            setRowHeaderView(if (it.columnCount == 0) null else myIndexTable)
            setCorner(
                ScrollPaneConstants.UPPER_LEFT_CORNER,
                if (it.columnCount == 0 || it.shouldHideHeaders()) null else myIndexTable.tableHeader
            )
        }

        model.getValueDataModel().let {
            myValueTable.setModel(it)
            setColumnHeaderView(if (it.shouldHideHeaders()) null else myValueTable.tableHeader)
        }

        setFocusedCell(cellToFocus)
    }

    /**
     * Returns the position of the focused cell.
     * The method doesn't check if the table has currently the focus.
     *
     * @return the position of the focused cell. In case there is no selected cell the
     * [CellPosition.rowIndex] or [CellPosition.columnIndex] is -1.
     */
    fun getFocusedCell(): CellPosition {
        return CellPosition(
            myValueTable.selectionModel.leadSelectionIndex,
            myValueTable.columnModel.selectionModel.leadSelectionIndex,
        )
    }

    /**
     * Sets the focused cell without requesting the focus.
     * Clears the previous selection and ensures the new cell is selected.
     *
     * @param position the cell to set as focused cell.
     * Position is altered in case the indices are out of bounds.
     */
    fun setFocusedCell(position: CellPosition) {
        if (myValueTable.isEmpty) return

        val lastRowIndex = max(0, myValueTable.rowCount - 1)
        val lastColIndex = max(0, myValueTable.columnCount - 1)

        val sanitized = CellPosition(
            min(lastRowIndex, max(0, position.rowIndex)),
            min(lastColIndex, max(0, position.columnIndex)),
        )
        // apply without scrolling
        myValueTable.autoscrolls = false
        myValueTable.changeSelection(sanitized.rowIndex, sanitized.columnIndex, false, false)
        myValueTable.autoscrolls = true
    }

    override fun updateUI() {
        super.updateUI()
        border = BorderFactory.createLineBorder(colorFromUI("TableHeader.separatorColor", Color.BLACK))
    }
}

interface ITableColumnExpander {
    fun markFixed(viewColumnIndex: Int)
    fun clearFixed(viewColumnIndex: Int)
    fun clearAllFixed()
    fun toggleFixed(viewColumnIndex: Int)
    fun isFixed(viewColumnIndex: Int): Boolean
}

class MyValueTable : MyTable<ITableValueDataModel>(), PropertyChangeListener {
    /**
    This flag is needed to guarantee that we don't access not yet initialized class properties.
    This is required because [JTable.setModel] is called in the JTable constructor.
    Code inside [setModel] has to take care to not access not yet initialized non-nullable properties.

    Example:
        - The private property "myEmptyText" from [JBTable] is not initialized at this time.
          The getter of "emptyText" does a "not null"-check before returning the property.
     */
    private val myBaseClassIsFullyInitialized: Boolean = true

    private var myColumnExpander = MyTableColumnAutoExpander()
    private var myResizingColumnWasFixed: Boolean = false
    private var myResizingColumnStartWidth: Int = -1

    init {
        autoCreateColumnsFromModel = false
        autoResizeMode = JTable.AUTO_RESIZE_OFF
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)

        setDefaultRenderer(Object::class.java, ValueCellRenderer())

        getColumnModel().selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                // required to render the header for selected column in another style - see [CenteredHeaderLabelStyler]
                tableHeader?.repaint()
            }
        }

        // set property to 1 - to automatically determine the required row height
        // 1 because we use the same cell renderer for all cells
        setMaxItemsForSizeCalculation(1)

        registerSortKeyBindings()
        disableProblematicKeyBindings()
        tableHeader.addPropertyChangeListener(this)
    }

    override fun propertyChange(event: PropertyChangeEvent) {
        if (event.source == tableHeader) {
            if (event.propertyName == "resizingColumn") {
                val newColumn = event.newValue as MyValueTableColumn?
                val oldColumn = event.oldValue as MyValueTableColumn?
                if (newColumn == null) {
                    if (oldColumn != null && (myResizingColumnWasFixed || oldColumn.width != myResizingColumnStartWidth)) {
                        myColumnExpander.markFixed(convertColumnIndexToView(oldColumn.modelIndex))
                    }
                } else {
                    convertColumnIndexToView(newColumn.modelIndex).let {
                        myResizingColumnWasFixed = myColumnExpander.isFixed(it)
                        if (myResizingColumnWasFixed) {
                            myColumnExpander.clearFixed(it)
                        }
                    }
                }
            }
        }
    }

    fun getColumnExpander(): ITableColumnExpander {
        return myColumnExpander
    }

    override fun prepareRenderer(renderer: TableCellRenderer, row: Int, column: Int): Component {
        return super.prepareRenderer(renderer, row, column).also {
            myColumnExpander.updateColumnWidthByPreparedRendererComponent(it, column)
        }
    }

    private data class ColumnCacheKey(val type: String, val index: Int) {
        companion object {
            fun orgFrameIndex(orgFrameIndex: Int) = ColumnCacheKey("orgIndex", orgFrameIndex)
            fun modelIndex(modelIndex: Int) = ColumnCacheKey("modelIndex", modelIndex)
        }
    }

    private fun createTableColumns(
        valueModel: ITableValueDataModel,
        prevTableColumnCache: Map<ColumnCacheKey, MyValueTableColumn>,
    ): List<MyValueTableColumn> {
        val result = mutableListOf<MyValueTableColumn>()
        for (modelIndex in 0 until valueModel.columnCount) {
            val frameColumnIndex = valueModel.convertToFrameColumnIndex(modelIndex)
            result.add(MyValueTableColumn(modelIndex, frameColumnIndex).apply {
                prevTableColumnCache[ColumnCacheKey.orgFrameIndex(frameColumnIndex)]?.let { prevColumn ->
                    headerValue = prevColumn.headerValue
                    cellRenderer = prevColumn.cellRenderer
                    hasFixedWidth = prevColumn.hasFixedWidth
                    width = prevColumn.width
                    minWidth = prevColumn.minWidth
                    maxWidth = prevColumn.maxWidth
                    preferredWidth = prevColumn.preferredWidth
                }
            })
        }
        return result
    }

    override fun setModel(tableModel: TableModel) {
        if (tableModel !is ITableValueDataModel) throw IllegalArgumentException("The model has to implement ITableValueDataModel.")

        val prevTableColumnCache = mutableMapOf<ColumnCacheKey, MyValueTableColumn>()
        val isSameDataSource = tableModel.getDataSourceFingerprint() == model?.getDataSourceFingerprint()
        val translateSortKeys = rowSorter?.sortKeys?.isNotEmpty() == true

        if (isSameDataSource) {
            for (column in columnModel.columns) {
                if (column !is MyValueTableColumn) continue
                prevTableColumnCache[ColumnCacheKey.orgFrameIndex(column.orgFrameIndex)] = column
                if (translateSortKeys) prevTableColumnCache[ColumnCacheKey.modelIndex(column.modelIndex)] = column
            }
        }

        super.setModel(tableModel)

        val newColumns = createTableColumns(tableModel, prevTableColumnCache)
        while (columnCount > 0) removeColumn(columnModel.getColumn(0))
        newColumns.forEach { addColumn(it) }

        rowSorter = MyExternalDataRowSorter(tableModel).apply {
            if (isSameDataSource && translateSortKeys) {
                rowSorter?.sortKeys?.let { oldSortKeys ->
                    val newTableColumnCache = newColumns.associateBy { ColumnCacheKey.orgFrameIndex(it.orgFrameIndex) }
                    sortKeys = oldSortKeys.mapNotNull {
                        val orgFrameIndex = prevTableColumnCache[ColumnCacheKey.modelIndex(it.column)]?.orgFrameIndex ?: return@mapNotNull null
                        val newModelIndex = newTableColumnCache[ColumnCacheKey.orgFrameIndex(orgFrameIndex)]?.modelIndex ?: return@mapNotNull null
                        if (newModelIndex == -1) null else SortKey(newModelIndex, it.sortOrder)
                    }
                }
            }
        }

        if (myBaseClassIsFullyInitialized) {
            myColumnExpander.ensureLastColumnIsNotFixed()
            emptyText.text = if (tableModel.rowCount > 0) {
                "loading DataFrame"
            } else {
                "DataFrame is empty"
            }
        }
    }

    override fun setRowSorter(sorter: RowSorter<out TableModel>?) {
        if (sorter !is MyExternalDataRowSorter?) throw IllegalArgumentException("The sorter has to be of type MyExternalDataRowSorter.")
        super.setRowSorter(sorter)
    }

    override fun getRowSorter(): MyExternalDataRowSorter? {
        return super.getRowSorter() as? MyExternalDataRowSorter
    }

    override fun convertColumnIndexToView(modelColumnIndex: Int): Int {
        if (modelColumnIndex >= 0 && modelColumnIndex < columnCount) {
            // fast path - if no columns were re-arranged
            columnModel.getColumn(modelColumnIndex).let { if (it.modelIndex == modelColumnIndex) return it.modelIndex }
        }
        return super.convertColumnIndexToView(modelColumnIndex)
    }

    override fun getScrollableTracksViewportWidth(): Boolean {
        return preferredSize.width < parent.width
    }

    override fun doLayout() {
        // https://stackoverflow.com/questions/15234691/enabling-auto-resize-of-jtable-only-if-it-fit-viewport
        if (tableHeader.resizingColumn == null) {
            autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
            super.doLayout()
            autoResizeMode = JTable.AUTO_RESIZE_OFF
        } else {
            // update resized column
            tableHeader.resizingColumn?.let { it.preferredWidth = it.width }

            if (scrollableTracksViewportWidth) {
                // proportionately resize all columns to fit viewport width
                autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
                super.doLayout()
                autoResizeMode = JTable.AUTO_RESIZE_OFF
            }
        }
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
        return MyEmptyValueModel()
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
            put("sortColumnAscending", MySortColumnsAction(SortOrder.ASCENDING, false))
            put("sortColumnDescending", MySortColumnsAction(SortOrder.DESCENDING, false))
            put("clearColumnSorting", MySortColumnsAction(SortOrder.UNSORTED, false))

            put("addColumnAscendingToMultiSort", MySortColumnsAction(SortOrder.ASCENDING, true))
            put("addColumnDescendingToMultiSort", MySortColumnsAction(SortOrder.DESCENDING, true))
            put("removeColumnFromMultiSort", MySortColumnsAction(SortOrder.UNSORTED, true))
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

    private class MySortColumnsAction(
        private val sortOrder: SortOrder,
        private val shiftKeyIsDown: Boolean
    ) : AbstractAction() {
        override fun accept(sender: Any?) = sender is MyValueTable

        override fun actionPerformed(event: ActionEvent) {
            (event.source as MyValueTable).let { table ->
                val viewColumnIndex = table.columnModel.selectionModel.leadSelectionIndex
                if (viewColumnIndex == -1) return
                table.rowSorter?.let {
                    if (sortOrder == SortOrder.UNSORTED && !shiftKeyIsDown) {
                        it.sortKeys = emptyList()
                    } else {
                        it.setSortOrder(
                            table.convertColumnIndexToModel(viewColumnIndex),
                            sortOrder,
                            shiftKeyIsDown
                        )
                    }
                }
            }
        }
    }

    private inner class MyTableColumnAutoExpander : ITableColumnExpander {

        init { installActions() }

        override fun markFixed(viewColumnIndex: Int) {
            /*
            Last column can't be fixed.

            There has to be at least one column which can't be set to a fixed width. That column is used
            as a buffer to which the remaining width will be applied in case all other columns have a
            fixed width.

            Otherwise, the fixed columns would not fill the available width of the viewport if the user
            makes the width of the window wider than the total width of the fixed columns. There would
            be an empty space to the right side. The same problem occurs when no horizontal scroll bar
            is visible and the user shrinks one of the fixed columns. Since all columns are fixed, none
            of the other columns would take the freed space.
             */
            if (viewColumnIndex == columnCount - 1) return
            columnAt(viewColumnIndex).let {
                if (!it.hasFixedWidth) {
                    it.hasFixedWidth = true
                    it.minWidth = it.width
                    it.maxWidth = it.width
                    it.preferredWidth = it.width
                    tableHeader.repaint()
                    repaint()
                }
            }
        }

        override fun clearFixed(viewColumnIndex: Int) {
            columnAt(viewColumnIndex).let {
                if (it.hasFixedWidth) {
                    it.hasFixedWidth = false
                    it.minWidth = MIN_COLUMN_WIDTH
                    it.maxWidth = Int.MAX_VALUE
                    tableHeader.repaint()
                    repaint()
                }
            }
        }

        override fun clearAllFixed() {
            for (i in 0 until columnCount) clearFixed(i)
        }

        override fun toggleFixed(viewColumnIndex: Int) {
            if (isFixed(viewColumnIndex)) {
                clearFixed(viewColumnIndex)
            } else {
                markFixed(viewColumnIndex)
            }
        }

        override fun isFixed(viewColumnIndex: Int): Boolean {
            return columnAt(viewColumnIndex).hasFixedWidth
        }

        fun ensureLastColumnIsNotFixed() {
            if (columnCount > 0) {
                clearFixed(columnCount - 1)
            }
        }

        fun updateColumnWidthByPreparedRendererComponent(component: Component, viewColumnIndex: Int) {
            val resizingColumn = tableHeader.resizingColumn
            if (resizingColumn == null && !isFixed(viewColumnIndex)) {
                val tableColumn = columnModel.getColumn(viewColumnIndex)
                val renderer = tableColumn.headerRenderer ?: tableHeader.defaultRenderer
                val columnHeaderWidth = renderer.getTableCellRendererComponent(
                    this@MyValueTable,
                    tableColumn.headerValue,
                    false,
                    false,
                    -1,
                    viewColumnIndex,
                ).preferredSize.width + 4
                tableColumn.preferredWidth = min(
                    // only expand column - never shrink back
                    max(
                        max(columnHeaderWidth, component.preferredSize.width) + 2 * intercellSpacing.width,
                        tableColumn.preferredWidth,
                    ),
                    MAX_AUTO_EXPAND_COLUMN_WIDTH,
                )
            }
        }

        private fun columnAt(viewColumnIndex: Int): MyValueTableColumn {
            return columnModel.getColumn(viewColumnIndex) as MyValueTableColumn
        }

        private fun installActions() {
            installAction(
                MyToggleFixedForFocusedColumnAction(),
                "toggleFixedForFocusedColumn",
                KeyEvent.VK_PERIOD,
                KeyEvent.SHIFT_DOWN_MASK,
            )
            installAction(
                MyInvertFixedAction(),
                "invertFixed",
                KeyEvent.VK_PERIOD,
                KeyEvent.CTRL_DOWN_MASK,
            )
            installAction(
                MyClearAllFixedAction(),
                "clearAllFixed",
                KeyEvent.VK_PERIOD,
                KeyEvent.CTRL_DOWN_MASK or KeyEvent.SHIFT_DOWN_MASK,
            )
            val expandBy = 10
            installAction(
                MyAdjustFixedWidthAction { min(it + expandBy, MAX_AUTO_EXPAND_COLUMN_WIDTH) },
                "expandWidthForFocusedColumn",
                KeyEvent.VK_PLUS,
                KeyEvent.SHIFT_DOWN_MASK,
            )
            installAction(
                MyAdjustFixedWidthAction { MAX_AUTO_EXPAND_COLUMN_WIDTH },
                "expandWidthMaxForFocusedColumn",
                KeyEvent.VK_PLUS,
                KeyEvent.SHIFT_DOWN_MASK or KeyEvent.CTRL_DOWN_MASK,
            )

            installAction(
                MyAdjustFixedWidthAction { max(it - expandBy, MIN_COLUMN_WIDTH) },
                "shrinkWidthForFocusedColumn",
                KeyEvent.VK_MINUS,
                KeyEvent.SHIFT_DOWN_MASK,
            )
            installAction(
                MyAdjustFixedWidthAction { MIN_COLUMN_WIDTH },
                "shrinkWidthMinForFocusedColumn",
                KeyEvent.VK_MINUS,
                KeyEvent.SHIFT_DOWN_MASK or KeyEvent.CTRL_DOWN_MASK,
            )
        }

        private fun installAction(
            action: Action,
            key: String,
            keyCode: Int,
            @MagicConstant(flagsFromClass = java.awt.event.InputEvent::class)
            modifiers: Int,
        ) {
            getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                KeyStroke.getKeyStroke(keyCode, modifiers),
                key,
            )
            actionMap.put(key, action)
        }

        private inner class MyToggleFixedForFocusedColumnAction : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                columnModel.selectionModel.leadSelectionIndex.let { if (it != -1) toggleFixed(it) }
            }
        }

        private inner class MyClearAllFixedAction : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                clearAllFixed()
            }
        }

        private inner class MyInvertFixedAction : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                for (i in 0 until columnCount) toggleFixed(i)
            }
        }

        private inner class MyAdjustFixedWidthAction(
            private val newWidthProducer: (width: Int) -> Int,
        ) : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                columnModel.selectionModel.leadSelectionIndex.let { index ->
                    if (index != -1) {
                        val column = columnModel.getColumn(index)
                        val newWidth = newWidthProducer(column.width)
                        if (isFixed(index)) {
                            column.minWidth = newWidth
                            column.maxWidth = newWidth
                        }
                        // In case of a non-fixed column, the autoFit behavior will automatically
                        // adjust the width at a later time.
                        // But it is OK to allow the user to modify it in that case. It can
                        // be useful when the user has filtered out large values of a specific
                        // column and wants to shrink the width of a column without setting a fixed width
                        // to not lose the autoFit feature.
                        column.preferredWidth = newWidth
                    }
                }
            }
        }
    }

    private class MyValueTableColumn(
        modelIndex: Int,
        orgFrameIndex: Int,
        var hasFixedWidth: Boolean = false,
    ) : TableColumn(modelIndex) {
        init { super.identifier = orgFrameIndex }

        val orgFrameIndex: Int
            get() = getIdentifier() as Int

        override fun setIdentifier(identifier: Any?) {
            throw UnsupportedOperationException("Identifier can't be overwritten.")
        }
    }
}

class MyExternalDataRowSorter(private val model: ITableValueDataModel) : RowSorter<ITableValueDataModel>() {

    private var mySortKeys: List<SortKey> = emptyList()
    private var myShiftKeyIsDown: Boolean = false
    private val myMaxSortKeys = 9

    override fun getModel() = model

    fun setShiftKeyIsDownDuringAction(isDown: Boolean) {
        myShiftKeyIsDown = isDown
    }

    fun setSortOrder(modelColumnIndex: Int, order: SortOrder, shiftKeyIsDown: Boolean) {
        val sortKeyIndex = sortKeys.indexOfFirst { it.column == modelColumnIndex }
        if (sortKeyIndex == -1 && order == SortOrder.UNSORTED) return
        if (sortKeyIndex != -1 && sortKeys[sortKeyIndex].sortOrder == order && sortKeys.size > 1 && shiftKeyIsDown) return
        myShiftKeyIsDown = shiftKeyIsDown
        updateSortKey(sortKeyIndex, SortKey(modelColumnIndex, order))
        myShiftKeyIsDown = false
    }

    override fun toggleSortOrder(modelColumnIndex: Int) {
        val sortKeyIndex = sortKeys.indexOfFirst { it.column == modelColumnIndex }
        val updatedSortKey = if (sortKeyIndex == -1) {
            SortKey(modelColumnIndex, SortOrder.ASCENDING)
        } else {
            if (myShiftKeyIsDown) {
                toggle(sortKeys[sortKeyIndex])
            } else {
                if (sortKeys.size > 1) {
                    // was multi sort - clear sort and start from scratch
                    SortKey(modelColumnIndex, SortOrder.ASCENDING)
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
            model.setSortKeys(mySortKeys)
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

class MyIndexTable(
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
        return MyEmptyIndexModel()
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
        tableModel.enableDataFetching(false)
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
                    for (modelColumnIndex in payload.firstColumn..payload.lastColumn) {
                        columnModel.getColumn(convertColumnIndexToView(modelColumnIndex)).let {
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

    /**
     * Fetching data which is not yet loaded to calculate things outside the painting process is unwanted
     * because it can block the model from fetching other required data while loading unwanted data.
     *
     * Examples where not yet loaded data shouldn't be fetched:
     *  - [JBTable.getExpandedColumnWidth]
     *  - [AbstractExpandableItemsHandler.createToolTipImage]
     *  - [JBTable.JBTableHeader.packColumn]
     *  - [JBTable.calculateRowHeight]
     *  - ...
     *
     *  Therefore, the data fetching is only activated during the [paintComponent] method.
     *  (It doesn't have to be enabled for the table header - it's on purpose.)
     */
    override fun paintComponent(g: Graphics) {
        model?.enableDataFetching(true)
        super.paintComponent(g)
        model?.enableDataFetching(false)
    }

    protected abstract fun createLeveledTableHeaderRenderer(): TableCellRenderer

    protected abstract fun createTableHeaderRenderer(): TableCellRenderer

    protected fun createDefaultHeaderRenderer(): TableCellRenderer {
        return getCastedTableHeader().createDefaultRenderer()
    }

    private fun getCastedTableHeader(): MyTable<*>.MyTableHeader {
        return getTableHeader() as MyTable<*>.MyTableHeader
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

    private inner class MyTableHeader : JBTable.JBTableHeader() {
        override fun processMouseEvent(e: MouseEvent?) {
            if (e?.id == MouseEvent.MOUSE_RELEASED) setRowSorterShiftKeyFlag(e.isShiftDown)
            super.processMouseEvent(e)
            if (e?.id == MouseEvent.MOUSE_CLICKED) setRowSorterShiftKeyFlag(false)
        }

        override fun setResizingColumn(column: TableColumn?) {
            val oldValue = resizingColumn
            super.setResizingColumn(column)
            if (oldValue != resizingColumn) {
                firePropertyChange("resizingColumn", oldValue, resizingColumn)
            }
        }

        override fun getToolTipText(event: MouseEvent): String {
            val viewColumnIndex = this.columnAtPoint(event.point)
            val modelColumnIndex = convertColumnIndexToModel(viewColumnIndex)
            var tooltipText: String? = null
            if (modelColumnIndex >= 0) {
                tooltipText = createHeaderToolTip(viewColumnIndex, modelColumnIndex)
            }
            // "super.getToolTipText(event)" can return null, so it has to be handled
            return tooltipText ?: super.getToolTipText(event) ?: ""
        }

        private fun setRowSorterShiftKeyFlag(isDown: Boolean) {
            table.rowSorter?.let {
                if (it is MyExternalDataRowSorter) {
                    it.setShiftKeyIsDownDuringAction(isDown)
                }
            }
        }

        private fun createHeaderToolTip(viewColumnIndex: Int, modelColumnIndex: Int): String? {
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
                        model.getColumnHeaderAt(modelColumnIndex).let {
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
                            model.getColumnHeaderAt(modelColumnIndex).text()
                        }

                        is ITableIndexDataModel -> {
                            model.getColumnHeader().text()
                        }

                        else -> ""
                    }
                    if (label.isEmpty() || label == EMPTY_TABLE_HEADER_VALUE) {
                        return ""
                    } else {
                        val column = this.getColumnModel().getColumn(viewColumnIndex)
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
                            viewColumnIndex
                        )
                        if (rendererComp is JLabel) {
                            val stringWidth = SwingUtilities.computeStringWidth(
                                rendererComp.getFontMetrics(rendererComp.font),
                                rendererComp.text
                            )
                            if (getHeaderRect(viewColumnIndex).width < stringWidth) {
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

private class MyEmptyValueModel(
    private val dataSourceFingerprint: String = UUID.randomUUID().toString(),
) : AbstractTableModel(), ITableValueDataModel {

    override fun getRowCount() = 0
    override fun getColumnCount() = 0
    override fun isLeveled() = false
    override fun shouldHideHeaders() = false
    override fun enableDataFetching(enabled: Boolean) {}
    override fun getDataSourceFingerprint() = dataSourceFingerprint

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Value {
        throw UnsupportedOperationException("Operation 'getValueAt' isn't supported.")
    }

    override fun getColumnHeaderAt(columnIndex: Int): IHeaderLabel {
        throw UnsupportedOperationException("Operation 'getColumnHeaderAt' isn't supported.")
    }

    override fun setSortKeys(sortKeys: List<SortKey>) {
        throw UnsupportedOperationException("Operation 'setSortKeys' isn't supported.")
    }

    override fun convertToFrameColumnIndex(columnIndex: Int): Int {
        throw UnsupportedOperationException("Operation 'convertToFrameColumnIndex' isn't supported.")
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

private class MyEmptyIndexModel(
    private val dataSourceFingerprint: String = UUID.randomUUID().toString(),
) : AbstractTableModel(), ITableIndexDataModel {

    override fun getRowCount() = 0
    override fun getColumnCount() = 0
    override fun getColumnName(columnIndex: Int) = getColumnName()
    override fun isLeveled() = false
    override fun shouldHideHeaders() = false
    override fun enableDataFetching(enabled: Boolean) {}
    override fun getDataSourceFingerprint() = dataSourceFingerprint

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