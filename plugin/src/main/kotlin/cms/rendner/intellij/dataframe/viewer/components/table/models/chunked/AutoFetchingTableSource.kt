/*
 * Copyright 2021-2026 cms.rendner (Daniel Schmidt)
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
package cms.rendner.intellij.dataframe.viewer.components.table.models.chunked

import cms.rendner.intellij.dataframe.viewer.components.table.DataFrameTable
import cms.rendner.intellij.dataframe.viewer.components.table.models.HeaderLabel
import cms.rendner.intellij.dataframe.viewer.components.table.models.IHeaderLabel
import cms.rendner.intellij.dataframe.viewer.notifications.ChunkValidationProblemNotification
import cms.rendner.intellij.dataframe.viewer.notifications.ErrorNotification
import cms.rendner.intellij.dataframe.viewer.python.bridge.Cell
import cms.rendner.intellij.dataframe.viewer.python.bridge.ChunkData
import cms.rendner.intellij.dataframe.viewer.python.bridge.ChunkDataRequest
import cms.rendner.intellij.dataframe.viewer.python.bridge.ChunkRegion
import cms.rendner.intellij.dataframe.viewer.python.bridge.FilterConfig
import cms.rendner.intellij.dataframe.viewer.python.bridge.FilterResult
import cms.rendner.intellij.dataframe.viewer.python.bridge.IPyPatchedStylerRef
import cms.rendner.intellij.dataframe.viewer.python.bridge.IPyTableSourceRef
import cms.rendner.intellij.dataframe.viewer.python.bridge.SortConfig
import cms.rendner.intellij.dataframe.viewer.python.bridge.StyleFunctionValidationProblem
import cms.rendner.intellij.dataframe.viewer.python.bridge.TableStructureColumn
import cms.rendner.intellij.dataframe.viewer.python.bridge.exceptions.TableSourceException
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.beans.PropertyChangeListener
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executor
import javax.swing.SwingUtilities
import javax.swing.event.ChangeListener
import javax.swing.event.TableColumnModelListener
import javax.swing.event.TableModelListener
import javax.swing.table.TableColumnModel
import javax.swing.table.TableModel
import kotlin.collections.set
import kotlin.math.max
import kotlin.math.min

private sealed class Request {
    data class TableValues(val chunkRegion: ChunkRegion) : Request()
    data class ApplySort(val config: SortConfig) : Request()
    data class ApplyFilter(val config: FilterConfig) : Request()
    data class ColumnStatistics(val columnIndex: Int) : Request()
}

interface AutoFetchingTableSourceListener {
    fun tableValuesFetched(chunkRegion: ChunkRegion) {}
    fun columnStatisticsFetched(columnIndex: Int) {}
    fun sortApplied() {}
    fun filterApplied(filterResult: FilterResult?, exception: TableSourceException?) {}
}

class AutoFetchingTableSource(
    private val executor: Executor,
    private val tableSource: IPyTableSourceRef,
    private val validateStyles: Boolean,
    private val project: Project?,
): Disposable {
    private val myPrioRequestsQueue = MyPriorityQueue()
    private var myPendingRequest: Request? = null

    private val myListeners = CopyOnWriteArraySet<AutoFetchingTableSourceListener>()

    private var myTableStructure = tableSource.initialTableStructure

    private var myViewportWatcher: MyDataFrameViewportWatcher? = null

    // todo: idea - only keep the last N fetched chunks to reduce memory consumption
    // (LRU cache or similar) -> put behind a feature switch

    private var myCanInteractWithTableSource: Boolean = true

    /**
     * The successfully loaded cell values.
     */
    private val myFetchedTableValues: MutableMap<ChunkRegion, List<List<Cell>>> = mutableMapOf()

    /**
     * Internal loading marker.
     */
    private val myLoadingColumnStatisticsIndicator = mapOf(Pair("status", "LOADING"))

    /**
     * The loaded column statistics.
     */
    private val myFetchedColumnStatistics: MutableMap<Int, Map<String, String>> = mutableMapOf()

    /**
     * The successfully loaded row headers.
     * Key is the first row of the chunkRegion to which the labels belong.
     */
    private val myFetchedRowHeaderLabels: MutableMap<Int, List<IHeaderLabel>> = mutableMapOf()

    /**
     * Tracks all regions which couldn't be loaded because of an error.
     */
    private val myFailedTableValues: MutableSet<ChunkRegion> = HashSet()

    /**
     * Dummy label for the not yet loaded header label.
     */
    private val myNotYetLoadedHeaderLabel = HeaderLabel(EMPTY_TABLE_HEADER_VALUE)

    /**
     * Dummy label for the not yet loaded cell values.
     */
    private val myNotYetLoadedValue = ""

    /**
     *
     * Dummy values for a not yet loaded cell.
     */
    private val myNotYetLoadedCell = Cell(myNotYetLoadedValue)

    private var myLastAppliedFilterExpr: String = ""

    override fun dispose() {
        myViewportWatcher?.let { Disposer.dispose(it) }
        myViewportWatcher = null
        myListeners.clear()
        myPrioRequestsQueue.clear()
        myFetchedTableValues.clear()
        myFetchedColumnStatistics.clear()
        myFetchedRowHeaderLabels.clear()
        myFailedTableValues.clear()
        myPendingRequest = null
    }

    fun setCanInteractWithTableSource(canInteractWithTableSource: Boolean) {
        myCanInteractWithTableSource = canInteractWithTableSource
        if (myCanInteractWithTableSource) {
            processNextRequest()
        }
    }

    fun addListener(listener: AutoFetchingTableSourceListener) {
        myListeners.add(listener)
    }

    fun removeListener(listener: AutoFetchingTableSourceListener) {
        myListeners.remove(listener)
    }

    fun getLastAppliedFilter(): String {
        return myLastAppliedFilterExpr
    }

    fun registerViewportWatcher(dataFrameTable: DataFrameTable) {
        myViewportWatcher?.let { Disposer.dispose(it) }
        myViewportWatcher = MyDataFrameViewportWatcher(dataFrameTable)
    }

    fun getCellAt(rowIndex: Int, columnIndex: Int): Cell {
        val chunkRegion = createChunkRegion(rowIndex, columnIndex)
        val chunk = getOrFetchChunk(chunkRegion) ?: return myNotYetLoadedCell
        return chunk[rowIndex - chunkRegion.firstRow][columnIndex - chunkRegion.firstColumn]
    }

    fun getColumnStatisticsAt(columnIndex: Int): Map<String, String>? {
        val statistics = myFetchedColumnStatistics[columnIndex]
        if (statistics == myLoadingColumnStatisticsIndicator) return null
        if (statistics != null) return statistics

        return myLoadingColumnStatisticsIndicator.also {
            myFetchedColumnStatistics[columnIndex] = it

            if (tableSource.isAlive()) {
                addRequest(Request.ColumnStatistics(columnIndex))
            }
        }
    }

    fun getRowHeaderLabelAt(rowIndex: Int): IHeaderLabel {
        val firstIndex = CHUNK_SIZE.getIndexOfFirstRowInChunk(rowIndex)
        val chunkHeaders = myFetchedRowHeaderLabels[firstIndex] ?: return myNotYetLoadedHeaderLabel
        return chunkHeaders[rowIndex - firstIndex]
    }

    fun getColumnHeaderAt(columnIndex: Int): TableStructureColumn {
        return myTableStructure.columnInfo.columns[columnIndex]
    }

    fun setSorting(config: SortConfig) {
        addRequest(Request.ApplySort(config))
    }

    fun setFilter(config: FilterConfig) {
        addRequest(Request.ApplyFilter(config))
    }

    private fun createChunkRegion(rowIndex: Int, columnIndex: Int): ChunkRegion {
        val firstRow = CHUNK_SIZE.getIndexOfFirstRowInChunk(rowIndex)
        val firstColumn = CHUNK_SIZE.getIndexOfFirstColumnInChunk(columnIndex)
        return ChunkRegion(
            firstRow,
            firstColumn,
            CHUNK_SIZE.rows,
            CHUNK_SIZE.columns,
        )
    }

    private fun getOrFetchChunk(chunkRegion: ChunkRegion): List<List<Cell>>? {
        val values = myFetchedTableValues[chunkRegion]
        if (values != null) return values
        processNextRequest()
        return null
    }

    private fun addRequest(request: Request) {
        if (request == myPendingRequest) {
            return
        }
        myPrioRequestsQueue.addRequest(request)
        processNextRequest()
    }

    private fun processNextRequest() {
        if (myCanInteractWithTableSource && myPendingRequest == null && tableSource.isAlive()) {
            val request = myPrioRequestsQueue.nextRequest() ?: nextFetchTableValuesRequest()
            if (request == null) return

            myPendingRequest = request

            when (request) {
                is Request.ColumnStatistics -> runFetchColumnStatistics(request)
                is Request.TableValues -> runFetchTableValues(request)
                is Request.ApplySort ->  runApplySort(request)
                is Request.ApplyFilter -> runApplyFilter(request)
            }
        }
    }

    private fun nextFetchTableValuesRequest(): Request.TableValues? {
        val visibleChunksIterator = myViewportWatcher?.computeVisibleChunkRegions() ?: return null

        var nextChunk: ChunkRegion? = null
        while (visibleChunksIterator.hasNext()) {
            nextChunk = visibleChunksIterator.next()
            if (!myFetchedTableValues.containsKey(nextChunk) && !myFailedTableValues.contains(nextChunk)) {
                return Request.TableValues(nextChunk)
            }
        }

        return null
    }

    private fun runApplyFilter(request: Request.ApplyFilter) {
        executor.execute {
            try {
                val result = tableSource.applyFilter(request.config)
                completeOnEdtAndStartNextRequest("ApplyFilter::resultHandler") {
                    clearAllFetchedDataAndPublish(request)
                    myLastAppliedFilterExpr = request.config.expr ?: ""
                    myTableStructure = result.tableStructure
                    notifyFilterApplied(result, null)
                }
            } catch (e: Exception) {
                completeOnEdtAndStartNextRequest("ApplyFilter:errorHandler") {
                    if (isCausedByProcessIsRunningException(e)) {
                        myPrioRequestsQueue.addRequest(request, false)
                    } else {
                        if (e is TableSourceException) {
                            notifyFilterApplied(null, e)
                        } else {
                            reportErrorToUser("Apply Filter failed.", e)
                        }
                    }
                }
            }
        }
    }

    private fun runApplySort(request: Request.ApplySort) {
        executor.execute {
            try {
                tableSource.applySort(request.config)
                completeOnEdtAndStartNextRequest("ApplySort::resultHandler") {
                    clearAllFetchedDataAndPublish(request)
                    notifySortApplied()
                }
            } catch (e: Exception) {
                completeOnEdtAndStartNextRequest("ApplySort::errorHandler") {
                    if (isCausedByProcessIsRunningException(e)) {
                        myPrioRequestsQueue.addRequest(request, false)
                    } else {
                        reportErrorToUser("Apply sort failed.", e)
                    }

                }
            }
        }
    }

    private fun runFetchColumnStatistics(request: Request.ColumnStatistics) {
        executor.execute {
            try {
                val statistics = tableSource.getColumnStatistics(request.columnIndex)
                completeOnEdtAndStartNextRequest("ColumStatistics::resultHandler") {
                    myFetchedColumnStatistics[request.columnIndex] = statistics
                    notifyColumnStatisticsFetched(request.columnIndex)
                }
            } catch (e: Exception) {
                completeOnEdtAndStartNextRequest("ColumStatistics::errorHandler") {
                    if (isCausedByProcessIsRunningException(e)) {
                        myPrioRequestsQueue.addRequest(request, false)
                    } else {
                        myFetchedColumnStatistics[request.columnIndex] =
                            mapOf(Pair("error", "could not load statistics"))
                        notifyColumnStatisticsFetched(request.columnIndex)

                        reportErrorToUser("Fetching column statistics failed.", e)
                    }
                }
            }
        }
    }

    private fun runFetchTableValues(request: Request.TableValues) {
        val dataRequest = ChunkDataRequest(
            withCells = true,
            withRowHeaders = myFetchedRowHeaderLabels[request.chunkRegion.firstRow] == null,
        )
        executor.execute {
            try {
                var chunkData: ChunkData?
                var problems: List<StyleFunctionValidationProblem>? = null

                if (validateStyles && tableSource is IPyPatchedStylerRef) {
                    val result = tableSource.validateAndComputeChunkData(request.chunkRegion, dataRequest)
                    chunkData = result.data
                    problems = result.problems
                } else {
                    chunkData = tableSource.computeChunkData(request.chunkRegion, dataRequest)
                }

                completeOnEdtAndStartNextRequest("TableValues::resultHandler") {

                    problems?.let { newProblems ->
                        if (newProblems.isNotEmpty()) {
                            ChunkValidationProblemNotification(newProblems).notify(project)
                        }
                    }

                    chunkData.rowHeaders?.let {
                        myFetchedRowHeaderLabels[request.chunkRegion.firstRow] = it
                    }
                    chunkData.cells?.let {
                        myFetchedTableValues[request.chunkRegion] = it
                    }

                    notifyDataFetched(request.chunkRegion)
                }
            } catch(e: Exception) {
                completeOnEdtAndStartNextRequest("TableValues::errorHandler") {
                    if (isCausedByProcessIsRunningException(e)) {
                        // if region is still needed it will be repainted
                        // which then results in a new load request
                        notifyDataFetched(request.chunkRegion)
                    } else {
                        myFailedTableValues.add(request.chunkRegion)
                        reportErrorToUser("Fetching table values failed.", e)
                    }
                }
            }
        }
    }

    private fun completeOnEdtAndStartNextRequest(title: String, runnable: Runnable) {
        SwingUtilities.invokeLater {

            try {
                runnable.run()
            } catch (e: Exception) {
                reportErrorToUser(title, e)
            }

            // Schedule next processing only after handler completes
            // This allows the handler to add new higher priority requests
            myPendingRequest = null
            processNextRequest()
        }
    }

    private fun clearAllFetchedDataAndPublish(request: Request) {
        when (request) {
            is Request.ApplyFilter -> {
                myFetchedTableValues.clear()
                myFetchedColumnStatistics.clear()
                myFetchedRowHeaderLabels.clear()
                myFailedTableValues.clear()
                myViewportWatcher?.invalidateComputedResult()
            }
            is Request.ApplySort -> {
                myFetchedTableValues.clear()
                myFetchedRowHeaderLabels.clear()
                myFailedTableValues.clear()
                myViewportWatcher?.invalidateComputedResult()
            }
            else -> throw IllegalArgumentException("Unsupported request type: $request")
        }

        val chunkRegion = ChunkRegion(
            0,
            0,
            myTableStructure.rowsCount,
            myTableStructure.columnsCount
        )
        notifyDataFetched(chunkRegion)
    }

    private fun notifyDataFetched(chunkRegion: ChunkRegion) {
        myListeners.forEach { it.tableValuesFetched(chunkRegion) }
    }

    private fun notifyColumnStatisticsFetched(columnIndex: Int) {
        myListeners.forEach { it.columnStatisticsFetched(columnIndex) }
    }

    private fun notifySortApplied() {
        myListeners.forEach { it.sortApplied() }
    }

    private fun notifyFilterApplied(filterResult: FilterResult?, exception: TableSourceException?) {
        myListeners.forEach { it.filterApplied(filterResult, exception) }
    }

    private fun shouldSwallowExceptionReporting(throwable: Throwable?): Boolean {
        if (throwable !is EvaluateException) return false
        return throwable.isCausedByProcessIsRunningException() || throwable.isCausedByDisconnectException()
    }

    private fun isCausedByProcessIsRunningException(throwable: Throwable?): Boolean {
        if (throwable !is EvaluateException) return false
        return throwable.isCausedByProcessIsRunningException()
    }

    private fun reportErrorToUser(title: String, throwable: Throwable) {
        if (shouldSwallowExceptionReporting(throwable)) return

        // If the evaluation fails, it is highly likely that it is caused by a bug.
        val notificationTitle = if (throwable is EvaluateException) "[plugin-bug] $title" else title

        ErrorNotification(
            notificationTitle,
            throwable.localizedMessage ?: "",
            throwable
        ).notify(project)
    }
}

