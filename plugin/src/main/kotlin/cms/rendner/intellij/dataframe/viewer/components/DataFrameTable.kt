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
private const val MAX_COLUMN_WIDTH = Int.MAX_VALUE
private const val MIN_TABLE_WIDTH = 350

private const val MAX_AUTO_EXPAND_COLUMN_WIDTH = 350

data class CellPosition(val rowIndex: Int, val columnIndex: Int)

class DataFrameTable : JScrollPane() {

    private var myDataFrameModel: IDataFrameModel = EmptyDataFrameModel()
    private val myIndexTable: MyIndexTable
    private val myValueTable: MyValueTable = MyValueTable(myDataFrameModel.getValueDataModel())

    init {
        myIndexTable = MyIndexTable(
            myValueTable,
            max(MIN_COLUMN_WIDTH, MIN_TABLE_WIDTH - MIN_COLUMN_WIDTH),
            myDataFrameModel.getIndexDataModel(),
        )

        setViewportView(myValueTable)
        minimumSize = Dimension(MIN_TABLE_WIDTH, 200)
    }

    fun getValueTable() = myValueTable

    fun getPreferredFocusedComponent(): JComponent = myValueTable

    fun getRowHeight() = myValueTable.rowHeight

    fun getRowCount() = myValueTable.rowCount

    fun getColumnCount() = myValueTable.columnCount

    fun getDataFrameModel() = myDataFrameModel

