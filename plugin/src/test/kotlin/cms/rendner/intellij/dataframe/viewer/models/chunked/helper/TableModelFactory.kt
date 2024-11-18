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
package cms.rendner.intellij.dataframe.viewer.models.chunked.helper

import cms.rendner.intellij.dataframe.viewer.models.*
import cms.rendner.intellij.dataframe.viewer.models.chunked.*
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.IChunkDataLoader
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.IChunkDataResultHandler
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.LoadRequest

class TableModelFactory(private val chunkSize: ChunkSize) {

    fun createModel(tableStructure: TableStructure, sortable: Boolean = true): RecordingModel {
        val loader = RecordingLoader()
        val model = ChunkedDataFrameModel(
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
        private val loader: RecordingLoader,
    ) : IDataFrameModel by model {

        val recordedLoadRequests: List<LoadRequest>
            get() {
                return loader.recordedRequests
            }

        val recordedSortCriteria: SortCriteria
            get() {
                return loader.recordedSortCriteria
            }

        fun enableDataFetching(enabled: Boolean) {
            getValueDataModel().enableDataFetching(enabled)
        }
    }

    class RecordingLoader : IChunkDataLoader {
        val recordedRequests: MutableList<LoadRequest> = mutableListOf()
        var recordedSortCriteria: SortCriteria = SortCriteria()

        private var resultHandler: IChunkDataResultHandler? = null

        override fun loadChunk(request: LoadRequest) {
            recordedRequests.add(request)
            resultHandler?.onChunkLoaded(request, createResponseFor(request))
        }

        override fun setSortCriteria(sortCriteria: SortCriteria) {
            recordedSortCriteria = sortCriteria
        }

        override fun setResultHandler(resultHandler: IChunkDataResultHandler) {
            this.resultHandler = resultHandler
        }

        override fun isAlive() = true
        override fun dispose() {}

        private fun createResponseFor(request: LoadRequest): ChunkData {
            val chunkRegion = request.chunkRegion
            return ChunkData(
                ChunkHeaderLabels(
                    null,
                    createColumnHeader(if (request.excludeColumnHeaders) 0 else chunkRegion.numberOfColumns),
                    createHeaderLabels(if (request.excludeRowHeaders) 0 else chunkRegion.numberOfRows)
                ),
                ChunkValuesPlaceholder(StringValue("col")),
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

        private fun createColumnHeader(size: Int): List<ColumnHeader> {
            return if (size == 0) {
                emptyList()
            } else {
                val header = ColumnHeader(null, HeaderLabel())
                return List(size) { header }
            }
        }
    }
}