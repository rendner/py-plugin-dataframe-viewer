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
package cms.rendner.intellij.dataframe.viewer.models.chunked

import cms.rendner.intellij.dataframe.viewer.models.*
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.converter.ChunkDataConverter.Companion.convertHeaderLabel
import cms.rendner.intellij.dataframe.viewer.models.events.DataFrameTableModelEvent
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.*
import cms.rendner.intellij.dataframe.viewer.python.bridge.Cell
import java.awt.Rectangle
import java.lang.Integer.min
import javax.swing.RowSorter.SortKey
import javax.swing.SortOrder
import javax.swing.SwingUtilities
import javax.swing.table.AbstractTableModel

/**
 * Fetches the model data chunkwise via a [chunkDataLoader].
 *
 * @param tableStructure describes the structure of the model.
 * @param chunkDataLoader used for lazy data loading.
 * @param chunkSize size of the chunks to load.
 * @param sortable true if model can be sorted.
 * @param hasIndexLabels true if model should provide an [IDataFrameIndexDataModel].
 */
class LazyDataFrameModel(
    private val tableStructure: TableStructure,
    private val chunkDataLoader: IModelDataLoader,
    private val chunkSize: ChunkSize,
    private val sortable: Boolean = false,
    private val hasIndexLabels: Boolean = false,
) : IDataFrameModel,
    IModelDataLoader.IResultHandler,
    IModelDataLoader.ILoadRequestCreator {

    private val myValuesModel = ValuesModel(this)
    private val myIndexModel = IndexModel(this)

    private val myLegendHeaders: LegendHeaders

    /**
     * The successfully loaded cell values.
     */
    private val myFetchedChunkValues: MutableMap<ChunkRegion, IChunkValues> = mutableMapOf()

    /**
     * Internal loading marker.
     */
    private val myLoadingColumnStatisticsIndicator = mapOf(Pair("LOADING", ""))

    /**
     * The loaded column statistics.
     */
    private val myFetchedColumnStatistics: MutableMap<Int, Map<String, String>> = mutableMapOf()

    /**
     * The successfully loaded row headers.
     */
    private val myFetchedChunkRowHeaderLabels: MutableMap<Int, List<IHeaderLabel>> = mutableMapOf()

    /**
     * Tracks all regions which couldn't be loaded because of an error.
     */
    private val myFailedChunks: MutableSet<ChunkRegion> = HashSet()

    /**
     * Tracks all pending load requests, to send only one request per chunk region.
     */
    private val myPendingChunks: MutableSet<ChunkRegion> = HashSet()

    /**
     * Dummy label for the not yet loaded header label.
     */
    private val myNotYetLoadedHeaderLabel = HeaderLabel(EMPTY_TABLE_HEADER_VALUE)

    /**
     * Dummy label for the not yet loaded cell values.
     */
    private val myNotYetLoadedValue = ""

    /**
     * Dummy values for the not yet loaded cell values of a chunk region.
     */
    private val myNotYetLoadedChunkValues = ChunkValuesPlaceholder(Cell(myNotYetLoadedValue))

    /**
     * Tracks the region of rejected load requests, to re-request the loading at a later time.
     */
    private var myRejectedChunkRegions = Rectangle()

    private var disposed: Boolean = false

    private var myDataFetchingEnabled = false

    init {
        chunkDataLoader.addResultHandler(this)
        chunkDataLoader.setLoadRequestCreator(this)
        myLegendHeaders = tableStructure.columnInfo.legend?.let {
            LegendHeaders(
                convertHeaderLabel(it.index),
                convertHeaderLabel(it.column)
            )
        } ?: LegendHeaders(myNotYetLoadedHeaderLabel, myNotYetLoadedHeaderLabel)
    }

    private fun setValueSortKeys(sortKeys: List<SortKey>) {
        if (!sortable) {
            throw IllegalStateException("Model is not sortable.")
        }
        val sortCriteria = sortKeys.fold(Pair(mutableListOf<Int>(), mutableListOf<Boolean>()))
        { pair, sortKey ->
            pair.first.add(sortKey.column)
            pair.second.add(sortKey.sortOrder == SortOrder.ASCENDING)
            pair
        }.let {
            SortCriteria(it.first, it.second)
        }
        chunkDataLoader.setSorting(sortCriteria)
        clearAllFetchedData()
    }

    private fun clearAllFetchedData() {
        // "myPendingRequests" don't have to be cleared
        myFailedChunks.clear()
        myFetchedChunkValues.clear()
        myFetchedChunkRowHeaderLabels.clear()

        val chunkRegion = ChunkRegion(0, 0, tableStructure.rowsCount, tableStructure.columnsCount)
        fireIndexModelValuesUpdated(chunkRegion)
        fireValuesModelValuesUpdated(chunkRegion)
    }

    override fun dispose() {
        if (!disposed) {
            disposed = true
            myFailedChunks.clear()
            myPendingChunks.clear()
            myFetchedChunkValues.clear()
            myFetchedChunkRowHeaderLabels.clear()
            myFetchedColumnStatistics.clear()
        }
    }

    override fun getValuesDataModel(): IDataFrameValuesDataModel {
        return myValuesModel
    }

    override fun getIndexDataModel(): IDataFrameIndexDataModel? {
        return if (hasIndexLabels) myIndexModel else null
    }

    override fun getFingerprint() = tableStructure.fingerprint

    private fun enableDataFetching(enabled: Boolean) {
        myDataFetchingEnabled = enabled
    }

    private fun getCellAt(rowIndex: Int, columnIndex: Int): Cell {
        val chunkRegion = createChunkRegion(rowIndex, columnIndex)
        return getOrFetchChunk(chunkRegion)
            .getValue(
                rowIndex - chunkRegion.firstRow,
                columnIndex - chunkRegion.firstColumn,
            )
    }

    private fun getValueAt(rowIndex: Int, columnIndex: Int): String {
        return getCellAt(rowIndex, columnIndex).value
    }

    private fun getCellMetaAt(rowIndex: Int, columnIndex: Int): String? {
        return getCellAt(rowIndex, columnIndex).meta
    }

    private fun getRowHeaderLabelAt(rowIndex: Int): IHeaderLabel {
        checkIndex("RowIndex", rowIndex, tableStructure.rowsCount)
        val firstIndex = getIndexOfFirstRowInChunk(rowIndex)
        val chunkHeaders = myFetchedChunkRowHeaderLabels[firstIndex] ?: return myNotYetLoadedHeaderLabel
        return chunkHeaders[rowIndex - firstIndex]
    }

    private fun getColumnStatisticsAt(columnIndex: Int): Map<String, String>? {
        val statistics = myFetchedColumnStatistics[columnIndex]

        if (statistics == null && !disposed && chunkDataLoader.isAlive()) {
            myFetchedColumnStatistics[columnIndex] = myLoadingColumnStatisticsIndicator
            chunkDataLoader.loadColumnStatistics(columnIndex)
        }

        return if (statistics === myLoadingColumnStatisticsIndicator) null else statistics
    }

    private fun getColumnLabelAt(columnIndex: Int): IHeaderLabel {
        checkIndex("ColumnIndex", columnIndex, tableStructure.columnsCount)
        return convertHeaderLabel(tableStructure.columnInfo.columns[columnIndex].labels)
    }

    private fun getColumnDtypeAt(columnIndex: Int): String {
        checkIndex("ColumnIndex", columnIndex, tableStructure.columnsCount)
        return tableStructure.columnInfo.columns[columnIndex].dtype
    }

    private fun checkIndex(type: String, index: Int, maxBounds: Int) {
        if (index < 0 || index >= maxBounds) {
            throw IndexOutOfBoundsException("$type $index is out of bounds.")
        }
    }

    private fun createChunkRegion(rowIndex: Int, columnIndex: Int): ChunkRegion {
        checkIndex("RowIndex", rowIndex, tableStructure.rowsCount)
        checkIndex("ColumnIndex", columnIndex, tableStructure.columnsCount)
        val firstRow = getIndexOfFirstRowInChunk(rowIndex)
        val firstColumn = getIndexOfFirstColumnInChunk(columnIndex)
        return ChunkRegion(
            firstRow,
            firstColumn,
            chunkSize.rows,
            chunkSize.columns,
        )
    }

    private fun getIndexOfFirstRowInChunk(rowIndex: Int): Int {
        val rowBlockIndex = rowIndex / chunkSize.rows
        return rowBlockIndex * chunkSize.rows
    }

    private fun getIndexOfFirstColumnInChunk(columnIndex: Int): Int {
        val columnBlockIndex = columnIndex / chunkSize.columns
        return columnBlockIndex * chunkSize.columns
    }

    private fun getOrFetchChunk(chunkRegion: ChunkRegion): IChunkValues {
        val values = myFetchedChunkValues[chunkRegion]

        if (values != null) return values

        if (myDataFetchingEnabled
            && !disposed
            && chunkDataLoader.isAlive()
            && !myPendingChunks.contains(chunkRegion)
            && !myFailedChunks.contains(chunkRegion)
        ) {
            myPendingChunks.add(chunkRegion)
            chunkDataLoader.loadChunk(chunkRegion)
        }
        return myNotYetLoadedChunkValues
    }

    override fun createLoadRequestFor(chunkRegion: ChunkRegion): ChunkDataRequest {
        return ChunkDataRequest(
            withCells = true,
            withRowHeaders = myFetchedChunkRowHeaderLabels[chunkRegion.firstRow] == null,
        )
    }

    override fun onResult(result: IModelDataLoader.IResultHandler.Result) {
        when (result) {
            // CHUNK
            is IModelDataLoader.IResultHandler.ChunkDataRejected -> {
                myPendingChunks.remove(result.chunk)
                val scheduleInvokeLater = myRejectedChunkRegions.isEmpty
                // Create a union of all rejected requests to fire only one "fireValueModelValuesUpdated" event.
                // This reduces the amount of fired events during scrolling over many rows/columns of a huge DataFrame.
                //
                // The union can include regions which were not rejected. For example when combining the first and last
                // region of a DataFrame. Such a union includes all rows and columns of the whole DataFrame. This is OK,
                // because the table only repaints the visible rows and columns.
                myRejectedChunkRegions = myRejectedChunkRegions.union(
                    Rectangle(
                        result.chunk.firstColumn,
                        result.chunk.firstRow,
                        result.chunk.numberOfColumns,
                        result.chunk.numberOfRows,
                    )
                )
                if (scheduleInvokeLater) {
                    SwingUtilities.invokeLater {
                        // Repainting a region results in an implicit refetch of the chunk data.
                        fireValuesModelValuesUpdated(
                            ChunkRegion(
                                myRejectedChunkRegions.y,
                                myRejectedChunkRegions.x,
                                myRejectedChunkRegions.height,
                                myRejectedChunkRegions.width,
                            )
                        )
                        myRejectedChunkRegions = Rectangle()
                    }
                }
            }
            is IModelDataLoader.IResultHandler.ChunkDataSuccess -> {
                if (disposed) return

                myPendingChunks.remove(result.chunk)
                var cellsNeedRepaint = false

                result.data.rowHeaderLabels?.let {
                    myFetchedChunkRowHeaderLabels[result.chunk.firstRow] = it
                    if (it.isNotEmpty()) {
                        fireIndexModelValuesUpdated(result.chunk)
                    }
                }

                result.data.values?.let {
                    cellsNeedRepaint = true
                    myFetchedChunkValues[result.chunk] = it
                }

                if (cellsNeedRepaint) {
                    fireValuesModelValuesUpdated(result.chunk)
                }
            }
            is IModelDataLoader.IResultHandler.ChunkDataFailure -> {
                if (disposed) return
                result.chunk.let {
                    myPendingChunks.remove(it)
                    myFailedChunks.add(it)
                }
            }

            // COLUMN STATISTICS
            is IModelDataLoader.IResultHandler.ColumnStatisticsSuccess -> {
                myFetchedColumnStatistics[result.columnIndex] = result.statistics
                fireValuesModelColumnStatisticsUpdated(result.columnIndex)
            }
            is IModelDataLoader.IResultHandler.ColumnStatisticsFailure -> {
                myFetchedColumnStatistics[result.columnIndex] = mapOf(Pair("error", "could not load statistics"))
                fireValuesModelColumnStatisticsUpdated(result.columnIndex)
            }
        }
    }

    private fun fireValuesModelValuesUpdated(chunkRegion: ChunkRegion) {
        if (tableStructure.rowsCount == 0 || tableStructure.columnsCount == 0) return
        myValuesModel.fireTableChanged(
            DataFrameTableModelEvent.createValuesChanged(
                myValuesModel,
                chunkRegion.firstRow,
                min(tableStructure.rowsCount - 1, chunkRegion.firstRow + chunkRegion.numberOfRows),
                chunkRegion.firstColumn,
                min(tableStructure.columnsCount - 1, chunkRegion.firstColumn + chunkRegion.numberOfColumns),
            )
        )
    }

    private fun fireIndexModelValuesUpdated(chunkRegion: ChunkRegion) {
        if (tableStructure.rowsCount == 0 || tableStructure.columnsCount == 0) return
        myIndexModel.fireTableChanged(
            DataFrameTableModelEvent.createValuesChanged(
                myIndexModel,
                chunkRegion.firstRow,
                min(tableStructure.rowsCount - 1, chunkRegion.firstRow + chunkRegion.numberOfRows),
                0,
                0,
            )
        )
    }

    private fun fireValuesModelColumnStatisticsUpdated(index: Int) {
        myValuesModel.fireTableChanged(
            DataFrameTableModelEvent.createColumnStatisticsChanged(myValuesModel, index, index)
        )
    }

    private class ValuesModel(private val source: LazyDataFrameModel) : AbstractTableModel(), IDataFrameValuesDataModel {
        override fun getRowCount() = source.tableStructure.rowsCount
        override fun getColumnCount() = source.tableStructure.columnsCount
        override fun enableDataFetching(enabled: Boolean) = source.enableDataFetching(enabled)
        override fun getValueAt(rowIndex: Int, columnIndex: Int) = source.getValueAt(rowIndex, columnIndex)
        override fun getCellMetaAt(rowIndex: Int, columnIndex: Int) = source.getCellMetaAt(rowIndex, columnIndex)
        override fun getColumnLabelAt(columnIndex: Int) = source.getColumnLabelAt(columnIndex)
        override fun getColumnDtypeAt(columnIndex: Int) = source.getColumnDtypeAt(columnIndex)
        override fun setSortKeys(sortKeys: List<SortKey>) = source.setValueSortKeys(sortKeys)
        override fun isSortable() = source.sortable
        override fun getColumnStatisticsAt(columnIndex: Int) = source.getColumnStatisticsAt(columnIndex)
        override fun getColumnName(columnIndex: Int) = getColumnLabelAt(columnIndex).text()
        override fun getLegendHeader() = source.myLegendHeaders.column
        override fun getUniqueColumnIdAt(columnIndex: Int): Int {
            return source.tableStructure.columnInfo.columns[columnIndex].id
        }
    }

    private class IndexModel(private val source: LazyDataFrameModel) : AbstractTableModel(), IDataFrameIndexDataModel {
        override fun getRowCount() = source.tableStructure.rowsCount
        override fun getColumnCount() = 1
        override fun getColumnName(columnIndex: Int) = getColumnName()
        override fun getValueAt(rowIndex: Int) = source.getRowHeaderLabelAt(rowIndex)
        override fun getColumnHeader() = getLegendHeader()
        override fun getColumnName() = getLegendHeader().text()
        override fun getLegendHeader() = source.myLegendHeaders.row
        override fun getLegendHeaders() = source.myLegendHeaders
    }
}