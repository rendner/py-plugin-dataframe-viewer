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
package cms.rendner.intellij.dataframe.viewer.models.chunked.loader

import cms.rendner.intellij.dataframe.viewer.models.chunked.*
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.exceptions.ChunkDataLoaderException
import com.intellij.openapi.Disposable

/**
 * An interface to retrieve load results from an [IChunkDataLoader]
 */
interface IChunkDataResultHandler {

    enum class RejectReason {
        SORT_CRITERIA_CHANGED,
        TOO_MANY_PENDING_REQUESTS,
    }
    /**
     * Called when the data for a load request was successfully loaded.
     * @param request the load request to which the data belongs
     * @param chunkData the loaded data
     */
    fun onChunkLoaded(request: LoadRequest, chunkData: ChunkData)

    /**
     * Called when an error happened during the loading or processing of a loaded chunk.
     * @param request the load request which failed.
     */
    fun onChunkFailed(request: LoadRequest)

    /**
     * Called when a load request is rejected.
     * This can happen when too many requests are added to the loader which can't be handled in a short time frame.
     * Or if the sort criteria was changed during the loading of a chunk.
     *
     * The requester can decide if the data is still needed and if so add the same request again.
     *
     * @param request the rejected load request
     * @param reason the reason why the request was rejected
     */
    fun onRequestRejected(request: LoadRequest, reason: RejectReason)
}

/**
 * Interface for loading chunks of a pandas DataFrame.
 */
interface IChunkDataLoader : Disposable {
    /**
     * Adds a load request to the data loader.
     * The result can be retrieved by setting a [IChunkDataResultHandler].
     */
    fun loadChunk(request: LoadRequest)

    /**
     * Sets the sort criteria for the underlying pandas DataFrame.
     *
     * Setting a new sort criteria results into a new sorted DataFrame.
     * All previous loaded chunks are outdated and should be removed.
     *
     * Not yet started, pending, load requests are loaded with the last
     * set sort criteria.
     */
    fun setSortCriteria(sortCriteria: SortCriteria)

    /**
     * Sets a handler to the loader that is used to return the loaded data or occurred errors for a processed load request.
     *
     * @param resultHandler the result handler
     */
    fun setResultHandler(resultHandler: IChunkDataResultHandler)

    /**
     * Tests if the loader is alive.
     * A loader is alive if it has access to the python debugger to be able to load data.
     */
    fun isAlive(): Boolean
}

/**
 * An interface to handle errors during the loading/processing of chunk data.
 */
interface IChunkDataLoaderErrorHandler {
    fun handleChunkDataError(region: ChunkRegion, exception: ChunkDataLoaderException)
}

/**
 * Load request for a chunk.
 * @property chunkRegion the region of the chunk in the pandas DataFrame.
 * @property excludeRowHeaders if the row headers of the DataFrame should be excluded from the result
 * @property excludeColumnHeaders if the columns headers of the DataFrame should be excluded from the result
 */
data class LoadRequest(
    val chunkRegion: ChunkRegion,
    val excludeRowHeaders: Boolean,
    val excludeColumnHeaders: Boolean,
)