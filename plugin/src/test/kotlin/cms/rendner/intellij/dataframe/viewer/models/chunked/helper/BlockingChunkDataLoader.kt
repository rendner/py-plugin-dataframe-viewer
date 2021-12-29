/*
 * Copyright 2021 cms.rendner (Daniel Schmidt)
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

import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkData
import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkValues
import cms.rendner.intellij.dataframe.viewer.models.chunked.IChunkEvaluator
import cms.rendner.intellij.dataframe.viewer.models.chunked.converter.IChunkConverter
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.AbstractChunkDataLoader
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.LoadRequest
import org.jsoup.nodes.Document

internal class BlockingChunkDataLoader(
    chunkEvaluator: IChunkEvaluator,
    private val chunkConverterFactory: ((document: Document) -> IChunkConverter)? = null,
) : AbstractChunkDataLoader(
    chunkEvaluator
) {
    override fun addToLoadingQueue(request: LoadRequest) {
        createFetchChunkTask(request).run()
    }

    override fun handleChunkData(loadRequest: LoadRequest, chunkData: ChunkData) {
        myResultHandler?.onChunkLoaded(loadRequest, chunkData)
    }

    override fun handleStyledValues(loadRequest: LoadRequest, chunkValues: ChunkValues) {
        myResultHandler?.onStyledValues(loadRequest, chunkValues)
    }

    override fun handleError(loadRequest: LoadRequest, throwable: Throwable) {
        myResultHandler?.onError(loadRequest, throwable)
        // re-throw unexpected error
        throw throwable
    }

    override fun createChunkConverter(document: Document): IChunkConverter {
        return chunkConverterFactory?.let { it(document) } ?: super.createChunkConverter(document)
    }
}