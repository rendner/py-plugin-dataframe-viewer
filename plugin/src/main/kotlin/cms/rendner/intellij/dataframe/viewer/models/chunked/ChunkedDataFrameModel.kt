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
package cms.rendner.intellij.dataframe.viewer.models.chunked

import cms.rendner.intellij.dataframe.viewer.models.*
import cms.rendner.intellij.dataframe.viewer.models.chunked.events.ChunkTableModelEvent
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.IChunkDataLoader
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.IChunkDataResultHandler
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.LoadRequest
import java.awt.Rectangle
import java.lang.Integer.min
import javax.swing.RowSorter.SortKey
import javax.swing.SortOrder
import javax.swing.SwingUtilities
import javax.swing.table.AbstractTableModel

/**
 * Fetches the model data chunkwise via a [chunkDataLoader].
 * The [IChunkDataLoader.dispose] method of the [chunkDataLoader] is automatically called when the model is disposed.
 *
 * @param tableStructure describes the structure of the model.
 * @param chunkDataLoader used for lazy data loading.
 * @param chunkSize size of the chunks to load.
 */
class ChunkedDataFrameModel(
    private val tableStructure: TableStructure,
    private val chunkDataLoader: IChunkDataLoader,
    private val chunkSize: ChunkSize,
) : IDataFrameModel, IExternalSortableDataFrameModel, IChunkDataResultHandler {

    private val myValueModel = ValueModel(this)
    private val myIndexModel = IndexModel(this)

    /**
     * The successfully loaded cell values.
     */
    private val myFetchedChunkValues: MutableMap<ChunkRegion, IChunkValues> = HashMap()

    /**
     * The successfully loaded row headers.
     */
    private val myFetchedChunkRowHeaderLabels: MutableMap<Int, List<IHeaderLabel>> = HashMap()

    /**
     * The successfully loaded column headers.
     */
    private val myFetchedChunkColumnHeaderLabels: MutableMap<Int, List<IHeaderLabel>> = HashMap()

    /**
     * The successfully loaded legend headers.
     */
    private var myFetchedLegendHeaders: LegendHeaders? = null

    /**
     * Tracks all regions which couldn't be loaded because of an error.
     */
    private val myFailedChunks: MutableSet<ChunkRegion> = HashSet()

    /**
     * Tracks all pending load requests, to send only one request per chunk region.
     */
    private val myPendingChunks: MutableSet<ChunkRegion> = HashSet()

    /**
     * Dummy label for the not yet loaded header.
     */
    private val myNotYetLoadedHeaderLabel = HeaderLabel(EMPTY_TABLE_HEADER_VALUE)

    /**
     * Dummy label for the not yet loaded leveled-header.
     */
    private val myNotYetLoadedLeveledHeaderLabel = LeveledHeaderLabel(EMPTY_TABLE_HEADER_VALUE)

    /**
     * Dummy label for the not yet loaded legend-headers.
     */
    private val myNotYetLoadedLegendHeaders: LegendHeaders

    /**
     * Dummy label for the not yet loaded cell values.
     */
    private val myNotYetLoadedValue = StringValue("")

    /**
     * Dummy values for the not yet loaded cell values of a chunk region.
     */
    private val myNotYetLoadedChunkValues = ChunkValuesPlaceholder(myNotYetLoadedValue)

    /**
     * Tracks the region of rejected load requests, to re-request the loading at a later time.
     */
    private var myRejectedChunkRegions = Rectangle()

    private var disposed: Boolean = false

    init {
        chunkDataLoader.setResultHandler(this)

        myNotYetLoadedLegendHeaders = LegendHeaders(
            if (tableStructure.rowLevelsCount > 1) myNotYetLoadedLeveledHeaderLabel else myNotYetLoadedHeaderLabel,
            if (tableStructure.columnLevelsCount > 1) myNotYetLoadedLeveledHeaderLabel else myNotYetLoadedHeaderLabel
        )
    }

    override fun setSortKeys(sortKeys: List<SortKey>) {
        val sortCriteria = sortKeys.fold(MutableSortCriteria()) { criteria, sortKey ->
            criteria.byIndex.add(sortKey.column)
            criteria.ascending.add(sortKey.sortOrder == SortOrder.ASCENDING)
            criteria
        }.toSortCriteria()
        chunkDataLoader.setSortCriteria(sortCriteria)
        clearAllFetchedData()
    }

    private fun clearAllFetchedData() {
        // "myPendingRequests" don't have to be cleared
        myFailedChunks.clear()
        myFetchedChunkValues.clear()
        myFetchedChunkRowHeaderLabels.clear()

        val chunkRegion = ChunkRegion(0, 0, tableStructure.rowsCount, tableStructure.columnsCount)
        fireIndexModelValuesUpdated(chunkRegion)
        fireValueModelValuesUpdated(chunkRegion)
    }

    override fun dispose() {
        if (!disposed) {
            disposed = true
            myFailedChunks.clear()
            myPendingChunks.clear()
            myFetchedChunkValues.clear()
            myFetchedChunkRowHeaderLabels.clear()
            myFetchedChunkColumnHeaderLabels.clear()
        }
    }

    override fun getValueDataModel(): ITableValueDataModel {
        return myValueModel
    }

    override fun getIndexDataModel(): ITableIndexDataModel {
        return myIndexModel
    }

    private fun getValueAt(rowIndex: Int, columnIndex: Int): Value {
        checkIndex("RowIndex", rowIndex, tableStructure.rowsCount)
        checkIndex("ColumnIndex", columnIndex, tableStructure.columnsCount)
        val chunkRegion = createChunkRegion(rowIndex, columnIndex)
        return getOrFetchChunk(chunkRegion)
            .value(
                rowIndex - chunkRegion.firstRow,
                columnIndex - chunkRegion.firstColumn,
            )
    }

    private fun getRowHeaderLabelAt(rowIndex: Int): IHeaderLabel {
        checkIndex("RowIndex", rowIndex, tableStructure.rowsCount)
        val firstIndex = getIndexOfFirstRowInChunk(rowIndex)
        val chunkHeaders = myFetchedChunkRowHeaderLabels[firstIndex]
            ?: return getNotYetLoadedHeaderLabel(tableStructure.rowLevelsCount)
        return chunkHeaders[rowIndex - firstIndex]
    }

    private fun getLegendHeaders(): LegendHeaders {
        return myFetchedLegendHeaders ?: myNotYetLoadedLegendHeaders
    }

    private fun getColumnLegendHeader(): IHeaderLabel {
        return getLegendHeaders().column ?: getNotYetLoadedHeaderLabel(tableStructure.columnLevelsCount)
    }

    private fun getRowLegendHeader(): IHeaderLabel {
        return getLegendHeaders().row ?: getNotYetLoadedHeaderLabel(tableStructure.rowLevelsCount)
    }

    private fun getNotYetLoadedHeaderLabel(levels: Int): IHeaderLabel {
        return if (levels > 1) myNotYetLoadedLeveledHeaderLabel else myNotYetLoadedHeaderLabel
    }

    private fun getColumnHeaderAt(columnIndex: Int): IHeaderLabel {
        checkIndex("ColumnIndex", columnIndex, tableStructure.columnsCount)
        if (tableStructure.hideColumnHeader) {
            return getNotYetLoadedHeaderLabel(tableStructure.columnLevelsCount)
        }
        val firstIndex = getIndexOfFirstColumnInChunk(columnIndex)
        val chunkHeaders = myFetchedChunkColumnHeaderLabels[firstIndex] ?: return getNotYetLoadedHeaderLabel(
            tableStructure.columnLevelsCount
        )
        return chunkHeaders[columnIndex - firstIndex]
    }

    private fun checkIndex(type: String, index: Int, maxBounds: Int) {
        if (index < 0 || index >= maxBounds) {
            throw IndexOutOfBoundsException("$type $index is out of bounds.")
        }
    }

    private fun createChunkRegion(rowIndex: Int, columnIndex: Int): ChunkRegion {
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

        if (!disposed
            && chunkDataLoader.isAlive()
            && !myPendingChunks.contains(chunkRegion)
            && !myFailedChunks.contains(chunkRegion)
        ) {
            myPendingChunks.add(chunkRegion)
            chunkDataLoader.loadChunk(
                LoadRequest(
                    chunkRegion,
                    tableStructure.hideRowHeader || myFetchedChunkRowHeaderLabels[chunkRegion.firstRow] != null,
                    tableStructure.hideColumnHeader || myFetchedChunkColumnHeaderLabels[chunkRegion.firstColumn] != null
                )
            )
        }
        return myNotYetLoadedChunkValues
    }

    override fun onChunkLoaded(request: LoadRequest, chunkData: ChunkData) {
        if (disposed) return
        val chunkRegion = request.chunkRegion

        myPendingChunks.remove(chunkRegion)

        if (myFetchedLegendHeaders == null) {
            myFetchedLegendHeaders = chunkData.headerLabels.legend
            if (chunkData.headerLabels.legend.row != null) {
                fireIndexModelHeaderUpdated()
            }
        }
        if (!myFetchedChunkColumnHeaderLabels.containsKey(chunkRegion.firstColumn)) {
            myFetchedChunkColumnHeaderLabels[chunkRegion.firstColumn] = chunkData.headerLabels.columns
            if (chunkData.headerLabels.columns.isNotEmpty()) {
                fireValueModelHeadersUpdated(chunkRegion)
            }
        }

        if (!myFetchedChunkRowHeaderLabels.containsKey(chunkRegion.firstRow)) {
            myFetchedChunkRowHeaderLabels[chunkRegion.firstRow] = chunkData.headerLabels.rows
            if (chunkData.headerLabels.rows.isNotEmpty()) {
                fireIndexModelValuesUpdated(chunkRegion)
            }
        }
        setChunkValues(chunkRegion, chunkData.values)
    }

    override fun onStyledValuesProcessed(request: LoadRequest, chunkValues: ChunkValues) {
        if (disposed) return
        setChunkValues(request.chunkRegion, chunkValues)
    }

    override fun onRequestRejected(request: LoadRequest, reason: IChunkDataResultHandler.RejectReason) {
        myPendingChunks.remove(request.chunkRegion)
        if (reason == IChunkDataResultHandler.RejectReason.TOO_MANY_PENDING_REQUESTS) {
            val scheduleInvokeLater = myRejectedChunkRegions.isEmpty
            // Create a union of all rejected requests to fire only one "fireValueModelValuesUpdated" event.
            // This reduces the amount of fired events during scrolling over many rows/columns of a huge DataFrame.
            //
            // The union can include regions which were not rejected. For example when combining the first and last
            // region of a DataFrame, the union includes all rows and columns of the whole DataFrame. This is OK,
            // because the table only repaints the visible rows anc columns.
            myRejectedChunkRegions = myRejectedChunkRegions.union(
                Rectangle(
                    request.chunkRegion.firstColumn,
                    request.chunkRegion.firstRow,
                    request.chunkRegion.numberOfColumns,
                    request.chunkRegion.numberOfRows,
                )
            )
            if (scheduleInvokeLater) {
                SwingUtilities.invokeLater {
                    // Repainting a chunk region which was rejected results in an implicit refetch of the chunk data.
                    fireValueModelValuesUpdated(
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
    }

    override fun onError(request: LoadRequest, throwable: Throwable) {
        if (disposed) return
        myPendingChunks.remove(request.chunkRegion)
        myFailedChunks.add(request.chunkRegion)
    }

    private fun setChunkValues(chunkRegion: ChunkRegion, chunkValues: ChunkValues) {
        myFetchedChunkValues[chunkRegion] = chunkValues
        fireValueModelValuesUpdated(chunkRegion)
    }

    private fun fireValueModelValuesUpdated(chunkRegion: ChunkRegion) {
        if (tableStructure.rowsCount == 0 || tableStructure.columnsCount == 0) return
        myValueModel.fireTableChanged(
            ChunkTableModelEvent.createValuesChanged(
                myValueModel,
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
            ChunkTableModelEvent.createValuesChanged(
                myIndexModel,
                chunkRegion.firstRow,
                min(tableStructure.rowsCount - 1, chunkRegion.firstRow + chunkRegion.numberOfRows),
                0,
                0,
            )
        )
    }

    private fun fireValueModelHeadersUpdated(chunkRegion: ChunkRegion) {
        if (tableStructure.rowsCount == 0 || tableStructure.columnsCount == 0) return

        myValueModel.fireTableChanged(
            ChunkTableModelEvent.createHeaderLabelsChanged(
                myValueModel,
                chunkRegion.firstColumn,
                min(tableStructure.columnsCount - 1, chunkRegion.firstColumn + chunkRegion.numberOfColumns),
            )
        )
    }

    private fun fireIndexModelHeaderUpdated() {
        myIndexModel.fireTableChanged(
            ChunkTableModelEvent.createHeaderLabelsChanged(myIndexModel, 0, 0)
        )
    }

    private data class ChunkValuesPlaceholder(private val placeholder: Value) : IChunkValues {
        override fun value(rowIndexInChunk: Int, columnIndexInChunk: Int) = placeholder
    }

    private class ValueModel(private val source: ChunkedDataFrameModel) : AbstractTableModel(), ITableValueDataModel {
        override fun getRowCount() = source.tableStructure.rowsCount
        override fun getColumnCount() = source.tableStructure.columnsCount
        override fun getValueAt(rowIndex: Int, columnIndex: Int) = source.getValueAt(rowIndex, columnIndex)
        override fun getColumnHeaderAt(columnIndex: Int) = source.getColumnHeaderAt(columnIndex)
        override fun getColumnName(columnIndex: Int) = getColumnHeaderAt(columnIndex).text()
        override fun getLegendHeader() = source.getColumnLegendHeader()
        override fun getLegendHeaders() = source.getLegendHeaders()
        override fun isLeveled() = source.tableStructure.columnLevelsCount > 1
        override fun shouldHideHeaders() = source.tableStructure.hideColumnHeader
    }

    private class IndexModel(private val source: ChunkedDataFrameModel) : AbstractTableModel(), ITableIndexDataModel {
        override fun getRowCount() = source.tableStructure.rowsCount
        override fun getColumnCount() = if (source.tableStructure.hideRowHeader) 0 else 1
        override fun getColumnName(columnIndex: Int) = getColumnName()
        override fun getValueAt(rowIndex: Int) = source.getRowHeaderLabelAt(rowIndex)
        override fun getColumnHeader() = getLegendHeader()
        override fun getColumnName() = getLegendHeader().text()
        override fun getLegendHeader() = source.getRowLegendHeader()
        override fun getLegendHeaders() = source.getLegendHeaders()
        override fun isLeveled() = source.tableStructure.rowLevelsCount > 1
        override fun shouldHideHeaders() = source.tableStructure.hideRowHeader
    }
}