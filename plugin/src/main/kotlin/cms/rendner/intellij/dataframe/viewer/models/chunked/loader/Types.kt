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
import com.intellij.openapi.Disposable

/**
 * An interface to retrieve load results from an [IChunkDataLoader]
 */
interface IChunkDataResultHandler {
    /**
     * Called when the data for a load request was successfully loaded.
     * @param request the load request to which the data belongs
     * @param chunkData the loaded data
     */
    fun onChunkLoaded(request: LoadRequest, chunkData: ChunkData)

    /**
     * Called when the styled values for a load request are processed.
     * This happens always after an initial [onChunkLoaded] for the same load request.
     * @param request the load request to which the data belongs
     * @param chunkValues the styled values, if there were css styling in the chunk,
     * otherwise the same values are returned as in [ChunkData.values]
     * from [onChunkLoaded] for the same load request.
     */
    fun onStyledValuesProcessed(request: LoadRequest, chunkValues: ChunkValues)

    /**
     * Called when an error happens during the loading ar processing of a loaded chunk.
     * @param request the load request to which the error belongs
     * @param throwable the error
     */
    fun onError(request: LoadRequest, throwable: Throwable)
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
    fun handleChunkDataError(region: ChunkRegion, throwable: Throwable)
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