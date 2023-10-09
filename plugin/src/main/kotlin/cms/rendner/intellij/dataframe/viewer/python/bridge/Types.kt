/*
 * Copyright 2023 cms.rendner (Daniel Schmidt)
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
package cms.rendner.intellij.dataframe.viewer.python.bridge

import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkRegion
import cms.rendner.intellij.dataframe.viewer.models.chunked.SortCriteria
import cms.rendner.intellij.dataframe.viewer.models.chunked.TableStructure
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.StyleFunctionDetails
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.StyleFunctionValidationProblem
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.ValidationStrategyType
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

data class PandasVersion(val major: Int, val minor: Int, val rest: String = "") {
    companion object {
        /**
         * Creates a version instance from a pandas version string.
         *
         * pandas version is defined as described in pep-440:
         * Version Identification and Dependency Specification
         * https://peps.python.org/pep-0440/
         */
        fun fromString(value: String): PandasVersion {
            // versions can be:
            // - '1.2.0'
            // - '1.4.0.dev0+1574.g46ddb8ef88'
            // - '2.0.0rc0'
            // this is a very naive way to extract the required info (but OK)
            val parts = value.split(".")
            return PandasVersion(
                parts[0].toInt(),
                parts[1].toInt(),
                value.substring(parts[0].length + parts[1].length + 2)
            )
        }
    }
}

/**
 * Interface to evaluate values of the Python class "PatchedStyler".
 */
interface IPyPatchedStylerRef {
    /**
     * Calls the "get_table_structure" method of the Python class "PatchedStyler".
     * The returned result contains information about the visible rows and columns of a DataFrame.
     *
     * @return structural information about the pandas DataFrame.
     * @throws EvaluateException in case the evaluation fails.
     *
     * [pandas-docs - Styler.to_html](https://pandas.pydata.org/docs/reference/api/pandas.io.formats.style.Styler.to_html.html)
     */
    @Throws(EvaluateException::class)
    fun evaluateTableStructure(): TableStructure

    /**
     * Calls the "get_style_function_details" method of the Python class "PatchedStyler".
     *
     * The result contains details about each styling function registered via "Styler::apply" and "Styler::applymap".
     *
     * @return list of details about registered styling functions.
     * @throws EvaluateException in case the evaluation fails.
     */
    @Throws(EvaluateException::class)
    fun evaluateStyleFunctionDetails(): List<StyleFunctionDetails>

    /**
     * Calls the "validate_style_functions" method of the Python class "PatchedStyler".
     *
     * The result contains the found validation problems.
     *
     * @param chunk the region of the data to validate
     * @param validationStrategy the validation strategy to use, in case of [ValidationStrategyType.DISABLED]
     *  the method immediately returns an empty list without evaluation the specified region.
     * @return the found problems or an empty list of no problems could be found by the used validation strategy.
     * @throws EvaluateException in case the evaluation fails.
     */
    @Throws(EvaluateException::class)
    fun evaluateValidateStyleFunctions(
        chunk: ChunkRegion,
        validationStrategy: ValidationStrategyType,
    ): List<StyleFunctionValidationProblem>

    /**
     * Calls the "set_sort_criteria" method of the Python class "PatchedStyler".
     *
     * @param sortCriteria the new sort criteria, <code>null</code> for no sorting.
     */
    @Throws(EvaluateException::class)
    fun evaluateSetSortCriteria(sortCriteria: SortCriteria?)

    /**
     * Calls the "compute_chunk_table_frame" method of the Python class "PatchedStyler".
     *
     * @param chunk the region of the data to evaluate
     * @param excludeRowHeader if true, row headers are excluded from the result.
     * @param excludeColumnHeader if true, column headers are excluded from the result.
     * @return returns a table representation of the chunk.
     * @throws EvaluateException in case the evaluation fails.
     */
    @Throws(EvaluateException::class)
    fun evaluateComputeChunkTableFrame(
        chunk: ChunkRegion,
        excludeRowHeader: Boolean,
        excludeColumnHeader: Boolean
    ): TableFrame

    /**
     * Calls the "get_org_indices_of_visible_columns" method of the Python class "PatchedStyler".
     */
    @Throws(EvaluateException::class)
    fun evaluateGetOrgIndicesOfVisibleColumns(partStart: Int, maxColumns: Int): List<Int>
}

@Serializable
data class TableFrameLegend(val index: List<String>, val column: List<String>)

@Serializable
data class TableFrameCell(val value: String, val css: Map<String, String>?)

@Serializable
data class TableFrame(
    @SerialName("index_labels") val indexLabels: List<List<String>>,
    @SerialName("column_labels") val columnLabels: List<List<String>>,
    val cells: List<List<TableFrameCell>>,
    val legend: TableFrameLegend?,
    )

enum class DataSourceToFrameHint {
    DictKeysAsRows,
}

enum class CreatePatchedStylerErrorKind {
    /**
     * The evaluation resulted in an [EvaluateException].
     */
    EVAL_EXCEPTION,
    /**
     * Re-evaluated data-source has another type.
     */
    RE_EVAL_DATA_SOURCE_OF_WRONG_TYPE,
    /**
     * The data-source is not supported by the plugin.
     */
    UNSUPPORTED_DATA_SOURCE_TYPE,
    /**
     * The data-source has another fingerprint as expected.
     */
    INVALID_FINGERPRINT,
    /**
     * The filter expression can't be evaluated.
     */
    FILTER_FRAME_EVAL_FAILED,
    /**
     * The filter expression evaluates to an unexpected type (DataFrame is expected).
     */
    FILTER_FRAME_OF_WRONG_TYPE,
}

@Serializable
data class CreatePatchedStylerConfig(
    @SerialName("data_source_to_frame_hint") val dataSourceToFrameHint: DataSourceToFrameHint? = null,
    @SerialName("previous_fingerprint") val previousFingerprint: String? = null,
    @SerialName("filter_eval_expr") val filterEvalExpr: String? = null,
    @SerialName("filter_eval_expr_provide_frame")
    @Serializable(PythonBooleanSerializer::class)
    val filterEvalExprProvideFrame: Boolean = false,
)

@Serializable
data class CreatePatchedStylerFailure(
    /**
     * Describes the error kind.
     */
    @SerialName("error_kind") val errorKind: CreatePatchedStylerErrorKind,
    /**
     * Can be a failure message or any other information (depends on the [errorKind])
     */
    @SerialName("info") val info: String,
)

class PythonBooleanSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("PythonBooleanDescriptor", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean {
        return when (decoder.decodeString()) {
            "True", "true" -> true
            "False", "false" -> false
            else -> false
        }
    }

    override fun serialize(encoder: Encoder, value: Boolean) {
        encoder.encodeString(if (value) "True" else "False")
    }
}