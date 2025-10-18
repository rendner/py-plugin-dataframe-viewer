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
package cms.rendner.intellij.dataframe.viewer.components.table

import cms.rendner.intellij.dataframe.viewer.components.table.models.EmptyDataFrameModel
import cms.rendner.intellij.dataframe.viewer.components.table.fixtures.DataFrameTableFixture
import cms.rendner.intellij.dataframe.viewer.python.bridge.SortConfig
import io.mockk.justRun
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import javax.swing.SortOrder

internal class DataFrameTableTest {

    private fun setupFixture(sortable: Boolean = true): DataFrameTableFixture {
        return DataFrameTableFixture(sortable)
    }

    private fun sleep(milliseconds: Long = 42L) {
        Thread.sleep(milliseconds)
    }

    @Test
    fun valueTable_shouldMarkColumnFixed() {
        val fixture = setupFixture()

        fixture.tableComponent.getValuesTable().getColumnResizeBehavior().let {
            Assertions.assertThat(it.isFixed(0)).isFalse
            it.markFixed(0)
            Assertions.assertThat(it.isFixed(0)).isTrue
        }
    }

    @Test
    fun valueTable_shouldRememberFixedColumn() {
        val fixture = setupFixture()

        fixture.tableComponent.getValuesTable().getColumnResizeBehavior().let {
            Assertions.assertThat(it.isFixed(1)).isFalse
            it.markFixed(1)
            Assertions.assertThat(it.isFixed(1)).isTrue
        }

        val fixedColumnIdentifier = fixture.tableComponent.getValuesTable().columnModel.getColumn(1).identifier

        fixture.tableComponent.getValuesTable().setModel(EmptyDataFrameModel().getValuesDataModel())
        fixture.tableComponent.getValuesTable().setModel(fixture.dataFrameModel.getValuesDataModel())

        val newColumn = fixture.tableComponent.getValuesTable().columnModel.getColumn(1)
        fixture.tableComponent.getValuesTable().getColumnResizeBehavior().let {
            Assertions.assertThat(newColumn.identifier).isEqualTo(fixedColumnIdentifier)
            Assertions.assertThat(it.isFixed(1)).isTrue
        }
    }

    @Test
    fun valueTable_shouldToggleColumnFixedState() {
        val fixture = setupFixture()

        fixture.tableComponent.getValuesTable().getColumnResizeBehavior().let {
            it.markFixed(0)
            it.toggleFixed(0)
            it.toggleFixed(1)
            Assertions.assertThat(it.isFixed(0)).isFalse
            Assertions.assertThat(it.isFixed(1)).isTrue
        }
    }

    @Test
    fun valueTable_shouldClearColumnFixedState() {
        val fixture = setupFixture()

        fixture.tableComponent.getValuesTable().getColumnResizeBehavior().let {
            it.markFixed(0)
            it.clearFixed(0)
            Assertions.assertThat(it.isFixed(0)).isFalse

            it.toggleFixed(0)
            it.markFixed(1)
            it.clearAllFixed()
            Assertions.assertThat(it.isFixed(0)).isFalse
            Assertions.assertThat(it.isFixed(1)).isFalse
        }
    }

    @Test
    fun valueTable_lastColumnCantMarkedAsFixed() {
        val fixture = setupFixture()

        fixture.tableComponent.getValuesTable().getColumnResizeBehavior().let {
            val lastColIndex = fixture.tableComponent.getValuesTable().columnCount - 1
            Assertions.assertThat(it.isFixed(lastColIndex)).isFalse
            it.markFixed(lastColIndex)
            Assertions.assertThat(it.isFixed(lastColIndex)).isFalse
        }
    }

    @Test
    fun valueTable_rowSorter_shouldBeNull() {
        val fixture = setupFixture(false)
        Assertions.assertThat(fixture.tableComponent.getValuesTable().rowSorter).isNull()
    }

    @Test
    fun valueTable_rowSorter_sortShouldSortSingleColumn() {
        val fixture = setupFixture()

        val sortSlot = slot<SortConfig>()
        justRun { fixture.mockTableSource.applySort(capture(sortSlot)) }

        fixture.tableComponent.getValuesTable().rowSorter!!.let {
            it.setSortOrder(0, SortOrder.ASCENDING, false)
            sleep()
            Assertions.assertThat(sortSlot.captured).isEqualTo(SortConfig(listOf(0), listOf(true)))

            it.setSortOrder(1, SortOrder.DESCENDING, false)
            sleep()
            Assertions.assertThat(sortSlot.captured).isEqualTo(SortConfig(listOf(1), listOf(false)))
        }
    }

