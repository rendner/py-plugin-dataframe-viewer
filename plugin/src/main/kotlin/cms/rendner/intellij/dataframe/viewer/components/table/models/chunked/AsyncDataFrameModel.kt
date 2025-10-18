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
package cms.rendner.intellij.dataframe.viewer.components.table.models.chunked

import cms.rendner.intellij.dataframe.viewer.components.table.models.HeaderLabel
import cms.rendner.intellij.dataframe.viewer.components.table.models.IDataFrameIndexDataModel
import cms.rendner.intellij.dataframe.viewer.components.table.models.IDataFrameModel
import cms.rendner.intellij.dataframe.viewer.components.table.models.IDataFrameValuesDataModel
import cms.rendner.intellij.dataframe.viewer.components.table.models.LegendHeaders
import cms.rendner.intellij.dataframe.viewer.components.table.models.events.DataFrameTableModelEvent
import cms.rendner.intellij.dataframe.viewer.python.bridge.ChunkRegion
import cms.rendner.intellij.dataframe.viewer.python.bridge.TableStructure
import com.intellij.openapi.Disposable
import javax.swing.table.AbstractTableModel

class AsyncDataFrameModel(
    private val tableSource: AutoFetchingTableSource,
    private val tableStructure: TableStructure,
    private val sortable: Boolean = false,
    withIndexModel: Boolean = false,
): IDataFrameModel, Disposable, AutoFetchingTableSourceListener {
    private val myValuesModel = ValuesModel(this)
    private val myIndexModel = if (withIndexModel) IndexModel(this) else null

    private val myLegendHeaders: LegendHeaders = tableStructure.columnInfo.legend
        ?: LegendHeaders(
            HeaderLabel(EMPTY_TABLE_HEADER_VALUE),
            HeaderLabel(EMPTY_TABLE_HEADER_VALUE),
            )

    init {
        tableSource.addListener(this)
    }

    override fun getValuesDataModel(): IDataFrameValuesDataModel {
        return myValuesModel
    }

    override fun getIndexDataModel(): IDataFrameIndexDataModel? {
        return myIndexModel
    }

    override fun dispose() {
        tableSource.removeListener(this)
    }

    override fun tableValuesFetched(chunkRegion: ChunkRegion) {
        fireIndexModelValuesUpdated(chunkRegion)
        fireValuesModelValuesUpdated(chunkRegion)
    }

    override fun columnStatisticsFetched(columnIndex: Int) {
        fireValuesModelColumnStatisticsUpdated(columnIndex)
    }

    private fun fireValuesModelValuesUpdated(chunkRegion: ChunkRegion) {
        if (tableStructure.rowsCount == 0 || tableStructure.columnsCount == 0) return
        myValuesModel.fireTableChanged(
            DataFrameTableModelEvent.Companion.createValuesChanged(
                myValuesModel,
                chunkRegion.firstRow,
                Integer.min(tableStructure.rowsCount - 1, chunkRegion.firstRow + chunkRegion.numberOfRows),
                chunkRegion.firstColumn,
                Integer.min(tableStructure.columnsCount - 1, chunkRegion.firstColumn + chunkRegion.numberOfColumns),
            )
        )
    }

    private fun fireIndexModelValuesUpdated(chunkRegion: ChunkRegion) {
        if (tableStructure.rowsCount == 0 || tableStructure.columnsCount == 0) return
        myIndexModel?.let {
            it.fireTableChanged(
                DataFrameTableModelEvent.Companion.createValuesChanged(
                    it,
                    chunkRegion.firstRow,
                    Integer.min(tableStructure.rowsCount - 1, chunkRegion.firstRow + chunkRegion.numberOfRows),
                    0,
                    0,
                )
            )
        }
    }

    private fun fireValuesModelColumnStatisticsUpdated(index: Int) {
        myValuesModel.fireTableChanged(
            DataFrameTableModelEvent.Companion.createColumnStatisticsChanged(myValuesModel, index, index)
        )
    }

    private class ValuesModel(private val source: AsyncDataFrameModel) : AbstractTableModel(),
        IDataFrameValuesDataModel {
        override fun getRowCount() = source.tableStructure.rowsCount
        override fun getColumnCount() = source.tableStructure.columnsCount
        override fun getValueAt(rowIndex: Int, columnIndex: Int) = source.tableSource.getCellAt(rowIndex, columnIndex).value
        override fun getCellMetaAt(rowIndex: Int, columnIndex: Int) = source.tableSource.getCellAt(rowIndex, columnIndex).meta
        override fun getColumnLabelAt(columnIndex: Int) = source.tableSource.getColumnHeaderAt(columnIndex).labels
        override fun getColumnTextAlignAt(columnIndex: Int) = source.tableSource.getColumnHeaderAt(columnIndex).textAlign
        override fun getColumnDtypeAt(columnIndex: Int) = source.tableSource.getColumnHeaderAt(columnIndex).dtype
        override fun isSortable() = source.sortable
        override fun getColumnStatisticsAt(columnIndex: Int) = source.tableSource.getColumnStatisticsAt(columnIndex)
        override fun getColumnName(columnIndex: Int) = getColumnLabelAt(columnIndex).text()
        override fun getLegendHeader() = source.myLegendHeaders.column
        override fun getUniqueColumnIdAt(columnIndex: Int): Int {
            return source.tableStructure.columnInfo.columns[columnIndex].id
        }
    }

    private class IndexModel(private val source: AsyncDataFrameModel) : AbstractTableModel(), IDataFrameIndexDataModel {
        override fun getRowCount() = source.tableStructure.rowsCount
        override fun getColumnCount() = 1
        override fun getColumnName(columnIndex: Int) = getColumnName()
        override fun getValueAt(rowIndex: Int) = source.tableSource.getRowHeaderLabelAt(rowIndex)
        override fun getColumnHeader() = getLegendHeader()
        override fun getColumnName() = getLegendHeader().text()
        override fun getLegendHeader() = source.myLegendHeaders.row
        override fun getLegendHeaders() = source.myLegendHeaders
    }
}