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
package cms.rendner.intellij.dataframe.viewer.components.table.fixtures

import cms.rendner.intellij.dataframe.viewer.components.table.models.HeaderLabel
import cms.rendner.intellij.dataframe.viewer.components.table.models.chunked.AsyncDataFrameModel
import cms.rendner.intellij.dataframe.viewer.components.table.models.chunked.AutoFetchingTableSource
import cms.rendner.intellij.dataframe.viewer.python.bridge.ChunkRegion
import cms.rendner.intellij.dataframe.viewer.python.bridge.FilterResult
import cms.rendner.intellij.dataframe.viewer.python.bridge.IPyTableSourceRef
import cms.rendner.intellij.dataframe.viewer.python.bridge.SortConfig
import cms.rendner.intellij.dataframe.viewer.python.bridge.TableStructure
import cms.rendner.intellij.dataframe.viewer.python.bridge.TableStructureColumn
import cms.rendner.intellij.dataframe.viewer.python.bridge.TableStructureColumnInfo
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.Executor

open class AsyncDataFrameModelFixture(sortable: Boolean = true) {
    val tableStructure: TableStructure
    val mockTableSource: IPyTableSourceRef
    val dataFetcher: AutoFetchingTableSource
    val dataFrameModel: AsyncDataFrameModel

    init {

        val rowsCount = 250
        val columnsCount = 42

        tableStructure = TableStructure(
            rowsCount,
            columnsCount,
            rowsCount,
            columnsCount,
            TableStructureColumnInfo(
                (0 until columnsCount).map { TableStructureColumn(it, "string", HeaderLabel("col_$it")) }
            ),
        )

        mockTableSource = createMockTableSourceRef(tableStructure)

        val executor = Executor { task -> task.run() }
        dataFetcher = AutoFetchingTableSource(executor, mockTableSource, false, null)
        dataFrameModel = AsyncDataFrameModel(dataFetcher, tableStructure, sortable)
    }
}

private fun createMockTableSourceRef(
    tableStructure: TableStructure,
): IPyTableSourceRef {
    val mock = mockk<IPyTableSourceRef>(relaxed = true)

    // Configure default behavior
    every { mock.initialTableStructure } returns tableStructure
    every { mock.isAlive() } returns true

    every { mock.getColumnStatistics(any()) } returns emptyMap()

    every { mock.computeChunkData(any(), any()) } answers {
        val region = firstArg<ChunkRegion>()
        createDefaultChunkData(region)
    }

    every { mock.applyFilter(any()) } answers {
        FilterResult(
            sort = SortConfig(emptyList()),
            tableStructure = tableStructure
        )
    }

    every { mock.getColumnNameCompletionVariants(any(), any()) } returns emptyList()

    every { mock.disposeTableSource() } answers {
        every { mock.isAlive() } returns false
    }

    return mock
}