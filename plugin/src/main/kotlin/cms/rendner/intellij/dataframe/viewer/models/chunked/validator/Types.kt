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
package cms.rendner.intellij.dataframe.viewer.models.chunked.validator

import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkRegion
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
     * @param details list of additional information to describe the styling function which caused a problem
     */
    fun handleValidationProblems(
        region: ChunkRegion,
        validationStrategy: ValidationStrategyType,
        problems: List<StyleFunctionValidationProblem>,
        details: List<StyleFunctionDetails>,
    )
}

/**
 * Enumeration of validation strategies.
 *
 * The names of the enum values match with the "ValidationStrategyType" Python class.
 */
enum class ValidationStrategyType {
    /**
     * Fast validation, but not deterministic and not very accurate.
     *
     * Halves the region to be inspected alternately horizontally or vertically and compares the two parts.
     * Since the region is alternately bisected horizontally and vertically, the result is not deterministic.
     *
     * For a deterministic validation use [PRECISION] or call the validation twice for
     * the same region when using [FAST].
     */
    FAST,

    /**
     * Slow but deterministic and more accurate validation then [FAST].
     *
     * Halves the region to validate vertically and horizontally and compares the four parts.
     */
    PRECISION,

    /**
     * No validation.
     * This is a marker value and doesn't exist in the "ValidationStrategyType" Python class.
     */
    DISABLED;

    companion object {
        fun valueOfOrDisabled(value: String): ValidationStrategyType {
            return try {
                ValidationStrategyType.valueOf(value.uppercase())
            } catch (ignore:IllegalArgumentException) {
                DISABLED
            }
        }
    }
}

/**
 * Describes a styling function which was registered via pandas "Styler::apply" or "Styler::applymap".
 *
 * @property index the index of the styling function in the pandas "Styler._todo"
 * @property qname the qualified name of the styling function
 * @property resolvedName the resolved name of the styling function
 * @property axis the axis parameter of the styling function (only set if [isApply] is true)
 * @property isPandasBuiltin true if styling function is a builtin styling method
 * @property isSupported true if the plugin supports the styling function
 * @property isApply true if styling function was added via "Styler::apply"
 * @property isChunkParentRequested true if styling function requests the "chunk_parent"
 */
@Serializable
data class StyleFunctionDetails(
    @SerialName("index") val index: Int,
    @SerialName("qname") val qname: String,
    @SerialName("resolved_name") val resolvedName: String,
    @SerialName("axis") val axis: String,
    @SerialName("is_pandas_builtin") val isPandasBuiltin: Boolean,
    @SerialName("is_supported") val isSupported: Boolean,
    @SerialName("is_apply") val isApply: Boolean,
    @SerialName("is_chunk_parent_requested") val isChunkParentRequested: Boolean,
)

/**
 * Description of the problem.
 *
 * @property index the index of the styling function in the pandas "Styler._todo"
 * @property reason the reason
 * @property message a message, only set if [reason] is [ProblemReason.EXCEPTION]
 */
@Serializable
data class StyleFunctionValidationProblem(
    @SerialName("index") val index: Int,
    @SerialName("reason") val reason: ProblemReason,
    @SerialName("message") val message: String = "",
)

/**
 * Enumeration of problems occurred during the validation process.
 */
enum class ProblemReason {
    /**
     * The compared parts didn't match.
     */
    NOT_EQUAL,

    /**
     * There was an exception during the validation.
     * Therefore, no validation could be done.
     */
    EXCEPTION,
}