private class MyPriorityQueue() {
    private var applySort: Request.ApplySort? = null
    private var applyFilter: Request.ApplyFilter? = null
    private var columnStatistics: Request.ColumnStatistics? = null

    fun addRequest(request: Request, replaceExisting: Boolean = true) {
        when (request) {
            is Request.ApplySort -> {
                if (applySort == null || replaceExisting) {
                    applySort = request
                }
            }
            is Request.ApplyFilter -> {
                if (applyFilter == null || replaceExisting) {
                    applyFilter = request
                }
            }
            is Request.ColumnStatistics -> {
                if (columnStatistics == null || replaceExisting) {
                    columnStatistics = request
                }
            }
            else -> throw IllegalStateException("Unsupported request $request")
        }
    }

    fun nextRequest(): Request? {
        if (columnStatistics != null) {
            return columnStatistics.also { columnStatistics = null }
        }
        if (applySort != null) {
            return applySort.also { applySort = null }
        }
        if (applyFilter != null) {
            return applyFilter.also { applyFilter = null }
        }
        return null
    }

    fun clear() {
        applySort = null
        applyFilter = null
        columnStatistics = null
    }
}

private class MyDataFrameViewportWatcher(private val dataFrameTable: DataFrameTable): Disposable {
    private var intersectingChunksIterator: Iterator<ChunkRegion>? = null

