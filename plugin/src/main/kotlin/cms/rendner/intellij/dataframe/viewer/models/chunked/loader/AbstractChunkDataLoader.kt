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
import cms.rendner.intellij.dataframe.viewer.models.chunked.converter.ChunkConverter
import cms.rendner.intellij.dataframe.viewer.models.chunked.converter.AbstractChunkConverter
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.exceptions.ChunkDataLoaderException
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.ChunkValidator
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.function.Supplier

/**
 * Abstract class for loading chunks of a pandas DataFrame.
 * The data is fetched by using an [IChunkEvaluator] and validated by an optional [ChunkValidator].
 *
 * Chunks of a specific area of a DataFrame can be requested by calling [loadChunk] and specifying the exact
 * location inside the DataFrame. Since, the underlying pandas DataFrame isn't thread safe, there should not be
 * more than one running request in parallel.
 *
 * See [pandas-docs - Thread-safety](https://pandas.pydata.org/pandas-docs/dev/user_guide/gotchas.html#thread-safety)
 * See [DataFrame.copy(), at least, should be threadsafe](https://github.com/pandas-dev/pandas/issues/2728)
 *
 * Note:
 * Subclasses are responsible for:
 * - forwarding the results of a load request to the registered result handler ([setResultHandler])
 * - ensure that only one task, returned by [submitFetchChunkTask], is executed at a time
 *
 * @param chunkEvaluator the evaluator to fetch the HTML data for a chunk of the pandas DataFrame
 * @param chunkValidator the validator to validate the generated HTML data for a chunk
 */
abstract class AbstractChunkDataLoader(
    private var chunkEvaluator: IChunkEvaluator,
    private var chunkValidator: ChunkValidator? = null,
) : IChunkDataLoader {

    protected var myResultHandler: IChunkDataResultHandler? = null

    override fun setResultHandler(resultHandler: IChunkDataResultHandler) {
        myResultHandler = resultHandler
    }

    protected open fun createChunkConverter(document: Document): AbstractChunkConverter {
        return ChunkConverter(document)
    }

    /**
     * Creates a fetch-chunk task.
     *
     * The created task is able to run on a single thread, two threads are optimal.
     * Using more than two threads can't speed up the processing.
     *
     * @param loadRequest the location of the data to fetch
     * @param executor to submit additional subtasks at execution time.
     * @return the fetch-chunk task.
     */
    protected fun submitFetchChunkTask(loadRequest: LoadRequest, executor: Executor): CompletableFuture<Void> {
        return CompletableFuture
            .supplyAsync(createFetchHtmlTask(loadRequest), executor)
            .thenCompose { html ->
                val tasks = mutableListOf<CompletableFuture<Void>>()
                tasks.add(CompletableFuture.runAsync(createParseHtmlTask(loadRequest, html), executor))
                chunkValidator?.let {
                    tasks.add(CompletableFuture.runAsync(createValidateChunkTask(loadRequest), executor))
                }
                CompletableFuture.allOf(*tasks.toTypedArray())
            }
    }

    private fun createFetchHtmlTask(loadRequest: LoadRequest): Supplier<String> {
        return Supplier {
            try {
                chunkEvaluator.evaluate(
                    loadRequest.chunkRegion,
                    loadRequest.excludeRowHeaders,
                    loadRequest.excludeColumnHeaders
                )
            } catch (throwable: Throwable) {
                if (throwable is InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                throw ChunkDataLoaderException("Fetching data failed", throwable)
            }
        }
    }

    private fun createParseHtmlTask(loadRequest: LoadRequest, html: String): Runnable {
        return Runnable {
            try {
                val document = Jsoup.parse(html)
                if (Thread.currentThread().isInterrupted) return@Runnable

                val converter = createChunkConverter(document)

                val chunkData = converter.extractData(loadRequest.excludeRowHeaders, loadRequest.excludeColumnHeaders)
                if (Thread.currentThread().isInterrupted) return@Runnable
                handleChunkData(loadRequest, chunkData)

                val styledValues = converter.mergeWithStyles(chunkData.values)
                if (Thread.currentThread().isInterrupted) return@Runnable
                handleStyledValues(loadRequest, styledValues)
            } catch (throwable: Throwable) {
                if (throwable is InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                throw ChunkDataLoaderException("Parsing fetched data for failed", throwable)
            }
        }
    }

    private fun createValidateChunkTask(loadRequest: LoadRequest): Runnable {
        return Runnable {
            try {
                chunkValidator?.validate(loadRequest.chunkRegion)
            } catch (throwable: Throwable) {
                if (throwable is InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                throw ChunkDataLoaderException("Validating styling functions failed", throwable)
            }
        }
    }

    protected abstract fun handleChunkData(loadRequest: LoadRequest, chunkData: ChunkData)
    protected abstract fun handleStyledValues(loadRequest: LoadRequest, chunkValues: ChunkValues)
}