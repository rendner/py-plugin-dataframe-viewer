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
package cms.rendner.intellij.dataframe.viewer.models.chunked.evaluator

import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkRegion
import cms.rendner.intellij.dataframe.viewer.models.chunked.IChunkEvaluator
import cms.rendner.intellij.dataframe.viewer.python.bridge.IPyPatchedStylerRef

class AllAtOnceEvaluator(
    private val patchedStyler: IPyPatchedStylerRef,
) : IChunkEvaluator {

    fun evaluate(): String {
        return patchedStyler.evaluateRenderUnpatched()
    }

    override fun evaluate(
        chunkRegion: ChunkRegion,
        excludeRowHeaders: Boolean,
        excludeColumnHeaders: Boolean
    ): String {
        return evaluate()
    }
}