    @Test
    fun valueTable_rowSorter_toggleShouldCycleBetweenAscDescUnsorted() {
        val fixture = setupFixture()

        val sortSlot = slot<SortConfig>()
        justRun { fixture.mockTableSource.applySort(capture(sortSlot)) }

        fixture.tableComponent.getValuesTable().rowSorter!!.let {
            it.setSortOrder(0, SortOrder.ASCENDING, false)
            sleep()
            Assertions.assertThat(sortSlot.captured).isEqualTo(SortConfig(listOf(0), listOf(true)))

            it.toggleSortOrder(0)
            sleep()
            Assertions.assertThat(sortSlot.captured).isEqualTo(SortConfig(listOf(0), listOf(false)))

            it.toggleSortOrder(0)
            sleep()
            Assertions.assertThat(sortSlot.captured).isEqualTo(SortConfig())

            it.toggleSortOrder(0)
            sleep()
            Assertions.assertThat(sortSlot.captured).isEqualTo(SortConfig(listOf(0), listOf(true)))
        }
    }

    @Test
    fun valueTable_rowSorter_multiSort_sortShouldSortMultipleColumns() {
        val fixture = setupFixture()

        val sortSlot = slot<SortConfig>()
        justRun { fixture.mockTableSource.applySort(capture(sortSlot)) }

        fixture.tableComponent.getValuesTable().rowSorter!!.let {
            for (i in 0 until 9) {
                it.setSortOrder(i, SortOrder.ASCENDING, true)
            }

            sleep()
            Assertions.assertThat(sortSlot.captured)
                .isEqualTo(SortConfig(IntRange(0, 8).toList(), List(9) { true }))
        }
    }

    @Test
    fun valueTable_rowSorter_multiSort_sortShouldStartSingleColumnSort() {
        val fixture = setupFixture()

        val sortSlot = slot<SortConfig>()
        justRun { fixture.mockTableSource.applySort(capture(sortSlot)) }

        fixture.tableComponent.getValuesTable().rowSorter!!.let {
            it.setSortOrder(0, SortOrder.ASCENDING, true)
            it.setSortOrder(1, SortOrder.ASCENDING, true)
            it.setSortOrder(2, SortOrder.ASCENDING, true)
            sleep()

            verify(atMost = 3) { fixture.mockTableSource.applySort(any()) }
            Assertions.assertThat(sortSlot.captured)
                .isEqualTo(SortConfig(listOf(0, 1, 2), listOf(true, true, true)))

            it.setSortOrder(0, SortOrder.ASCENDING, false)
            sleep()

            verify(atMost = 4) { fixture.mockTableSource.applySort(any()) }
            Assertions.assertThat(sortSlot.captured).isEqualTo(SortConfig(listOf(0), listOf(true)))
        }
    }

    @Test
    fun valueTable_rowSorter_multiSort_toggleShouldToggleSingleColumnAndKeepMultiSort() {
        val fixture = setupFixture()

        val sortSlot = slot<SortConfig>()
        justRun { fixture.mockTableSource.applySort(capture(sortSlot)) }

        fixture.tableComponent.getValuesTable().rowSorter!!.let {
            it.setSortOrder(0, SortOrder.ASCENDING, true)
            it.setSortOrder(1, SortOrder.ASCENDING, true)
            it.setSortOrder(2, SortOrder.ASCENDING, true)
            sleep()

            Assertions.assertThat(sortSlot.captured)
                .isEqualTo(SortConfig(listOf(0, 1, 2), listOf(true, true, true)))

            it.setShiftKeyIsDownDuringAction(true)
            it.toggleSortOrder(1)
            it.setShiftKeyIsDownDuringAction(false)
            sleep()

            Assertions.assertThat(sortSlot.captured)
                .isEqualTo(SortConfig(listOf(0, 1, 2), listOf(true, false, true)))
        }
    }

