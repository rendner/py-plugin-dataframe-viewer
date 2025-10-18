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

import cms.rendner.intellij.dataframe.viewer.components.table.DataFrameTable
import cms.rendner.intellij.dataframe.viewer.python.bridge.SortConfig
import javax.swing.event.RowSorterListener

class DataFrameTableFixture(sortable: Boolean = true): AsyncDataFrameModelFixture(sortable) {

    val tableComponent: DataFrameTable = DataFrameTable().apply { setDataFrameModel(dataFrameModel) }

    init {
        dataFetcher.registerViewportWatcher(tableComponent)

        if (sortable) {
            val rowSortListener = RowSorterListener { event ->
                dataFetcher.setSorting(SortConfig.fromSortKeys(event.source.sortKeys))
            }

            tableComponent.getValuesTable().rowSorter?.addRowSorterListener(rowSortListener)
        }
    }
}