    init {
        ChangeListener { invalidateComputedResult() }.let {
            dataFrameTable.viewport.addChangeListener(it)
            Disposer.register(this) { dataFrameTable.viewport.removeChangeListener(it) }
        }

        val viewportTableModelListener = TableModelListener { invalidateComputedResult() }

        val viewportTableColumnModelListener = object : TableColumnModelListener {
            override fun columnAdded(e: javax.swing.event.TableColumnModelEvent?) {
                invalidateComputedResult()
            }

            override fun columnRemoved(e: javax.swing.event.TableColumnModelEvent?) {
                invalidateComputedResult()
            }

            override fun columnMoved(e: javax.swing.event.TableColumnModelEvent?) {
                invalidateComputedResult()
            }

            override fun columnMarginChanged(e: javax.swing.event.ChangeEvent?) {
                invalidateComputedResult()
            }

            override fun columnSelectionChanged(e: javax.swing.event.ListSelectionEvent?) {
                // Selection changes don't affect viewport
            }
        }

        val viewportTablePropertyChangeListener = PropertyChangeListener { evt ->
            when (evt.propertyName) {
                "model" -> {
                    (evt.oldValue as? TableModel)?.removeTableModelListener(viewportTableModelListener)
                    (evt.newValue as? TableModel)?.addTableModelListener(viewportTableModelListener)
                }
                "columnModel" -> {
                    (evt.oldValue as? TableColumnModel)?.removeColumnModelListener(viewportTableColumnModelListener)
                    (evt.newValue as? TableColumnModel)?.addColumnModelListener(viewportTableColumnModelListener)
                }
                else -> invalidateComputedResult()
            }
        }
        dataFrameTable.getValuesTable().let { viewportTable ->
            viewportTable.addPropertyChangeListener("rowHeight", viewportTablePropertyChangeListener)
            viewportTable.addPropertyChangeListener("columnModel", viewportTablePropertyChangeListener)
            viewportTable.addPropertyChangeListener("model", viewportTablePropertyChangeListener)

            viewportTable.model?.addTableModelListener(viewportTableModelListener)
            viewportTable.columnModel.addColumnModelListener(viewportTableColumnModelListener)

            Disposer.register(this) {
                viewportTable.removePropertyChangeListener("rowHeight", viewportTablePropertyChangeListener)
                viewportTable.removePropertyChangeListener("columnModel", viewportTablePropertyChangeListener)
                viewportTable.removePropertyChangeListener("model", viewportTablePropertyChangeListener)

                viewportTable.model?.removeTableModelListener(viewportTableModelListener)
                viewportTable.columnModel.removeColumnModelListener(viewportTableColumnModelListener)
            }
        }
    }

