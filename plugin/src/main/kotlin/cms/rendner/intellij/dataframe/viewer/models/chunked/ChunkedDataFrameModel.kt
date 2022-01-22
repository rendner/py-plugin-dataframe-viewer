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
import com.intellij.openapi.diagnostic.Logger
import javax.swing.table.AbstractTableModel

/**
 * Fetches thd model data chunkwise via the specified [chunkDataLoader].
 *
 * @param tableStructure describes the structure of the model.
 * @param chunkDataLoader used for lazy data loading.
 * The [IChunkDataLoader.dispose] method of the [chunkDataLoader] is automatically called when the model is disposed.
 */
class ChunkedDataFrameModel(
    private val tableStructure: TableStructure,
    private val chunkDataLoader: IChunkDataLoader
) : IDataFrameModel, IChunkDataResultHandler {

    private val logger = Logger.getInstance(this::class.java)

    private val myValueModel = ValueModel(this)
    private val myIndexModel = IndexModel(this)

    /**
     * Access is only allowed from event dispatch thread.
     */
    private val myFetchedChunkValues: MutableMap<ChunkCoordinates, IChunkValues> = HashMap()

    /**
     * Access is only allowed from event dispatch thread.
     */
    private val myFetchedChunkRowHeaderLabels: MutableMap<Int, List<IHeaderLabel>> = HashMap()

    /**
     * Access is only allowed from event dispatch thread.
     */
    private val myFetchedChunkColumnHeaderLabels: MutableMap<Int, List<IHeaderLabel>> = HashMap()

    /**
     * Access is only allowed from event dispatch thread.
     */
    private var myFetchedLegendHeaders: LegendHeaders? = null

    /**
     * Access is only allowed from event dispatch thread.
     */
    private val myFailedChunks: MutableSet<ChunkCoordinates> = HashSet()

    private val myNotYetLoadedHeaderLabel = HeaderLabel(EMPTY_TABLE_HEADER_VALUE)
    private val myNotYetLoadedLeveledHeaderLabel = LeveledHeaderLabel(EMPTY_TABLE_HEADER_VALUE)
    private val myNotYetLoadedLegendHeaders: LegendHeaders
    private val myNotYetLoadedValue = StringValue("")
    private val myNotYetLoadedChunkValues = ChunkValuesPlaceholder(myNotYetLoadedValue)

    private var disposed: Boolean = false

    init {
        chunkDataLoader.setResultHandler(this)

        myNotYetLoadedLegendHeaders = LegendHeaders(
            if (tableStructure.rowLevelsCount > 1) myNotYetLoadedLeveledHeaderLabel else myNotYetLoadedHeaderLabel,
            if (tableStructure.columnLevelsCount > 1) myNotYetLoadedLeveledHeaderLabel else myNotYetLoadedHeaderLabel
        )
    }

    override fun dispose() {
        if (!disposed) {
            disposed = true
            chunkDataLoader.dispose()
            myFailedChunks.clear()
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
        checkIndex("RowIndex", rowIndex, tableStructure.visibleRowsCount)
        checkIndex("ColumnIndex", columnIndex, tableStructure.visibleColumnsCount)
        val chunkStartIndices = createChunkCoordinates(rowIndex, columnIndex)
        return getOrFetchChunk(chunkStartIndices)
            .value(
                rowIndex - chunkStartIndices.indexOfFirstRow,
                columnIndex - chunkStartIndices.indexOfFirstColumn
            )
    }

    private fun getRowHeaderLabelAt(rowIndex: Int): IHeaderLabel {
        checkIndex("RowIndex", rowIndex, tableStructure.visibleRowsCount)
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
        checkIndex("ColumnIndex", columnIndex, tableStructure.visibleColumnsCount)
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

    private fun createChunkCoordinates(rowIndex: Int, columnIndex: Int): ChunkCoordinates {
        return ChunkCoordinates(
            getIndexOfFirstRowInChunk(rowIndex),
            getIndexOfFirstColumnInChunk(columnIndex)
        )
    }

    private fun getIndexOfFirstRowInChunk(rowIndex: Int): Int {
        val rowBlockIndex = rowIndex / chunkDataLoader.chunkSize.rows
        return rowBlockIndex * chunkDataLoader.chunkSize.rows
    }

    private fun getIndexOfFirstColumnInChunk(columnIndex: Int): Int {
        val columnBlockIndex = columnIndex / chunkDataLoader.chunkSize.columns
        return columnBlockIndex * chunkDataLoader.chunkSize.columns
    }

    private fun getOrFetchChunk(chunkCoordinates: ChunkCoordinates): IChunkValues {
        val values = myFetchedChunkValues[chunkCoordinates]

        if (values != null) {
            return values
        }

        if (!disposed && chunkDataLoader.isAlive() && !myFailedChunks.contains(chunkCoordinates)) {
            chunkDataLoader.addToLoadingQueue(
                LoadRequest(
                    chunkCoordinates,
                    tableStructure.hideRowHeader || myFetchedChunkRowHeaderLabels[chunkCoordinates.indexOfFirstRow] != null,
                    tableStructure.hideColumnHeader || myFetchedChunkColumnHeaderLabels[chunkCoordinates.indexOfFirstColumn] != null
                )
            )
        }

        return myNotYetLoadedChunkValues
    }

    override fun onChunkLoaded(request: LoadRequest, chunkData: ChunkData) {
        if (disposed) return
        val chunkStartIndices = request.chunkCoordinates

        if (myFetchedLegendHeaders == null) {
            myFetchedLegendHeaders = chunkData.headerLabels.legend
            if (chunkData.headerLabels.legend.row != null) {
                fireIndexModelHeaderUpdated()
            }
        }
        if (!myFetchedChunkColumnHeaderLabels.containsKey(chunkStartIndices.indexOfFirstColumn)) {
            myFetchedChunkColumnHeaderLabels[chunkStartIndices.indexOfFirstColumn] = chunkData.headerLabels.columns
            if (chunkData.headerLabels.columns.isNotEmpty()) {
                fireValueModelHeadersUpdated(
                    chunkStartIndices.indexOfFirstColumn,
                    chunkStartIndices.indexOfFirstColumn + chunkData.headerLabels.columns.size - 1
                )
            }
        }

        if (!myFetchedChunkRowHeaderLabels.containsKey(chunkStartIndices.indexOfFirstRow)) {
            myFetchedChunkRowHeaderLabels[chunkStartIndices.indexOfFirstRow] = chunkData.headerLabels.rows
            if (chunkData.headerLabels.rows.isNotEmpty()) {
                fireIndexModelValuesUpdated(
                    chunkStartIndices.indexOfFirstRow,
                    chunkStartIndices.indexOfFirstRow + chunkData.headerLabels.rows.size - 1
                )
            }
        }
        setChunkValues(chunkStartIndices, chunkData.values)
    }

    override fun onStyledValues(request: LoadRequest, chunkValues: ChunkValues) {
        if (disposed) return
        setChunkValues(request.chunkCoordinates, chunkValues)
    }

    override fun onError(request: LoadRequest, throwable: Throwable) {
        if (disposed) return
        logger.error("Failed to load chunk '${request}':", throwable)
        myFailedChunks.add(request.chunkCoordinates)
    }

    private fun setChunkValues(chunkCoordinates: ChunkCoordinates, chunkValues: ChunkValues) {
        myFetchedChunkValues[chunkCoordinates] = chunkValues

        fireValueModelValuesUpdated(
            chunkCoordinates.indexOfFirstRow,
            chunkCoordinates.indexOfFirstRow + chunkDataLoader.chunkSize.rows - 1,
            chunkCoordinates.indexOfFirstColumn,
            chunkCoordinates.indexOfFirstColumn + chunkDataLoader.chunkSize.columns - 1
        )
    }

    private fun fireValueModelValuesUpdated(firstRow: Int, lastRow: Int, firstColumn: Int, lastColumn: Int) {
        myValueModel.fireTableChanged(
            ChunkTableModelEvent.createValuesChanged(myValueModel, firstRow, lastRow, firstColumn, lastColumn)
        )
    }

    private fun fireIndexModelValuesUpdated(firstRow: Int, lastRow: Int) {
        myIndexModel.fireTableChanged(
            ChunkTableModelEvent.createValuesChanged(myIndexModel, firstRow, lastRow, 0, 0)
        )
    }

    private fun fireValueModelHeadersUpdated(firstColumn: Int, lastColumn: Int) {
        myValueModel.fireTableChanged(
            ChunkTableModelEvent.createHeaderLabelsChanged(myValueModel, firstColumn, lastColumn)
        )
    }

    private fun fireIndexModelHeaderUpdated() {
        myIndexModel.fireTableChanged(
            ChunkTableModelEvent.createHeaderLabelsChanged(myIndexModel, 0, 0)
        )
    }

    private class ValueModel(private val source: ChunkedDataFrameModel) : AbstractTableModel(), ITableValueDataModel {
        override fun getRowCount() = source.tableStructure.visibleRowsCount
        override fun getColumnCount() = source.tableStructure.visibleColumnsCount
        override fun getValueAt(rowIndex: Int, columnIndex: Int) = source.getValueAt(rowIndex, columnIndex)
        override fun getColumnHeaderAt(columnIndex: Int) = source.getColumnHeaderAt(columnIndex)
        override fun getColumnName(columnIndex: Int) = getColumnHeaderAt(columnIndex).text()
        override fun getLegendHeader() = source.getColumnLegendHeader()
        override fun getLegendHeaders() = source.getLegendHeaders()
        override fun isLeveled() = source.tableStructure.columnLevelsCount > 1
        override fun shouldHideHeaders() = source.tableStructure.hideColumnHeader
    }

    private class IndexModel(private val source: ChunkedDataFrameModel) : AbstractTableModel(), ITableIndexDataModel {
        override fun getRowCount() = source.tableStructure.visibleRowsCount
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