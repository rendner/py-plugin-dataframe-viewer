/*
 * Copyright 2021-2024 cms.rendner (Daniel Schmidt)
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
import cms.rendner.intellij.dataframe.viewer.python.bridge.*

/**
 * Evaluates the table representation of a chunk.
 *
 * @param tableSourceRef the source from which the chunk is fetched.
 */
open class ChunkEvaluator(
    private val tableSourceRef: IPyTableSourceRef,
) : IChunkEvaluator {

    override fun evaluateTableFrame(
        chunkRegion: ChunkRegion,
        excludeRowHeader: Boolean,
        excludeColumnHeader: Boolean
    ): TableFrame {
        return tableSourceRef.evaluateComputeChunkTableFrame(chunkRegion, excludeRowHeader, excludeColumnHeader)
    }

    override fun setSortCriteria(sortCriteria: SortCriteria) {
        tableSourceRef.evaluateSetSortCriteria(sortCriteria)
    }
}

/**
 * Evaluates and validates styled representation of a chunk.
 *
 * Validates that registered pandas styling functions produce a stable result when these are applied to the chunks.
 * Errors or problems are reported to [problemHandler].
 *
 * @param tableSourceRef the source from which the chunk is fetched.
 * @param problemHandler handler, to which the problems are reported.
 */
class ValidatedChunkEvaluator(
    private val tableSourceRef: IPyPatchedStylerRef,
    private val problemHandler: IChunkValidationProblemHandler,
): ChunkEvaluator(tableSourceRef) {

    /**
     * Stores the index of reported styling functions, to not report them more than once.
     *
     * The Python implementation of [IPyPatchedStylerRef] also tracks
     * reported styling functions to reduce unnecessary computations. It is not
     * guaranteed that the Python part does report a faulty styling function only once.
     * Whenever a [IPyPatchedStylerRef] becomes unreachable it is re-created by the plugin
     * and the tracked state on Python side is empty.
     */
    private val reportedStyleFuncIndices = mutableSetOf<Int>()

    override fun evaluateTableFrame(
        chunkRegion: ChunkRegion,
        excludeRowHeader: Boolean,
        excludeColumnHeader: Boolean,
    ): TableFrame {
        val result = tableSourceRef.evaluateValidateAndComputeChunkTableFrame(chunkRegion, excludeRowHeader, excludeColumnHeader)
        result.problems.filter { !reportedStyleFuncIndices.contains(it.funcInfo.index) }.let { newProblems ->
            if (newProblems.isNotEmpty()) {
                problemHandler.handleValidationProblems(newProblems)
                newProblems.forEach { reportedStyleFuncIndices.add(it.funcInfo.index) }
            }
        }
        return result.frame
    }
}