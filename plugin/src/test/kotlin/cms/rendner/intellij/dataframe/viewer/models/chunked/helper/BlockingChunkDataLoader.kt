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
import cms.rendner.intellij.dataframe.viewer.models.chunked.SortCriteria
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.AbstractChunkDataLoader
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.LoadRequest
import java.util.concurrent.Executor

/**
 * Loads chunks of a pandas DataFrame synchronously (blocks the calling thread).
 *
 * @param chunkEvaluator the evaluator to fetch the HTML data for a chunk of the pandas DataFrame
 * @param loadNewDataStructure flag to switch between old and new data structure.
 * The old one is an HTML string which has to be parsed to extract the element and style information.
 * The new one is an object which describes the required HTML properties - it is easier to process.
 */
internal class BlockingChunkDataLoader(
    chunkEvaluator: IChunkEvaluator,
    loadNewDataStructure: Boolean,
) : Executor, AbstractChunkDataLoader(
    chunkEvaluator,
    loadNewDataStructure,
) {
    override fun loadChunk(request: LoadRequest) {
        submitFetchChunkTask(LoadChunkContext(request), this).whenComplete { _, throwable ->
            if (throwable != null) {
                myResultHandler?.onError(request, throwable)
                throw throwable
            }
        }
    }

    override fun setSortCriteria(sortCriteria: SortCriteria) {
        NotImplementedError("Sorting isn't support by this implementation.")
    }

    override fun isAlive() = true
    override fun dispose() {
        // do nothing
    }

    override fun handleChunkData(ctx: LoadChunkContext, chunkData: ChunkData) {
        myResultHandler?.onChunkLoaded(ctx.request, chunkData)
    }

    override fun handleStyledValues(ctx: LoadChunkContext, chunkValues: ChunkValues) {
        myResultHandler?.onStyledValuesProcessed(ctx.request, chunkValues)
    }

    override fun execute(command: Runnable) {
        command.run()
    }
}