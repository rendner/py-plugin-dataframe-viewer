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
package cms.rendner.intellij.dataframe.viewer.components

import cms.rendner.intellij.dataframe.viewer.components.renderer.IndexRowHeaderRenderer
import cms.rendner.intellij.dataframe.viewer.components.renderer.ValueColumnHeaderRendererWithDtype
import cms.rendner.intellij.dataframe.viewer.components.renderer.ValueCellRenderer
import cms.rendner.intellij.dataframe.viewer.components.renderer.ValueColumnHeaderRenderer
import cms.rendner.intellij.dataframe.viewer.models.*
import cms.rendner.intellij.dataframe.viewer.models.events.DataFrameTableModelEvent
import com.intellij.ide.IdeTooltip
import com.intellij.ide.IdeTooltipManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import org.intellij.lang.annotations.MagicConstant
import java.awt.*
import java.awt.datatransfer.StringSelection
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

class DataFrameTable(showDType: Boolean = false): JBScrollPane() {

    private var myDataFrameModel: IDataFrameModel = EmptyDataFrameModel()
    private var myIndexTable: MyIndexTable? = null
    private val myValuesTable: MyValuesTable = MyValuesTable(showDType, myDataFrameModel.getValuesDataModel())

    init {
        setViewportView(myValuesTable)
        minimumSize = Dimension(MIN_TABLE_WIDTH, 200)
    }

    fun getValuesTable() = myValuesTable

    fun getPreferredFocusedComponent(): JComponent = myValuesTable

    fun getRowHeight() = myValuesTable.rowHeight

    fun getRowCount() = myValuesTable.rowCount

    fun getColumnCount() = myValuesTable.columnCount

    fun getDataFrameModel() = myDataFrameModel

