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
import cms.rendner.intellij.dataframe.viewer.python.bridge.ProblemReason
import cms.rendner.intellij.dataframe.viewer.python.bridge.StyleFunctionInfo
import cms.rendner.intellij.dataframe.viewer.python.bridge.ValidationStrategyType

/**
 * An interface to handle validation problems.
 */
interface IChunkValidationProblemHandler {
    /**
     * The handler is only called in case of problems.
     *
     * @param region the validated region of the DataFrame
     * @param validationStrategy the used validation strategy
     * @param problems a list of problems
     */
    fun handleValidationProblems(
        region: ChunkRegion,
        validationStrategy: ValidationStrategyType,
        problems: List<ValidationProblem>,
    )
}

/**
 * Description of the problem.
 *
 * @property funcInfo describes the styling function which caused the problem
 * @property reason the reason
 * @property message a message, only set if [reason] is [ProblemReason.EXCEPTION]
 */
data class ValidationProblem(
    val funcInfo: StyleFunctionInfo,
    val reason: ProblemReason,
    val message: String = "",
)