    fun invalidateComputedResult() {
        intersectingChunksIterator = null
    }

    override fun dispose() {
        invalidateComputedResult()
    }

    fun computeVisibleChunkRegions(): Iterator<ChunkRegion>? {
        if (intersectingChunksIterator != null) return intersectingChunksIterator

        val viewRect = dataFrameTable.viewport.viewRect

        val rowHeight = dataFrameTable.getValuesTable().rowHeight
        val columnModel = dataFrameTable.getValuesTable().columnModel

        val firstVisibleRow = max(0, viewRect.y / rowHeight)
        val lastVisibleRow = min(
            dataFrameTable.getRowCount() - 1,
            (viewRect.y + viewRect.height) / rowHeight
        )

        var firstVisibleColumn = -1
        var lastVisibleColumn = -1
        var currentWidth = 0
        val maxWidth = viewRect.x + viewRect.width
        for (i in 0..< columnModel.columnCount) {
            lastVisibleColumn = i
            currentWidth += columnModel.getColumn(i).getWidth()
            if (firstVisibleColumn == -1 && currentWidth >= viewRect.x) {
                firstVisibleColumn = i
            }
            if (currentWidth >= maxWidth) {
                break
            }
        }
        if (firstVisibleColumn == -1) {
            return null
        }

        val chunkRegion = computeChunkRegion(
            firstVisibleRow,
            lastVisibleRow,
            firstVisibleColumn,
            lastVisibleColumn
        )

        val chunks = generateIntersectingChunks(
            chunkRegion,
            dataFrameTable.getRowCount(),
            dataFrameTable.getColumnCount()
        )

        return chunks.iterator().also {
            intersectingChunksIterator = it
        }
    }

