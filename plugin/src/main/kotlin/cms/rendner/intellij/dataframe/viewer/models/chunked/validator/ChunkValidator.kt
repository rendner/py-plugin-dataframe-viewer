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
package cms.rendner.intellij.dataframe.viewer.models.chunked.validator

import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkRegion
import cms.rendner.intellij.dataframe.viewer.python.bridge.IPyPatchedStylerRef
import cms.rendner.intellij.dataframe.viewer.python.bridge.StyleFunctionInfo
import cms.rendner.intellij.dataframe.viewer.python.bridge.ValidationStrategyType

/**
 * Validates that registered pandas styling functions produce a stable result when these are applied to the chunks.
 *
 * Validating a whole DataFrame against the result of composite single chunks is not doable in real-time.
 * Instead, only the output for one chunk is generated at a time and compared with the combined output
 * of smaller chunks. Errors or problems are reported to [IChunkValidationProblemHandler.handleValidationProblems].
 *
 * The validator can't ensure that the generated output is the same as the output of the original DataFrame. It can only
 * guarantee that the combined output of the smaller chunks match with the generated output of the chunk.
 *
 * @param patchedStyler the styler from which the chunks should be validated.
 * @param validationStrategy the validation strategy to use.
 * @param problemHandler all detected problems are forwarded to this handler.
 */
class ChunkValidator(
    private val patchedStyler: IPyPatchedStylerRef,
    private val validationStrategy: ValidationStrategyType,
    private val problemHandler: IChunkValidationProblemHandler,
) {
    @Volatile
    private var functionInfo: List<StyleFunctionInfo>? = null

    fun validate(region: ChunkRegion) {
        val result = patchedStyler.evaluateValidateStyleFunctions(region, validationStrategy)
        if (result.isNotEmpty()) {
            val info = fetchFunctionInfoIfNotPresent()
            problemHandler.handleValidationProblems(
                region,
                validationStrategy,
                result.map { p -> ValidationProblem(info[p.index], p.reason, p.message) },
            )
        }
    }

    private fun fetchFunctionInfoIfNotPresent(): List<StyleFunctionInfo> {
        return functionInfo ?: patchedStyler.evaluateStyleFunctionInfo().also { functionInfo = it }
    }
}