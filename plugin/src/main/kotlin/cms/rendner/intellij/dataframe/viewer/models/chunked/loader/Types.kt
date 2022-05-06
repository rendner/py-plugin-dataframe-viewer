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

interface IChunkDataResultHandler {
    fun onChunkLoaded(request: LoadRequest, chunkData: ChunkData)
    fun onStyledValues(request: LoadRequest, chunkValues: ChunkValues)
    fun onError(request: LoadRequest, throwable: Throwable)
}

interface IChunkDataLoader : Disposable {
    /**
     * Adds a load request to the data loader.
     * If the same chunk is requested multiple times, before the result is passed to the registered
     * [IChunkDataResultHandler], the chunk will be only loaded once.
     */
    fun addToLoadingQueue(request: LoadRequest)
    fun setResultHandler(resultHandler: IChunkDataResultHandler)
    fun isAlive(): Boolean
}

data class LoadRequest(
    val chunkRegion: ChunkRegion,
    val excludeRowHeaders: Boolean,
    val excludeColumnHeaders: Boolean,
)