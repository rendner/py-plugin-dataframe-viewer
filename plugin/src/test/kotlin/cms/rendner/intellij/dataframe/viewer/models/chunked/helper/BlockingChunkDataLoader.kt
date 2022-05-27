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
package cms.rendner.intellij.dataframe.viewer.models.chunked.helper

import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkData
import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkValues
import cms.rendner.intellij.dataframe.viewer.models.chunked.IChunkEvaluator
import cms.rendner.intellij.dataframe.viewer.models.chunked.converter.AbstractChunkConverter
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.AbstractChunkDataLoader
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.LoadRequest
import org.jsoup.nodes.Document
import java.util.concurrent.Executor

/**
 * Loads chunks of a pandas DataFrame synchronously (blocks the calling thread).
 *
 * @param chunkEvaluator the evaluator to fetch the HTML data for a chunk of the pandas DataFrame
 * @param chunkConverterFactory a factory to create custom chunk converters. A chunk converter is responsible
 * for providing the extracted css styling and values from the fetched HTML data.
 */
internal class BlockingChunkDataLoader(
    chunkEvaluator: IChunkEvaluator,
    private val chunkConverterFactory: ((document: Document) -> AbstractChunkConverter)? = null,
) : Executor, AbstractChunkDataLoader(
    chunkEvaluator
) {
    override fun loadChunk(request: LoadRequest) {
        submitFetchChunkTask(request, this).whenComplete { _, throwable ->
            if (throwable != null) {
                myResultHandler?.onError(request, throwable)
                throw throwable
            }
        }
    }

    override fun isAlive() = true
    override fun dispose() {
        // do nothing
    }

    override fun handleChunkData(loadRequest: LoadRequest, chunkData: ChunkData) {
        myResultHandler?.onChunkLoaded(loadRequest, chunkData)
    }

    override fun handleStyledValues(loadRequest: LoadRequest, chunkValues: ChunkValues) {
        myResultHandler?.onStyledValuesProcessed(loadRequest, chunkValues)
    }

    override fun createChunkConverter(document: Document): AbstractChunkConverter {
        return chunkConverterFactory?.let { it(document) } ?: super.createChunkConverter(document)
    }

    override fun execute(command: Runnable) {
        command.run()
    }
}