    @Test
    fun valueTable_rowSorter_multiSort_toggleShouldCycleBetweenAscDesc() {
        val fixture = setupFixture()

        val sortSlot = slot<SortConfig>()
        justRun { fixture.mockTableSource.applySort(capture(sortSlot)) }

        fixture.tableComponent.getValuesTable().rowSorter!!.let {
            it.setSortOrder(0, SortOrder.ASCENDING, true)
            it.setSortOrder(1, SortOrder.ASCENDING, true)
            sleep()

            Assertions.assertThat(sortSlot.captured).isEqualTo(SortConfig(listOf(0, 1), listOf(true, true)))

            it.setShiftKeyIsDownDuringAction(true)
            it.toggleSortOrder(0)
            sleep()

            Assertions.assertThat(sortSlot.captured).isEqualTo(SortConfig(listOf(0, 1), listOf(false, true)))

            it.toggleSortOrder(0)
            sleep()

            Assertions.assertThat(sortSlot.captured).isEqualTo(SortConfig(listOf(0, 1), listOf(true, true)))
        }
    }

    @Test
    fun valueTable_rowSorter_multiSort_toggleShouldClearMultiSortAndStartSingleSort() {
        val fixture = setupFixture()

        val sortCaptures = mutableListOf<SortConfig>()
        justRun { fixture.mockTableSource.applySort(capture(sortCaptures)) }

        fixture.tableComponent.getValuesTable().rowSorter!!.let {
            it.setSortOrder(0, SortOrder.ASCENDING, true)
            it.setSortOrder(1, SortOrder.ASCENDING, true)
            it.setSortOrder(2, SortOrder.ASCENDING, true)
            sleep()

            Assertions.assertThat(sortCaptures.last())
                .isEqualTo(SortConfig(listOf(0, 1, 2), listOf(true, true, true)))

            it.toggleSortOrder(1)
            sleep()

            Assertions.assertThat(sortCaptures.last()).isEqualTo(SortConfig(listOf(1), listOf(true)))
        }
    }

    @Test
    fun valueTable_columns_shouldKeepColumnState() {
        val fixture = setupFixture()

        val column = fixture.tableComponent.getValuesTable().columnModel.getColumn(1).also {
                it.width = 123
                it.preferredWidth = 123
                it.headerValue = "123"
        }

        fixture.tableComponent.getValuesTable().setModel(EmptyDataFrameModel().getValuesDataModel())
        fixture.tableComponent.getValuesTable().setModel(fixture.dataFrameModel.getValuesDataModel())

        fixture.tableComponent.getValuesTable().columnModel.getColumn(1).let { c ->
            Assertions.assertThat(c.identifier).isEqualTo(column.identifier)
            Assertions.assertThat(c.headerValue).isEqualTo(column.headerValue)
            Assertions.assertThat(c.width).isEqualTo(column.width)
            Assertions.assertThat(c.preferredWidth).isEqualTo(column.preferredWidth)
        }
    }

    @Test
    fun valueTable_focusedCell_shouldBeKept() {
        val fixtureA = setupFixture()

        fixtureA.tableComponent.setFocusedCell(CellPosition(2, 2))
        Assertions.assertThat(fixtureA.tableComponent.getFocusedCell()).isEqualTo(CellPosition(2, 2))

        val fixtureB = setupFixture()
        fixtureA.tableComponent.setDataFrameModel(fixtureB.dataFrameModel)
        fixtureA.tableComponent.setDataFrameModel(fixtureA.dataFrameModel)

        Assertions.assertThat(fixtureA.tableComponent.getFocusedCell()).isEqualTo(CellPosition(2, 2))
    }

    @Test
    fun valueTable_focusedCell_shouldBeAbleToHandleInvalidPositionsWithoutErrors() {
        val fixture = setupFixture()

        fixture.tableComponent.let {
            it.setFocusedCell(CellPosition(-2, 2))
            Assertions.assertThat(it.getFocusedCell()).isEqualTo(CellPosition(0, 2))

            it.setFocusedCell(CellPosition(2, -2))
            Assertions.assertThat(it.getFocusedCell()).isEqualTo(CellPosition(2, 0))

            it.setFocusedCell(CellPosition(it.getRowCount() + 1, it.getColumnCount() + 1))
            Assertions.assertThat(it.getFocusedCell()).isEqualTo(CellPosition(it.getRowCount() - 1, it.getColumnCount() - 1))
        }
    }
}
