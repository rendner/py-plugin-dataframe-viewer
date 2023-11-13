/*
 * Copyright 2021-2023 cms.rendner (Daniel Schmidt)
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
import cms.rendner.intellij.dataframe.viewer.models.chunked.IChunkEvaluator
import cms.rendner.intellij.dataframe.viewer.models.chunked.SortCriteria
import cms.rendner.intellij.dataframe.viewer.models.chunked.converter.TableFrameConverter
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.exceptions.ChunkDataLoaderException
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.ChunkValidator
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/**
 * Abstract class for loading chunks of a pandas DataFrame.
 * The data is fetched by using an [IChunkEvaluator] and validated by an optional [ChunkValidator].
 *
 * Chunks of a specific area of a DataFrame can be requested by calling [loadChunk] and specifying the exact
 * location inside the DataFrame. Chunks are always loaded one after the other to not block the Python side.
 *
 * Note:
 * Subclasses are responsible for:
 * - forwarding the results of a load request to the registered result handler ([setResultHandler])
 * - ensure that only one task, returned by [submitFetchChunkTask], is executed at a time
 *      - starting more than one doesn't speed up the process because they run all in the same Python thread
 *
 * @param chunkEvaluator the evaluator to fetch the HTML data for a chunk of the pandas DataFrame
 * @param chunkValidator the validator to validate the generated HTML data for a chunk
 */
abstract class AbstractChunkDataLoader(
    private var chunkEvaluator: IChunkEvaluator,
    private var chunkValidator: ChunkValidator? = null,
) : IChunkDataLoader {

    protected var myResultHandler: IChunkDataResultHandler? = null

    protected data class LoadChunkContext(val request: LoadRequest, val sortCriteria: SortCriteria? = null)

    override fun setResultHandler(resultHandler: IChunkDataResultHandler) {
        myResultHandler = resultHandler
    }

    /**
     * Creates a fetch-chunk task.
     *
     * The created task is able to run on a single thread, two threads are optimal.
     * Using more than two threads can't speed up the processing.
     *
     * @param ctx the context, describes the sorting and the location of the data to fetch
     * @param executor to submit additional subtasks at execution time.
     * @return the fetch-chunk task.
     */
    protected fun submitFetchChunkTask(ctx: LoadChunkContext, executor: Executor): CompletableFuture<Void> {
        return CompletableFuture.runAsync(createFetchAndValidateTask(ctx), executor)
    }

    protected abstract fun handleChunkData(ctx: LoadChunkContext, chunkData: ChunkData)

    private fun createFetchAndValidateTask(ctx: LoadChunkContext): Runnable {
        return Runnable {
            var errMessage = ""
            try {
                if (Thread.currentThread().isInterrupted) return@Runnable
                errMessage = "Setting sort criteria failed"
                ctx.sortCriteria?.let { chunkEvaluator.setSortCriteria(it) }

                if (Thread.currentThread().isInterrupted) return@Runnable
                errMessage = "Fetching data failed"
                val table = chunkEvaluator.evaluateTableFrame(
                    ctx.request.chunkRegion,
                    ctx.request.excludeRowHeaders,
                    ctx.request.excludeColumnHeaders
                )

                if (Thread.currentThread().isInterrupted) return@Runnable
                errMessage = "Converting fetched data failed"
                val chunkData = TableFrameConverter.convert(
                    table,
                    ctx.request.excludeRowHeaders,
                    ctx.request.excludeColumnHeaders,
                )
                handleChunkData(ctx, chunkData)

                if (Thread.currentThread().isInterrupted) return@Runnable
                errMessage = "Validating styling functions failed"
                chunkValidator?.validate(ctx.request.chunkRegion)
            } catch (throwable: Throwable) {
                if (throwable is InterruptedException) Thread.currentThread().interrupt()
                throw ChunkDataLoaderException(errMessage, throwable)
            }
        }
    }
}