    fun setDataFrameModel(model: IDataFrameModel) {
        if (model == myDataFrameModel) return

        val oldFingerprint = myDataFrameModel.getDataSourceFingerprint()
        val newFingerprint = model.getDataSourceFingerprint()
        val isSameDataSource = newFingerprint != null && oldFingerprint == newFingerprint

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
            if (isSameDataSource) {
                myValueTable.setModelWithSameDataSource(it)
            } else {
                myValueTable.setModel(it)
            }
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

interface IColumnResizeBehavior {
    fun markFixed(viewColumnIndex: Int)
    fun clearFixed(viewColumnIndex: Int)
    fun clearAllFixed()
    fun toggleFixed(viewColumnIndex: Int)
    fun isFixed(viewColumnIndex: Int): Boolean
}

class MyValueTable(model: ITableValueDataModel) : MyTable<ITableValueDataModel>(model) {
    /**
    This flag is needed to guarantee that we don't access not yet initialized class properties.
    This is required because [JTable.setModel] is called in the JTable constructor.
    Code inside [setModel] has to take care to not access not yet initialized non-nullable properties.

    Example:
    - The private property "myEmptyText" from [JBTable] is not initialized at this time.
    The getter of "emptyText" does a "not null"-check before returning the property.
     */
    private val myBaseClassIsFullyInitialized: Boolean = true

    private var myColumnResizeBehavior = MyColumnResizeBehavior()
    private var myModelHasSameDataSource: Boolean = false

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
    }

    fun getColumnResizeBehavior(): IColumnResizeBehavior {
        return myColumnResizeBehavior
    }

    override fun prepareRenderer(renderer: TableCellRenderer, row: Int, column: Int): Component {
        return super.prepareRenderer(renderer, row, column).also {
            myColumnResizeBehavior.updateColumnWidthByPreparedRendererComponent(it, column)
        }
    }

    private data class ColumnCacheKey(val type: String, val index: Int) {
        companion object {
            fun orgFrameIndex(orgFrameIndex: Int) = ColumnCacheKey("orgIndex", orgFrameIndex)
            fun modelIndex(modelIndex: Int) = ColumnCacheKey("modelIndex", modelIndex)
        }
    }

    private fun createColumns(
        valueModel: ITableValueDataModel,
        prevTableColumnCache: Map<ColumnCacheKey, MyValueTableColumn>,
    ): List<MyValueTableColumn> {
        val result = mutableListOf<MyValueTableColumn>()
        for (modelIndex in 0 until valueModel.columnCount) {
            val frameColumnIndex = valueModel.convertToFrameColumnIndex(modelIndex)
            result.add(MyValueTableColumn(modelIndex, frameColumnIndex).apply {
                prevTableColumnCache[ColumnCacheKey.orgFrameIndex(frameColumnIndex)]?.let { prevColumn ->
                    this.copyStateFrom(prevColumn)
                }
            })
        }
        return result
    }

    fun setModelWithSameDataSource(tableModel: ITableValueDataModel) {
        myModelHasSameDataSource = true
        setModel(tableModel)
        myModelHasSameDataSource = false
    }

    override fun setModel(tableModel: TableModel) {
        if (tableModel !is ITableValueDataModel) throw IllegalArgumentException("The model has to implement ITableValueDataModel.")

        val prevColumnCache = mutableMapOf<ColumnCacheKey, MyValueTableColumn>()
        val keepSortKeyState = myModelHasSameDataSource && rowSorter?.sortKeys?.isNotEmpty() == true

        if (myModelHasSameDataSource) {
            for (column in columnModel.columns) {
                if (column !is MyValueTableColumn) continue
                prevColumnCache[ColumnCacheKey.orgFrameIndex(column.orgFrameIndex)] = column
                if (keepSortKeyState) prevColumnCache[ColumnCacheKey.modelIndex(column.modelIndex)] = column
            }
        }

        super.setModel(tableModel)

        val newColumns = createColumns(tableModel, prevColumnCache)
        while (columnCount > 0) removeColumn(columnModel.getColumn(0))
        newColumns.forEach { addColumn(it) }

        rowSorter = MyExternalDataRowSorter(tableModel).apply {
            if (keepSortKeyState) {
                rowSorter?.sortKeys?.let { oldSortKeys ->
                    val newColumnCache = newColumns.associateBy { ColumnCacheKey.orgFrameIndex(it.orgFrameIndex) }
                    sortKeys = oldSortKeys.mapNotNull {
                        val orgFrameIndex = prevColumnCache[ColumnCacheKey.modelIndex(it.column)]?.orgFrameIndex
                            ?: return@mapNotNull null
                        val newModelIndex = newColumnCache[ColumnCacheKey.orgFrameIndex(orgFrameIndex)]?.modelIndex
                            ?: return@mapNotNull null
                        if (newModelIndex == -1) null else SortKey(newModelIndex, it.sortOrder)
                    }
                }
            }
        }

        if (myBaseClassIsFullyInitialized) {
            myColumnResizeBehavior.ensureLastColumnIsNotFixed()
            emptyText.text = if (rowCount > 0) "loading DataFrame" else "DataFrame is empty"
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
        myColumnResizeBehavior.beforeColumnLayout()

        if (scrollableTracksViewportWidth) {
            // if the width of all columns is less than the width of the viewport - fill viewport
            autoResizeMode.let {
                autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
                super.doLayout()
                autoResizeMode = it
            }
        } else {
            super.doLayout()
        }

        myColumnResizeBehavior.afterColumnLayout()
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

    private fun castedColumnAt(viewColumnIndex: Int): MyValueTableColumn {
        return columnModel.getColumn(viewColumnIndex) as MyValueTableColumn
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
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.CTRL_DOWN_MASK),
            "doNothing",
        )
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

    private inner class MyColumnResizeBehavior : IColumnResizeBehavior, PropertyChangeListener {
        private var myResizingColumnWasFixed: Boolean = false
        private var myResizingColumnStartWidth: Int = -1

        init {
            installActions()
            tableHeader.addPropertyChangeListener(this)
        }

        fun beforeColumnLayout() {
            if (columnCount < 1) return

            castedColumnAt(columnCount - 1).let {
                // try to reduce width of last column
                // (to shrink back after window width was reduced or when shrinking a large column)
                it.setMinMaxPreferredWidth(it.autoFitPreferredWidth, it.maxWidth, it.autoFitPreferredWidth)
            }

            (tableHeader.resizingColumn as MyValueTableColumn?)?.let {
                // set minWidth temporary to current width to prevent jumping column width during resizing of the viewport
                // (can happen when width of columns exceed width of viewport and a horizontal scrollbar should be displayed)
                it.setMinMaxPreferredWidth(it.width, MAX_COLUMN_WIDTH, it.width)
            }
        }

        fun afterColumnLayout() {
            if (columnCount < 1) return
            (tableHeader.resizingColumn as MyValueTableColumn?)?.let {
                // remove temporary minWidth
                it.setMinMaxPreferredWidth(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH, it.width)
            }
        }

        override fun markFixed(viewColumnIndex: Int) {
            // last column can't be adjusted
            if (viewColumnIndex == columnCount - 1) return
            castedColumnAt(viewColumnIndex).let {
                if (!it.hasFixedWidth) {
                    it.hasFixedWidth = true
                    it.setMinMaxPreferredWidth(it.width, it.width, it.width)
                    // to repaint the fixed-column indicator
                    tableHeader.repaint()
                }
            }
        }

        override fun clearFixed(viewColumnIndex: Int) {
            castedColumnAt(viewColumnIndex).let {
                if (it.hasFixedWidth) {
                    it.hasFixedWidth = false
                    it.setMinMaxPreferredWidth(it.autoFitPreferredWidth, MAX_COLUMN_WIDTH, it.autoFitPreferredWidth)
                    // to repaint the fixed-column indicator
                    tableHeader.repaint()
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
            return castedColumnAt(viewColumnIndex).hasFixedWidth
        }

        fun ensureLastColumnIsNotFixed() {
            if (columnCount > 0) {
                clearFixed(columnCount - 1)
            }
        }

        fun updateColumnWidthByPreparedRendererComponent(component: Component, viewColumnIndex: Int) {
            if (tableHeader.resizingColumn == null) {
                val tableColumn = castedColumnAt(viewColumnIndex)
                val renderer = tableColumn.headerRenderer ?: tableHeader.defaultRenderer
                val columnHeaderWidth = renderer.getTableCellRendererComponent(
                    this@MyValueTable,
                    tableColumn.headerValue,
                    false,
                    false,
                    -1,
                    viewColumnIndex,
                ).preferredSize.width + 4
                // only expand column - never shrink back
                val autoFitPreferredWidth = max(
                    max(columnHeaderWidth, component.preferredSize.width) + 2 * intercellSpacing.width,
                    tableColumn.autoFitPreferredWidth,
                )

                tableColumn.autoFitPreferredWidth = min(autoFitPreferredWidth, MAX_AUTO_EXPAND_COLUMN_WIDTH)

                if (!tableColumn.hasFixedWidth) {
                    tableColumn.minWidth = tableColumn.autoFitPreferredWidth
                }
            }
        }

        override fun propertyChange(event: PropertyChangeEvent) {
            if (event.source == tableHeader) {
                if (event.propertyName == "resizingColumn") {
                    val newColumn = event.newValue as MyValueTableColumn?
                    if (newColumn == null) {
                        val oldColumn = event.oldValue as MyValueTableColumn? ?: return
                        if (oldColumn.width != myResizingColumnStartWidth) {
                            markFixed(convertColumnIndexToView(oldColumn.modelIndex))
                        } else if (!myResizingColumnWasFixed) {
                            castedColumnAt(convertColumnIndexToView(oldColumn.modelIndex)).let {
                                it.setMinMaxPreferredWidth(
                                    it.autoFitPreferredWidth,
                                    MAX_COLUMN_WIDTH,
                                    it.autoFitPreferredWidth
                                )
                            }
                        }
                    } else {
                        castedColumnAt(convertColumnIndexToView(newColumn.modelIndex)).let {
                            myResizingColumnWasFixed = it.hasFixedWidth
                            // allow to reduce and increase the width
                            it.setMinMaxPreferredWidth(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH, it.width)
                        }
                    }
                }
            }
        }

        private fun installActions() {
            val fixedKeyCodes = listOf(KeyEvent.VK_PERIOD)
            installAction(
                MyToggleFixedForFocusedColumnAction(),
                "toggleFixedForFocusedColumn",
                fixedKeyCodes,
                0,
            )
            installAction(
                MyInvertFixedAction(),
                "invertFixed",
                fixedKeyCodes,
                KeyEvent.ALT_DOWN_MASK,
            )
            installAction(
                MyClearAllFixedAction(),
                "clearAllFixed",
                fixedKeyCodes,
                KeyEvent.ALT_DOWN_MASK or KeyEvent.CTRL_DOWN_MASK,
            )

            val expandBy = 10
            /*
            An us-keyboard can't generate a key event for VK_PLUS.
            A "+" on an us-keyboard is generated by pressing "Shift" + "=".
            Therefore, the action is also mapped to "=".
            */
            val expandKeyCodes = listOf(KeyEvent.VK_PLUS, KeyEvent.VK_EQUALS)
            installAction(
                MyAdjustFixedWidthAction { min(it + expandBy, MAX_AUTO_EXPAND_COLUMN_WIDTH) },
                "expandWidthForFocusedColumn",
                expandKeyCodes,
                0,
            )
            installAction(
                MyAdjustFixedWidthAction { MAX_AUTO_EXPAND_COLUMN_WIDTH },
                "expandWidthMaxForFocusedColumn",
                expandKeyCodes,
                KeyEvent.ALT_DOWN_MASK,
            )

            /*
            An us-keyboard can't generate a key event for VK_MINUS.
            A "-" on an us-keyboard is generated by pressing "Shift" + "_".
            Therefore, the action is also mapped to "_".
            */
            val shrinkKeyCodes = listOf(KeyEvent.VK_MINUS, KeyEvent.VK_UNDERSCORE)
            installAction(
                MyAdjustFixedWidthAction { max(it - expandBy, MIN_COLUMN_WIDTH) },
                "shrinkWidthForFocusedColumn",
                shrinkKeyCodes,
                0,
            )
            installAction(
                MyAdjustFixedWidthAction { MIN_COLUMN_WIDTH },
                "shrinkWidthMinForFocusedColumn",
                shrinkKeyCodes,
                KeyEvent.ALT_DOWN_MASK,
            )
        }

        private fun installAction(
            action: Action,
            key: String,
            keyCodes: List<Int>,
            @MagicConstant(flagsFromClass = java.awt.event.InputEvent::class)
            modifiers: Int,
        ) {
            getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).let { inputMap ->
                keyCodes.forEach { inputMap.put(KeyStroke.getKeyStroke(it, modifiers), key) }
            }
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
                if (columnCount < 1) return
                columnModel.selectionModel.leadSelectionIndex.let { index ->
                    if (index >= 0 && index != columnCount - 1) {
                        castedColumnAt(index).let { column ->
                            if (column.hasFixedWidth) {
                                newWidthProducer(column.width).let { column.setMinMaxPreferredWidth(it, it, it) }
                            } else {
                                newWidthProducer(column.autoFitPreferredWidth).let {
                                    column.autoFitPreferredWidth = it
                                    column.setMinMaxPreferredWidth(it, column.maxWidth, it)
                                }
                            }
                        }
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

        var autoFitPreferredWidth: Int = preferredWidth

        init {
            super.identifier = orgFrameIndex
        }

        val orgFrameIndex: Int
            get() = getIdentifier() as Int

        override fun setIdentifier(identifier: Any?) {
            throw UnsupportedOperationException("Identifier can't be overwritten.")
        }

        fun setMinMaxPreferredWidth(min: Int, max: Int, preferred: Int) {
            minWidth = min
            maxWidth = max
            preferredWidth = preferred
            if (width > max || width < min) {
               setWidth(width)
            }
        }

        fun copyStateFrom(source: MyValueTableColumn) {
            headerValue = source.headerValue
            cellRenderer = source.cellRenderer
            hasFixedWidth = source.hasFixedWidth
            autoFitPreferredWidth = source.autoFitPreferredWidth
            setMinMaxPreferredWidth(source.minWidth, source.maxWidth, source.preferredWidth)
            width = source.width
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
    private val maxColumnWidth: Int,
    model: ITableIndexDataModel,
) : MyTable<ITableIndexDataModel>(model) {

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

abstract class MyTable<M : ITableDataModel>(model: M) : JBTable(model) {

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