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

import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkData
import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkValues
import cms.rendner.intellij.dataframe.viewer.models.chunked.IChunkEvaluator
import cms.rendner.intellij.dataframe.viewer.models.chunked.converter.ChunkConverter
import cms.rendner.intellij.dataframe.viewer.models.chunked.converter.IChunkConverter
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

abstract class AbstractChunkDataLoader(
    chunkEvaluator: IChunkEvaluator
) : IChunkDataLoader {

    protected var myResultHandler: IChunkDataResultHandler? = null
    private val myChunkEvaluator = chunkEvaluator

    override fun isAlive() = true

    override fun setResultHandler(resultHandler: IChunkDataResultHandler) {
        myResultHandler = resultHandler
    }

    override fun dispose() {
    }

    protected open fun createChunkConverter(document: Document): IChunkConverter {
        return ChunkConverter(document)
    }

    protected fun createFetchChunkTask(loadRequest: LoadRequest): Runnable {
        return Runnable {
            val currentThread = Thread.currentThread()
            try {
                val evaluatedChunk = myChunkEvaluator.evaluate(
                    loadRequest.chunkRegion,
                    loadRequest.excludeRowHeaders,
                    loadRequest.excludeColumnHeaders
                )
                if (currentThread.isInterrupted) return@Runnable

                val document = Jsoup.parse(evaluatedChunk)
                if (currentThread.isInterrupted) return@Runnable

                val converter = createChunkConverter(document)

                val chunkData = converter.convertText(loadRequest.excludeRowHeaders, loadRequest.excludeColumnHeaders)
                if (currentThread.isInterrupted) return@Runnable
                handleChunkData(loadRequest, chunkData)

                val styledValues = converter.mergeWithStyles(chunkData.values)
                if (currentThread.isInterrupted) return@Runnable
                handleStyledValues(loadRequest, styledValues)
            } catch (e: Throwable) {
                handleError(loadRequest, e)
                if (e is InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }

    protected abstract fun handleChunkData(loadRequest: LoadRequest, chunkData: ChunkData)
    protected abstract fun handleStyledValues(loadRequest: LoadRequest, chunkValues: ChunkValues)
    protected abstract fun handleError(loadRequest: LoadRequest, throwable: Throwable)
}