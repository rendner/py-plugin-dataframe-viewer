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
package cms.rendner.intellij.dataframe.viewer.models.chunked.helper

import cms.rendner.intellij.dataframe.viewer.models.*
import cms.rendner.intellij.dataframe.viewer.models.chunked.*
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.AbstractModelDataLoader
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.LoadRequest

class TableModelFactory(private val chunkSize: ChunkSize) {

    fun createModel(tableStructure: TableStructure, sortable: Boolean = true): RecordingModel {
        val loader = RecordingModelDataLoader()
        val model = LazyDataFrameModel(
            tableStructure,
            loader,
            chunkSize,
            sortable,
            true,
        )
        return RecordingModel(model, loader)
    }

    fun createTableStructure(
        rowCount: Int = chunkSize.rows * 4,
        columnCount: Int = chunkSize.columns * 4,
    ): TableStructure {
        return TableStructure(
            rowCount,
            columnCount,
            rowCount,
            columnCount,
            fingerprint = "",
            TableStructureColumnInfo(
                (0 until columnCount).map { TableStructureColumn(it, "string", listOf("col_$it")) }
            ),
        )
    }

    class RecordingModel internal constructor(
        private val model: IDataFrameModel,
        private val loader: RecordingModelDataLoader,
    ) : IDataFrameModel by model {

        val recordedLoadRequests: List<LoadRequest>
            get() {
                return loader.recordedRequests
            }

        val recordedSortCriteria: SortCriteria?
            get() {
                return loader.recordedSorting
            }

        fun enableDataFetching(enabled: Boolean) {
            getValuesDataModel().enableDataFetching(enabled)
        }
    }

    class RecordingModelDataLoader : AbstractModelDataLoader() {
        val recordedRequests: MutableList<LoadRequest> = mutableListOf()
        var recordedSorting: SortCriteria? = null

        override fun loadChunk(chunk: ChunkRegion) {
            createLoadRequestFor(chunk).let {
                recordedRequests.add(it)
                notifyChunkDataSuccess(chunk, createResponseFor(it))
            }
        }

        override fun loadColumnStatistics(columnIndex: Int) {
            notifyColumnStatisticsSuccess(columnIndex, emptyMap())
        }

        override fun setSorting(sorting: SortCriteria) {
            recordedSorting = sorting
        }

        override fun isAlive() = true

        private fun createResponseFor(request: LoadRequest): ChunkData {
            val chunkRegion = request.chunkRegion
            return ChunkData(
                values = ChunkValuesPlaceholder(StringValue("col")),
                rowHeaderLabels = createHeaderLabels(if (request.withRowHeaders) chunkRegion.numberOfRows else 0)
            )
        }

        private fun createHeaderLabels(size: Int): List<IHeaderLabel> {
            return if (size == 0) {
                emptyList()
            } else {
                val header = HeaderLabel()
                return List(size) { header }
            }
        }
    }
}