    fun setDataFrameModel(model: IDataFrameModel) {
        if (model == myDataFrameModel) return

        val oldFingerprint = myDataFrameModel.getFingerprint()
        val newFingerprint = model.getFingerprint()
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

        model.getIndexDataModel().let { newIndexModel ->
            if (newIndexModel == null) {
                myIndexTable = null
                setRowHeaderView(null)
                setCorner(ScrollPaneConstants.UPPER_LEFT_CORNER, null)
            } else {
                myIndexTable.let {
                    if (it == null) {
                        myIndexTable = MyIndexTable(
                            myValuesTable,
                            max(MIN_COLUMN_WIDTH, MIN_TABLE_WIDTH - MIN_COLUMN_WIDTH),
                        ).also { indexTable ->
                            indexTable.setModel(newIndexModel)
                            setRowHeaderView(indexTable)
                            setCorner(ScrollPaneConstants.UPPER_LEFT_CORNER, indexTable.tableHeader)
                        }
                    } else {
                        it.setModel(newIndexModel)
                    }
                }
            }
        }

        model.getValuesDataModel().let {
            if (isSameDataSource) {
                myValuesTable.setModelWithSameDataSource(it)
            } else {
                myValuesTable.setModel(it)
            }
            setColumnHeaderView(myValuesTable.tableHeader)
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
            myValuesTable.selectionModel.leadSelectionIndex,
            myValuesTable.columnModel.selectionModel.leadSelectionIndex,
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
        if (myValuesTable.isEmpty) return

        val lastRowIndex = max(0, myValuesTable.rowCount - 1)
        val lastColIndex = max(0, myValuesTable.columnCount - 1)

        val sanitized = CellPosition(
            min(lastRowIndex, max(0, position.rowIndex)),
            min(lastColIndex, max(0, position.columnIndex)),
        )
        // apply without scrolling
        myValuesTable.autoscrolls = false
        myValuesTable.changeSelection(sanitized.rowIndex, sanitized.columnIndex, false, false)
        myValuesTable.autoscrolls = true
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

class MyValuesTable(
    showDType: Boolean,
    model: IDataFrameValuesDataModel,
    ) : MyTable<IDataFrameValuesDataModel>(model) {
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


        if (showDType) {
            tableHeader?.apply {
                defaultRenderer = ValueColumnHeaderRendererWithDtype(
                    createDefaultHeaderRenderer(),
                    createDefaultHeaderRenderer(),
                )
            }
        }
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

        ApplicationManager.getApplication()?.let {
            if (!it.isHeadlessEnvironment) {
                registerSortKeyBindings()
                registerCopyKeyBindings()
                disableProblematicKeyBindings()
            }
        }
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
            fun uniqueColumnId(indexToCache: Int) = ColumnCacheKey("uniqueColumnId", indexToCache)
            fun modelIndex(indexToCache: Int) = ColumnCacheKey("modelIndex", indexToCache)
        }
    }

    private fun createColumns(
        valueModel: IDataFrameValuesDataModel,
        prevTableColumnCache: Map<ColumnCacheKey, MyValueTableColumn>,
    ): List<MyValueTableColumn> {
        val result = mutableListOf<MyValueTableColumn>()
        for (modelIndex in 0 until valueModel.columnCount) {
            val uniqueColumnId = valueModel.getUniqueColumnIdAt(modelIndex)
            result.add(MyValueTableColumn(modelIndex, uniqueColumnId).apply {
                this.minWidth = MIN_COLUMN_WIDTH
                if (uniqueColumnId != -1) {
                    prevTableColumnCache[ColumnCacheKey.uniqueColumnId(uniqueColumnId)]?.let { prevColumn ->
                        this.copyStateFrom(prevColumn)
                    }
                }
            })
        }
        return result
    }

    fun setModelWithSameDataSource(tableModel: IDataFrameValuesDataModel) {
        myModelHasSameDataSource = true
        setModel(tableModel)
        myModelHasSameDataSource = false
    }

    override fun setModel(model: TableModel) {
        if (model !is IDataFrameValuesDataModel) throw IllegalArgumentException("The model has to implement ITableValueDataModel.")

        model.enableDataFetching(false)

        val prevColumnCache = mutableMapOf<ColumnCacheKey, MyValueTableColumn>()
        val keepSortKeyState = myModelHasSameDataSource && rowSorter?.sortKeys?.isNotEmpty() == true

        if (myModelHasSameDataSource) {
            for (column in columnModel.columns) {
                if (column !is MyValueTableColumn) continue
                if (column.uniqueColumnId != -1) {
                    prevColumnCache[ColumnCacheKey.uniqueColumnId(column.uniqueColumnId)] = column
                }
                if (keepSortKeyState) prevColumnCache[ColumnCacheKey.modelIndex(column.modelIndex)] = column
            }
        }

        super.setModel(model)

        val newColumns = createColumns(model, prevColumnCache)
        while (columnCount > 0) removeColumn(columnModel.getColumn(0))
        newColumns.forEach { addColumn(it) }

        rowSorter = if (model.isSortable()) {
            MyExternalDataRowSorter(model).apply {
                if (keepSortKeyState) {
                    rowSorter?.sortKeys?.let { oldSortKeys ->
                        // it is OK, to add invalid cache entries (with an "uniqueColumnId" of -1) to the new cache
                        // because the cache is only used temporary and "prevColumnCache" has no invalid entries
                        val newColumnCache = newColumns.associateBy { ColumnCacheKey.uniqueColumnId(it.uniqueColumnId) }
                        sortKeys = oldSortKeys.mapNotNull {
                            val uniqueColumnId = prevColumnCache[ColumnCacheKey.modelIndex(it.column)]?.uniqueColumnId
                                ?: return@mapNotNull null
                            val newModelIndex = newColumnCache[ColumnCacheKey.uniqueColumnId(uniqueColumnId)]?.modelIndex
                                ?: return@mapNotNull null
                            if (newModelIndex == -1) null else SortKey(newModelIndex, it.sortOrder)
                        }
                    }
                }
            }
        } else null

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

    override fun createDefaultDataModel(): TableModel {
        return EmptyDataFrameModel().getValuesDataModel()
    }

    private fun castedColumnAt(viewColumnIndex: Int): MyValueTableColumn {
        return columnModel.getColumn(viewColumnIndex) as MyValueTableColumn
    }

    private fun registerCopyKeyBindings() {
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).apply {
            put(KeyStroke.getKeyStroke(KeyEvent.VK_C, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "copySelectedCellValue")
        }
        actionMap.apply {
            put("copySelectedCellValue", MyCopySelectedCellValueAction())
        }
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

    private class MyCopySelectedCellValueAction: AbstractAction() {
        override fun actionPerformed(event: ActionEvent) {
            (event.source as MyValuesTable).let { table ->
                table.model?.let { tableModel ->
                    val row = table.selectedRow
                    val col = table.selectedColumn
                    if (row != -1 && col != -1) {
                        val cellValue = tableModel.getValueAt(row, col)
                        // todo: use CopyPasteManager.getInstance().copyTextToClipboard
                        //  when setting min version for plugin >= 2024.1
                        try {
                            CopyPasteManager.getInstance().setContents(StringSelection(cellValue.text()))
                        } catch (ignore: Exception) { }
                    }
                }
            }
        }
    }

    private class DoNothingAction : AbstractAction() {
        override fun actionPerformed(e: ActionEvent?) {}
    }

    private class MySortColumnsAction(
        private val sortOrder: SortOrder,
        private val shiftKeyIsDown: Boolean
    ) : AbstractAction() {
        override fun accept(sender: Any?) = sender is MyValuesTable

        override fun actionPerformed(event: ActionEvent) {
            (event.source as MyValuesTable).let { table ->
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
            makeColumnWidthStaticAndMarkFixed(castedColumnAt(viewColumnIndex))
        }

        override fun clearFixed(viewColumnIndex: Int) {
            makeColumnWidthResizeableAndClearFixed(castedColumnAt(viewColumnIndex))
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
                makeColumnWidthResizeableAndClearFixed(castedColumnAt(columnCount - 1))
            }
        }

        fun updateColumnWidthByPreparedRendererComponent(component: Component, viewColumnIndex: Int) {
            if (tableHeader.resizingColumn == null) {
                val tableColumn = castedColumnAt(viewColumnIndex)
                val renderer = tableColumn.headerRenderer ?: tableHeader.defaultRenderer
                val columnHeaderWidth = renderer.getTableCellRendererComponent(
                    this@MyValuesTable,
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
                            makeColumnWidthStaticAndMarkFixed(oldColumn)
                        } else if (!myResizingColumnWasFixed) {
                            makeColumnWidthResizeableAndClearFixed(oldColumn)
                        }
                    } else {
                        myResizingColumnWasFixed = newColumn.hasFixedWidth
                        // allow to reduce and increase the width
                        newColumn.setMinMaxPreferredWidth(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH, newColumn.width)
                    }
                }
            }
        }

        private fun makeColumnWidthStaticAndMarkFixed(column: MyValueTableColumn) {
            // last column can't be marked as fixed
            if (convertColumnIndexToView(column.modelIndex) == columnCount - 1) return
            column.setMinMaxPreferredWidth(column.width, column.width, column.width)
            if (!column.hasFixedWidth) {
                column.hasFixedWidth = true
                // to repaint the fixed-column indicator
                tableHeader.repaint()
            }
        }

        private fun makeColumnWidthResizeableAndClearFixed(column: MyValueTableColumn) {
            column.setMinMaxPreferredWidth(column.autoFitPreferredWidth, MAX_COLUMN_WIDTH, column.autoFitPreferredWidth)
            if (column.hasFixedWidth) {
                column.hasFixedWidth = false
                // to repaint the fixed-column indicator
                tableHeader.repaint()
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
        uniqueColumnId: Int,
        var hasFixedWidth: Boolean = false,
    ) : TableColumn(modelIndex) {

        var autoFitPreferredWidth: Int = preferredWidth

        init {
            super.identifier = uniqueColumnId
        }

        val uniqueColumnId: Int
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

class MyExternalDataRowSorter(private val model: IDataFrameValuesDataModel) : RowSorter<IDataFrameValuesDataModel>() {

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
    private val mainTable: JTable,
    private val maxColumnWidth: Int,
) : MyTable<IDataFrameIndexDataModel>() {

    init {
        // disable automatic row height adjustment - use the value from the mainTable
        setMaxItemsForSizeCalculation(0)
        rowHeight = mainTable.rowHeight

        setSelectionModel(mainTable.selectionModel)
        isFocusable = false
        autoResizeMode = JTable.AUTO_RESIZE_OFF

        setDefaultRenderer(Object::class.java, IndexRowHeaderRenderer(createDefaultHeaderRenderer()))
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

    override fun setModel(model: TableModel) {
        super.setModel(model)
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

    private fun adjustPreferredScrollableViewportSize() {
        preferredScrollableViewportSize = preferredSize
    }

    override fun createDefaultDataModel(): TableModel {
        return EmptyDataFrameModel().getIndexDataModel()
    }
}

abstract class MyTable<M : TableModel> (model: M? = null) : JBTable(model) {

    init {
        emptyText.text = ""
        cellSelectionEnabled = false
        rowSelectionAllowed = false

        rowSorter = null
        tableHeader?.apply{
            reorderingAllowed = false
            defaultRenderer = ValueColumnHeaderRenderer(createDefaultHeaderRenderer())
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
        if (event is DataFrameTableModelEvent) {
            val payload = event.payload
            if (DataFrameTableModelEvent.UpdateType.COLUMN_STATISTICS == payload.type) {
                (tableHeader as? MyTable<*>.MyTableHeader)?.let {
                    for (modelColumnIndex in payload.firstColumn..payload.lastColumn) {
                        it.maybeTooltipChanged(convertColumnIndexToView(modelColumnIndex))
                    }
                }
            } else {
                super.onTableChanged(event)
            }
        } else {
            super.onTableChanged(event)
        }
    }

    override fun setModel(model: TableModel) {
        (tableHeader as? MyTable<*>.MyTableHeader)?.resetTooltip()
        super.setModel(model)
    }

    protected fun createDefaultHeaderRenderer(): TableCellRenderer {
        return getCastedTableHeader().createDefaultRenderer()
    }

    private fun getCastedTableHeader(): MyTable<*>.MyTableHeader {
        return getTableHeader() as MyTable<*>.MyTableHeader
    }

    private inner class MyTableHeader : JBTable.JBTableHeader() {

        private var myTooltip = MyHeaderTooltip(this)
        private var myMouseIsInsideHeader: Boolean = false

        init {
            if (ApplicationManager.getApplication() != null) {
                this.putClientProperty(IdeTooltip.TOOLTIP_DISMISS_DELAY_KEY, 25_000)
                IdeTooltipManager.getInstance().setCustomTooltip(this, myTooltip)
            }
        }

        override fun processMouseEvent(e: MouseEvent?) {
            if (e?.id == MouseEvent.MOUSE_RELEASED) setRowSorterShiftKeyFlag(e.isShiftDown)
            super.processMouseEvent(e)
            when (e?.id) {
                MouseEvent.MOUSE_CLICKED -> setRowSorterShiftKeyFlag(false)
                MouseEvent.MOUSE_PRESSED -> resetTooltip()
                MouseEvent.MOUSE_ENTERED -> myMouseIsInsideHeader = true
                MouseEvent.MOUSE_EXITED -> myMouseIsInsideHeader = false
            }
        }

        override fun setResizingColumn(column: TableColumn?) {
            val oldValue = resizingColumn
            super.setResizingColumn(column)
            if (oldValue != resizingColumn) {
                firePropertyChange("resizingColumn", oldValue, resizingColumn)
            }
        }

        fun resetTooltip() {
            myTooltip.reset()
        }

        fun maybeTooltipChanged(viewColumnIndex: Int) {
            if (myMouseIsInsideHeader) {
                myTooltip.maybeTextChanged(viewColumnIndex)
            }
        }

        override fun getToolTipText(event: MouseEvent): String {
            myTooltip.updateHoveredColumn(this.columnAtPoint(event.point), event.point)
            return ""
        }

        private fun setRowSorterShiftKeyFlag(isDown: Boolean) {
            table.rowSorter?.let {
                if (it is MyExternalDataRowSorter) {
                    it.setShiftKeyIsDownDuringAction(isDown)
                }
            }
        }

        public override fun createDefaultRenderer(): TableCellRenderer {
            return super.createDefaultRenderer()
        }
    }

    private inner class MyHeaderTooltip(component: JComponent) : IdeTooltip(component, Point(), null, component) {

        private var myViewColumnIndex: Int = -1
        private var myMaybeWaitingForText: Boolean = false

        init {
            preferredPosition = Balloon.Position.below
        }

        fun updateHoveredColumn(viewColumnIndex: Int, p: Point) {
            if (viewColumnIndex != myViewColumnIndex) {
                myViewColumnIndex = viewColumnIndex
                myMaybeWaitingForText = false
                this.hide()
            }
            point = p
        }

        fun reset() {
            updateHoveredColumn(-1, Point())
        }

        fun maybeTextChanged(viewColumnIndex: Int) {
            if (viewColumnIndex == myViewColumnIndex && myMaybeWaitingForText) {
                IdeTooltipManager.getInstance().show(this, true)
            }
        }

        override fun onHidden() {
            myMaybeWaitingForText = false
        }

        override fun beforeShow(): Boolean {
            val text = createText()
            if (text == null) {
                myMaybeWaitingForText = true
                return false
            }
            if (tipComponent == null) {
                tipComponent = JBLabel(text)
            } else (tipComponent as JBLabel).text = text
            return true
        }

        private fun createText(): String? {
            val modelColumnIndex = convertColumnIndexToModel(myViewColumnIndex)
            model?.let { model ->
                val sb = StringBuilder("<html>")
                val hexColor = ColorUtil.toHtmlColor(
                    ColorUtil.mix(
                        colorFromUI("ToolTip.background", Color.BLACK),
                        colorFromUI("ToolTip.foreground", Color.WHITE),
                        0.6
                    )
                )
                val colorizedText = { text: String -> "<font color='${hexColor}'>$text</font>" }
                val separator = colorizedText("/")

                if (model is IDataFrameIndexDataModel) {
                    model.getLegendHeaders().let {
                        if (it.column is LeveledHeaderLabel) {
                            sb.append("${colorizedText("levels &rarr;: ")} ${it.column.text(separator)}")
                            sb.append("<br/>")
                        }
                        if (it.row is LeveledHeaderLabel) {
                            sb.append("${colorizedText("levels &darr;: ")} ${it.row.text(separator)}")
                            sb.append("<br/>")
                        }
                    }
                } else if (model is IDataFrameValuesDataModel) {
                    // If no statistics is there no tooltip is shown.
                    // This prevents jumping tooltip, if tooltip without statistics is already
                    // opened and the tip is updated with the lazy loaded statistics.
                    val statistics = model.getColumnStatisticsAt(modelColumnIndex) ?: return null

                    model.getColumnLabelAt(modelColumnIndex).let {
                        val label = if (it is LeveledHeaderLabel) it.text(separator) else it.text()
                        sb.append("<h3 style='margin-top: 0px; margin-bottom: 6px'>${label}</h3>")
                        sb.append("${colorizedText("dtype: ")} ${model.getColumnDtypeAt(modelColumnIndex)}")
                        sb.append("<br/>")
                    }
                    model.getLegendHeader().let {
                        if (it is LeveledHeaderLabel) {
                            sb.append("${colorizedText("levels: ")} ${it.text(separator)}")
                            sb.append("<br/>")
                        }
                    }

                    if (statistics.isNotEmpty()) {
                        sb.append("<hr style='margin-top: 3px; margin-bottom: 3px'>")
                        sb.append("<h3 style='margin-top: 0px; margin-bottom: 0px'>Statistics</h3>")
                        sb.append("<table>")
                        statistics.forEach { (k, v) ->
                            sb.append("<tr>")
                            sb.append("<td>${colorizedText("$k: ")}</td>")
                            sb.append("<td width='8' />")
                            // Use 'nowrap' to not break the formatting in case values are too long.
                            sb.append("<td style='white-space: nowrap;'>$v</td>")
                            sb.append("</tr>")
                        }
                        sb.append("</table>")
                    }
                }

                return sb.append("</html>").toString().let {
                    if (it == "<html></html>") null else it
                }
            }
            return null
        }
    }
}