    private fun computeChunkRegion(
        firstRow: Int,
        lastRow: Int,
        firstColumn: Int,
        lastColumn: Int
    ): ChunkRegion {
        val firstChunkRow = CHUNK_SIZE.getIndexOfFirstRowInChunk(firstRow)
        val lastChunkRow = CHUNK_SIZE.getIndexOfFirstRowInChunk(lastRow)
        val firstChunkColumn = CHUNK_SIZE.getIndexOfFirstColumnInChunk(firstColumn)
        val lastChunkColumn = CHUNK_SIZE.getIndexOfFirstColumnInChunk(lastColumn)

        return ChunkRegion(
            firstRow = firstChunkRow,
            firstColumn = firstChunkColumn,
            numberOfRows = lastChunkRow - firstChunkRow + CHUNK_SIZE.rows,
            numberOfColumns = lastChunkColumn - firstChunkColumn + CHUNK_SIZE.columns
        )
    }

    private fun generateIntersectingChunks(
        region: ChunkRegion,
        totalRows: Int,
        totalColumns: Int
    ): List<ChunkRegion> {
        val chunks = mutableListOf<ChunkRegion>()

        // align the starting positions to chunk boundaries
        val firstChunkRow = CHUNK_SIZE.getIndexOfFirstRowInChunk(region.firstRow)
        val firstChunkColumn = CHUNK_SIZE.getIndexOfFirstColumnInChunk(region.firstColumn)

        val lastRow = min(region.firstRow + region.numberOfRows, totalRows)
        val lastColumn = min(region.firstColumn + region.numberOfColumns, totalColumns)

        var row = firstChunkRow
        while (row < lastRow) {
            var col = firstChunkColumn
            while (col < lastColumn) {
                chunks.add(ChunkRegion(
                    firstRow = row,
                    firstColumn = col,
                    numberOfRows = CHUNK_SIZE.rows,
                    numberOfColumns = CHUNK_SIZE.columns
                ))

                col += CHUNK_SIZE.columns
            }
            row += CHUNK_SIZE.rows
        }

        return chunks
    }
}
