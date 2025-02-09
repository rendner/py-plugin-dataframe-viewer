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
package cms.rendner.intellij.dataframe.viewer.models.chunked.loader

import cms.rendner.intellij.dataframe.viewer.models.chunked.*
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.IModelDataLoader.IResultHandler.Result
import com.intellij.openapi.Disposable
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Interface for loading data from a DataFrame.
 */
interface IModelDataLoader : Disposable {
    /**
     * Requests to load a chunk of data from the underlying DataFrame.
     */
    fun loadChunk(request: LoadRequest)

    /**
     * Requests to load the column statistics for a column from the underlying DataFrame.
     * @param columnIndex the index of the column.
     */
    fun loadColumnStatistics(columnIndex: Int)

    /**
     * Sets the sorting on the underlying DataFrame.
     *
     * Setting a new sorting results into a new sorted DataFrame.
     * All previous loaded chunks are outdated and should be removed.
     *
     * Not yet started, pending, load requests are loaded with the last
     * set sort criteria.
     *
     * @param sorting the new sorting to set.
     */
    fun setSorting(sorting: SortCriteria)

    /**
     * Tests if the loader is alive.
     * A loader is alive if it has access to the python debugger to be able to load data.
     */
    fun isAlive(): Boolean

    /**
     * Sets a handler to handle loaded data or occurred errors.
     * All added handler are automatically removed on dispose.
     *
     * @param handler the handler to add
     */
    fun addResultHandler(handler: IResultHandler)

    interface IResultHandler {
        sealed interface Result
        sealed interface Failure: Result {
            val throwable: Throwable
        }

        enum class RejectReason {
            SORT_CRITERIA_CHANGED,
            TOO_MANY_PENDING_REQUESTS,
        }

        data class ChunkDataSuccess(val request: LoadRequest, val data: ChunkData) : Result
        data class ChunkDataRejected(val request: LoadRequest, val reason: RejectReason) : Result
        data class ColumnStatisticsSuccess(val columnIndex: Int, val statistics: Map<String, String>) : Result

        data class ColumnStatisticsFailure(val columnIndex: Int, override val throwable: Throwable) : Failure
        data class ChunkDataFailure(val request: LoadRequest, override val throwable: Throwable) : Failure

        fun onResult(result: Result)
    }
}

abstract class AbstractModelDataLoader : IModelDataLoader {

    private var myResultHandlers: MutableList<IModelDataLoader.IResultHandler> = CopyOnWriteArrayList()

    override fun dispose() {
        myResultHandlers.clear()
    }

    override fun addResultHandler(handler: IModelDataLoader.IResultHandler) {
        myResultHandlers.add(handler)
    }

    protected fun notifyChunkDataSuccess(request: LoadRequest, data: ChunkData) {
        notify(IModelDataLoader.IResultHandler.ChunkDataSuccess(request, data))
    }

    protected fun notifyChunkDataRejected(request: LoadRequest, reason: IModelDataLoader.IResultHandler.RejectReason) {
        notify(IModelDataLoader.IResultHandler.ChunkDataRejected(request, reason))
    }

    protected fun notifyChunkDataFailure(request: LoadRequest, throwable: Throwable) {
        notify(IModelDataLoader.IResultHandler.ChunkDataFailure(request, throwable))
    }

    protected fun notifyColumnStatisticsSuccess(columnIndex: Int, statistics: Map<String, String>) {
        notify(IModelDataLoader.IResultHandler.ColumnStatisticsSuccess(columnIndex, statistics))
    }

    protected fun notifyColumnStatisticsFailure(columnIndex: Int, throwable: Throwable) {
        notify(IModelDataLoader.IResultHandler.ColumnStatisticsFailure(columnIndex, throwable))
    }

    private fun notify(result: Result) {
        myResultHandlers.forEach { it.onResult(result) }
    }
}

/**
 * Load request to load a chunk.
 * @property chunkRegion the region in the DataFrame to load.
 * @property excludeRowHeaders if the row headers of the DataFrame should be excluded from the result
 */
data class LoadRequest(
    val chunkRegion: ChunkRegion,
    val excludeRowHeaders: Boolean,
)