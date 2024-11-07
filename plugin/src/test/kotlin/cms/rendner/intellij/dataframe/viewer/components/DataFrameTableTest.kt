/*
 * Copyright 2021-2024 cms.rendner (Daniel Schmidt)
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

import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkSize
import cms.rendner.intellij.dataframe.viewer.models.chunked.SortCriteria
import cms.rendner.intellij.dataframe.viewer.models.chunked.TableStructure
import cms.rendner.intellij.dataframe.viewer.models.chunked.helper.TableModelFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.SortOrder

internal class DataFrameTableTest {

    private val chunkSize = ChunkSize(2, 2)
    private val tableModelFactory = TableModelFactory(chunkSize)

    private fun createModel(tableStructure: TableStructure, sortable: Boolean = true): TableModelFactory.RecordingModel {
        return tableModelFactory.createModel(tableStructure, sortable).apply { enableDataFetching(true) }
    }

    @Test
    fun valueTable_shouldMarkColumnFixed() {
        val tableComponent = DataFrameTable()
        tableComponent.setDataFrameModel(createModel(tableModelFactory.createTableStructure()))
        tableComponent.getValueTable().getColumnResizeBehavior().let {
            assertThat(it.isFixed(0)).isFalse
            it.markFixed(0)
            assertThat(it.isFixed(0)).isTrue
        }
    }

    @Test
    fun valueTable_shouldRememberFixedColumnForSameDataSource() {
        val tsA = tableModelFactory.createTableStructure()

        val tableComponent = DataFrameTable()
        tableComponent.setDataFrameModel(createModel(tsA))
        tableComponent.getValueTable().getColumnResizeBehavior().let {
            assertThat(it.isFixed(1)).isFalse
            it.markFixed(1)
            assertThat(it.isFixed(1)).isTrue
        }

        val oldColumn = tableComponent.getValueTable().columnModel.getColumn(1)

        val tsB = tsA.copy(
            columnInfo=tsA.columnInfo.copy(columns=tsA.columnInfo.columns.reversed()),
        )

        val lastIndex = tsB.columnsCount - 1
        tableComponent.setDataFrameModel(createModel(tsB))
        val newColumn = tableComponent.getValueTable().columnModel.getColumn(lastIndex - 1)
        tableComponent.getValueTable().getColumnResizeBehavior().let {
            assertThat(newColumn.identifier).isEqualTo(oldColumn.identifier)
            assertThat(it.isFixed(lastIndex - 1)).isTrue
        }
    }

    @Test
    fun valueTable_shouldToggleColumnFixedState() {
        val tableComponent = DataFrameTable()
        tableComponent.setDataFrameModel(createModel(tableModelFactory.createTableStructure()))
        tableComponent.getValueTable().getColumnResizeBehavior().let {
            it.markFixed(0)
            it.toggleFixed(0)
            it.toggleFixed(1)
            assertThat(it.isFixed(0)).isFalse
            assertThat(it.isFixed(1)).isTrue
        }
    }

    @Test
    fun valueTable_shouldClearColumnFixedState() {
        val tableComponent = DataFrameTable()
        tableComponent.setDataFrameModel(createModel(tableModelFactory.createTableStructure()))
        tableComponent.getValueTable().getColumnResizeBehavior().let {
            it.markFixed(0)
            it.clearFixed(0)
            assertThat(it.isFixed(0)).isFalse

            it.toggleFixed(0)
            it.markFixed(1)
            it.clearAllFixed()
            assertThat(it.isFixed(0)).isFalse
            assertThat(it.isFixed(1)).isFalse
        }
    }

    @Test
    fun valueTable_lastColumnCantMarkedAsFixed() {
        val tableComponent = DataFrameTable()
        tableComponent.setDataFrameModel(createModel(tableModelFactory.createTableStructure()))
        tableComponent.getValueTable().getColumnResizeBehavior().let {
            val lastColIndex = tableComponent.getValueTable().columnCount - 1
            assertThat(it.isFixed(lastColIndex)).isFalse
            it.markFixed(lastColIndex)
            assertThat(it.isFixed(lastColIndex)).isFalse
        }
    }

    @Test
    fun valueTable_rowSorter_shouldBeNull() {
        val model = createModel(tableModelFactory.createTableStructure(), sortable = false)
        val tableComponent = DataFrameTable().apply { setDataFrameModel(model) }
        assertThat(tableComponent.getValueTable().rowSorter).isNull()
    }

    @Test
    fun valueTable_rowSorter_sortShouldSortSingleColumn() {
        val model = createModel(tableModelFactory.createTableStructure())
        val tableComponent = DataFrameTable().apply { setDataFrameModel(model) }
        tableComponent.getValueTable().rowSorter!!.let {
            it.setSortOrder(0, SortOrder.ASCENDING, false)
            assertThat(model.recordedSortCriteria).isEqualTo(SortCriteria(listOf(0), listOf(true)))

            it.setSortOrder(1, SortOrder.DESCENDING, false)
            assertThat(model.recordedSortCriteria).isEqualTo(SortCriteria(listOf(1), listOf(false)))
        }
    }

    @Test
    fun valueTable_rowSorter_toggleShouldCycleBetweenAscDescUnsorted() {
        val model = createModel(tableModelFactory.createTableStructure())
        val tableComponent = DataFrameTable().apply { setDataFrameModel(model) }
        tableComponent.getValueTable().rowSorter!!.let {
            it.setSortOrder(0, SortOrder.ASCENDING, false)
            assertThat(model.recordedSortCriteria).isEqualTo(SortCriteria(listOf(0), listOf(true)))

            it.toggleSortOrder(0)
            assertThat(model.recordedSortCriteria).isEqualTo(SortCriteria(listOf(0), listOf(false)))

            it.toggleSortOrder(0)
            assertThat(model.recordedSortCriteria).isEqualTo(SortCriteria())

            it.toggleSortOrder(0)
            assertThat(model.recordedSortCriteria).isEqualTo(SortCriteria(listOf(0), listOf(true)))
        }
    }

    @Test
    fun valueTable_rowSorter_multiSort_sortShouldSortMultipleColumns() {
        val model = createModel(tableModelFactory.createTableStructure())
        val tableComponent = DataFrameTable().apply { setDataFrameModel(model) }
        tableComponent.getValueTable().rowSorter!!.let {
            for (i in 0 until 9) {
                it.setSortOrder(i, SortOrder.ASCENDING, true)
            }
            assertThat(model.recordedSortCriteria).isEqualTo(SortCriteria(IntRange(0, 8).toList(), List(9) { true }))
        }
    }

    @Test
    fun valueTable_rowSorter_multiSort_sortShouldStartSingleColumnSort() {
        val model = createModel(tableModelFactory.createTableStructure())
        val tableComponent = DataFrameTable().apply { setDataFrameModel(model) }
        tableComponent.getValueTable().rowSorter!!.let {
            it.setSortOrder(0, SortOrder.ASCENDING, true)
            it.setSortOrder(1, SortOrder.ASCENDING, true)
            it.setSortOrder(2, SortOrder.ASCENDING, true)
            assertThat(model.recordedSortCriteria).isEqualTo(SortCriteria(listOf(0, 1, 2), listOf(true, true, true)))

            it.setSortOrder(0, SortOrder.ASCENDING, false)
            assertThat(model.recordedSortCriteria).isEqualTo(SortCriteria(listOf(0), listOf(true)))
        }
    }

    @Test
    fun valueTable_rowSorter_multiSort_toggleShouldToggleSingleColumnAndKeepMultiSort() {
        val model = createModel(tableModelFactory.createTableStructure())
        val tableComponent = DataFrameTable().apply { setDataFrameModel(model) }
        tableComponent.getValueTable().rowSorter!!.let {
            it.setSortOrder(0, SortOrder.ASCENDING, true)
            it.setSortOrder(1, SortOrder.ASCENDING, true)
            it.setSortOrder(2, SortOrder.ASCENDING, true)
            assertThat(model.recordedSortCriteria).isEqualTo(SortCriteria(listOf(0, 1, 2), listOf(true, true, true)))

            it.setShiftKeyIsDownDuringAction(true)
            it.toggleSortOrder(1)
            it.setShiftKeyIsDownDuringAction(false)
            assertThat(model.recordedSortCriteria).isEqualTo(SortCriteria(listOf(0, 1, 2), listOf(true, false, true)))
        }
    }

    @Test
    fun valueTable_rowSorter_multiSort_toggleShouldCycleBetweenAscDesc() {
        val model = createModel(tableModelFactory.createTableStructure())
        val tableComponent = DataFrameTable().apply { setDataFrameModel(model) }
        tableComponent.getValueTable().rowSorter!!.let {
            it.setSortOrder(0, SortOrder.ASCENDING, true)
            it.setSortOrder(1, SortOrder.ASCENDING, true)
            assertThat(model.recordedSortCriteria).isEqualTo(SortCriteria(listOf(0, 1), listOf(true, true)))

            it.setShiftKeyIsDownDuringAction(true)

            it.toggleSortOrder(0)
            assertThat(model.recordedSortCriteria).isEqualTo(SortCriteria(listOf(0, 1), listOf(false, true)))

            it.toggleSortOrder(0)
            assertThat(model.recordedSortCriteria).isEqualTo(SortCriteria(listOf(0, 1), listOf(true, true)))
        }
    }

    @Test
    fun valueTable_rowSorter_multiSort_toggleShouldClearMultiSortAndStartSingleSort() {
        val model = createModel(tableModelFactory.createTableStructure())
        val tableComponent = DataFrameTable().apply { setDataFrameModel(model) }
        tableComponent.getValueTable().rowSorter!!.let {
            it.setSortOrder(0, SortOrder.ASCENDING, true)
            it.setSortOrder(1, SortOrder.ASCENDING, true)
            it.setSortOrder(2, SortOrder.ASCENDING, true)
            assertThat(model.recordedSortCriteria).isEqualTo(SortCriteria(listOf(0, 1, 2), listOf(true, true, true)))

            it.toggleSortOrder(1)
            assertThat(model.recordedSortCriteria).isEqualTo(SortCriteria(listOf(1), listOf(true)))
        }
    }

    @Test
    fun valueTable_rowSorter_shouldKeepSortStateOnModelChangeIfSameDataSource() {
        val dataSourceFingerprint = "X"
        val tsA = tableModelFactory.createTableStructure().copy(fingerprint = dataSourceFingerprint)
        val modelA = createModel(tsA)
        val tableComponent = DataFrameTable().apply { setDataFrameModel(modelA) }
        tableComponent.getValueTable().rowSorter!!.let {
            it.setSortOrder(7, SortOrder.DESCENDING, true)  // last col
            it.setSortOrder(6, SortOrder.ASCENDING, true)   // 2nd-last col
            it.setSortOrder(5, SortOrder.DESCENDING, true)  // 3rd-last col
        }

        // the 2nd-last column of "modelA" is filtered out and the column order is reversed
        // criteria contains only values for:
        //  - the last col which is now at position 0
        //  - the 3rd-last col which is now at position 1
        val expectedSortCriteria = SortCriteria(
            listOf(0, 1),
            listOf(false, false),
        )

        val tsB = tsA.copy(
            columnsCount=tsA.columnsCount - 1,
            columnInfo=tsA.columnInfo.copy(
                columns=tsA.columnInfo.columns.mapNotNull { if (it.id == 6) null else it }.reversed(),
            ),
        )

        val modelB = createModel(tsB)
        tableComponent.setDataFrameModel(modelB)
        assertThat(modelB.recordedSortCriteria).isEqualTo(expectedSortCriteria)

        assertThat(modelB.getValueDataModel().getUniqueColumnId(0))
            .isEqualTo(modelA.getValueDataModel().getUniqueColumnId(7))

        assertThat(modelB.getValueDataModel().getUniqueColumnId(1))
            .isEqualTo(modelA.getValueDataModel().getUniqueColumnId(5))
    }

    @Test
    fun valueTable_rowSorter_shouldClearSortStateOnModelChangeIfDifferentDataSource() {
        val modelA = createModel(tableModelFactory.createTableStructure().copy(fingerprint = "A"))
        val tableComponent = DataFrameTable().apply { setDataFrameModel(modelA) }
        tableComponent.getValueTable().rowSorter!!.let {
            it.setSortOrder(0, SortOrder.ASCENDING, true)
            it.setSortOrder(1, SortOrder.ASCENDING, true)
            it.setSortOrder(2, SortOrder.ASCENDING, true)
        }

        val modelB = createModel(tableModelFactory.createTableStructure().copy(fingerprint =  "B"))
        tableComponent.setDataFrameModel(modelB)
        assertThat(modelB.recordedSortCriteria).isEqualTo(SortCriteria())
    }

    @Test
    fun valueTable_columns_shouldKeepColumnStateIfSameDataSource() {
        val dataSourceFingerprint = "X"

        val tsA = tableModelFactory.createTableStructure().copy(fingerprint = dataSourceFingerprint)
        val modelA = createModel(tsA)
        val tableComponent = DataFrameTable().apply { setDataFrameModel(modelA) }
        val oldColumn = tableComponent.getValueTable().columnModel.getColumn(1).also {
                it.width = 123
                it.preferredWidth = 123
                it.headerValue = "123"
        }

        val tsB = tsA.copy(
            columnInfo = tsA.columnInfo.copy(columns = tsA.columnInfo.columns.reversed()),
        )

        val modelB = createModel(tsB)
        tableComponent.setDataFrameModel(modelB)
        tableComponent.getValueTable().columnModel.getColumn(tsB.columnsCount - 2).let { c ->
            assertThat(c.identifier).isEqualTo(oldColumn.identifier)
            assertThat(c.headerValue).isEqualTo(oldColumn.headerValue)
            assertThat(c.width).isEqualTo(oldColumn.width)
            assertThat(c.preferredWidth).isEqualTo(oldColumn.preferredWidth)
        }
    }

    @Test
    fun valueTable_columns_shouldIgnoreColumnStateIfDifferentDataSource() {
        val modelA = createModel(tableModelFactory.createTableStructure().copy(fingerprint = "A"))
        val tableComponent = DataFrameTable().apply { setDataFrameModel(modelA) }
        val oldColumn = tableComponent.getValueTable().columnModel.getColumn(0).also {
            it.width = 123
            it.preferredWidth = 123
            it.headerValue = "123"
        }

        val modelB = createModel(tableModelFactory.createTableStructure().copy(fingerprint = "B"))
        tableComponent.setDataFrameModel(modelB)
        tableComponent.getValueTable().columnModel.getColumn(0).let { c ->
            assertThat(c.identifier).isEqualTo(oldColumn.identifier)
            assertThat(c.headerValue).isNotEqualTo(oldColumn.headerValue)
            assertThat(c.width).isNotEqualTo(oldColumn.width)
            assertThat(c.preferredWidth).isNotEqualTo(oldColumn.preferredWidth)
        }
    }

    @Test
    fun valueTable_focusedCell_shouldBeKeptIfSameDataSource() {
        val dataSourceFingerprint = "X"

        val modelA = createModel(tableModelFactory.createTableStructure().copy(fingerprint = dataSourceFingerprint))
        val tableComponent = DataFrameTable().apply { setDataFrameModel(modelA) }
        tableComponent.setFocusedCell(CellPosition(2, 2))
        assertThat(tableComponent.getFocusedCell()).isEqualTo(CellPosition(2, 2))

        val modelB = createModel(tableModelFactory.createTableStructure().copy(fingerprint = dataSourceFingerprint))
        tableComponent.setDataFrameModel(modelB)
        assertThat(tableComponent.getFocusedCell()).isEqualTo(CellPosition(2, 2))
    }

    @Test
    fun valueTable_focusedCell_shouldBeResetIfDifferentDataSource() {
        val modelA = createModel(tableModelFactory.createTableStructure().copy(fingerprint =  "A"))
        val tableComponent = DataFrameTable().apply { setDataFrameModel(modelA) }
        tableComponent.setFocusedCell(CellPosition(2, 2))
        assertThat(tableComponent.getFocusedCell()).isEqualTo(CellPosition(2, 2))

        val modelB = createModel(tableModelFactory.createTableStructure().copy(fingerprint = "B"))
        tableComponent.setDataFrameModel(modelB)
        assertThat(tableComponent.getFocusedCell()).isEqualTo(CellPosition(0, 0))
    }

    @Test
    fun valueTable_focusedCell_shouldBeAbleToHandleInvalidPositionsWithoutErrors() {
        DataFrameTable().apply {
            setDataFrameModel(createModel(tableModelFactory.createTableStructure()))
        }.let {
            it.setFocusedCell(CellPosition(-2, 2))
            assertThat(it.getFocusedCell()).isEqualTo(CellPosition(0, 2))

            it.setFocusedCell(CellPosition(2, -2))
            assertThat(it.getFocusedCell()).isEqualTo(CellPosition(2, 0))

            it.setFocusedCell(CellPosition(it.getRowCount() + 1, it.getColumnCount() + 1))
            assertThat(it.getFocusedCell()).isEqualTo(CellPosition(it.getRowCount() - 1, it.getColumnCount() - 1))
        }
    }
}