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
package cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.evaluator

import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.ChunkSize
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.ChunkCoordinates
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.IChunkEvaluator
import cms.rendner.intellij.dataframe.viewer.pycharm.injector.PyPatchedStylerRef

/**
 * @param patchedStyler the styler from which the chunk is fetched.
 * @param chunkSize the size of the chunk. It is safe to provide a size with more rows/columns
 * as the DataFrame on which the [patchedStyler] operates.
 */
class ChunkEvaluator(
    private val patchedStyler: PyPatchedStylerRef,
    override val chunkSize: ChunkSize
) : IChunkEvaluator {

    override fun evaluate(chunkCoordinates: ChunkCoordinates, excludeRowHeaders: Boolean, excludeColumnHeaders: Boolean): String {
        return patchedStyler.evaluateRenderChunk(
            chunkCoordinates.indexOfFirstRow,
            chunkCoordinates.indexOfFirstColumn,
            chunkCoordinates.indexOfFirstRow + chunkSize.rows,
            chunkCoordinates.indexOfFirstColumn + chunkSize.columns,
            excludeRowHeaders,
            excludeColumnHeaders
        )
    }
}