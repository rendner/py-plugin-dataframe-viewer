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
package cms.rendner.intellij.dataframe.viewer.models.chunked.evaluator

import cms.rendner.intellij.dataframe.viewer.models.chunked.*
import cms.rendner.intellij.dataframe.viewer.python.bridge.HTMLPropsTable
import cms.rendner.intellij.dataframe.viewer.python.bridge.IPyPatchedStylerRef

/**
 * Evaluates the HTML representation for a chunk.
 *
 * @param patchedStyler the styler from which the chunk is fetched.
 */
class ChunkEvaluator(
    private val patchedStyler: IPyPatchedStylerRef,
) : IChunkEvaluator {

    override fun evaluate(
        chunkRegion: ChunkRegion,
        excludeRowHeaders: Boolean,
        excludeColumnHeaders: Boolean
    ): String {
        return patchedStyler.evaluateRenderChunk(
            chunkRegion.firstRow,
            chunkRegion.firstColumn,
            chunkRegion.numberOfRows,
            chunkRegion.numberOfColumns,
            excludeRowHeaders,
            excludeColumnHeaders
        )
    }

    override fun evaluateHTMLProps(
        chunkRegion: ChunkRegion,
        excludeRowHeaders: Boolean,
        excludeColumnHeaders: Boolean
    ): HTMLPropsTable {
        return patchedStyler.evaluateComputeChunkHTMLPropsTable(
            chunkRegion.firstRow,
            chunkRegion.firstColumn,
            chunkRegion.numberOfRows,
            chunkRegion.numberOfColumns,
            excludeRowHeaders,
            excludeColumnHeaders
        )
    }

    override fun setSortCriteria(sortCriteria: SortCriteria) {
        patchedStyler.evaluateSetSortCriteria(sortCriteria.byIndex, sortCriteria.ascending)
    }
}