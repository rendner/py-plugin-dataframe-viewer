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
package cms.rendner.intellij.dataframe.viewer.python.bridge

import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkRegion
import cms.rendner.intellij.dataframe.viewer.models.chunked.SortCriteria
import cms.rendner.intellij.dataframe.viewer.models.chunked.TableStructure
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import com.intellij.openapi.Disposable
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
 * Interface to evaluate values of the Python class "TableSource".
 */
interface IPyTableSourceRef: Disposable {
    /**
     * Calls the "get_table_structure" method of the Python class.
     * The returned result contains information about the visible rows and columns of a DataFrame.
     *
     * @return structural information about the pandas DataFrame.
     * @throws EvaluateException in case the evaluation fails.
     */
    @Throws(EvaluateException::class)
    fun evaluateTableStructure(): TableStructure

    /**
     * Calls the "set_sort_criteria" method of the Python class.
     *
     * @param sortCriteria the new sort criteria, <code>null</code> for no sorting.
     */
    @Throws(EvaluateException::class)
    fun evaluateSetSortCriteria(sortCriteria: SortCriteria?)

    /**
     * Calls the "compute_chunk_table_frame" method of the Python class.
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
     * Calls the "get_org_indices_of_visible_columns" method of the Python class.
     *
     * This method is only called if a table source is filterable and the [TableStructure],
     * retrieved from the table source, returns different values for [TableStructure.orgColumnsCount]
     * and [TableStructure.columnsCount].
     *
     * @param partStart the start index in the original unfiltered column list.
     * @param maxColumns the number of requested columns, starting from [partStart].
     *
     * @return A list of indices.
     * The number of elements matches [maxColumns]. In case
     * [partStart] + [maxColumns] exceeds the length of the original columns, the list
     * is shorter than [maxColumns].
     */
    @Throws(EvaluateException::class)
    fun evaluateGetOrgIndicesOfVisibleColumns(partStart: Int, maxColumns: Int): List<Int>
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
data class StyleFunctionInfo(
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

/**
 * Description of the problem.
 *
 * @property reason the reason
 * @property message a message, only set if [reason] is [ProblemReason.EXCEPTION]
 * @property funcInfo a description about the reported styling function
 */
@Serializable
data class StyleFunctionValidationProblem(
    @SerialName("reason") val reason: ProblemReason,
    @SerialName("message") val message: String,
    @SerialName("func_info") val funcInfo: StyleFunctionInfo,
)

/**
 * Interface to evaluate values of the Python class "PatchedStyler".
 */
interface IPyPatchedStylerRef: IPyTableSourceRef {
    /**
     * Calls the "validate_and_compute_chunk_table_frame" method of the Python class.
     *
     * @param chunk the region of the data to evaluate and validate
     * @param excludeRowHeader if true, row headers are excluded from the result.
     * @param excludeColumnHeader if true, column headers are excluded from the result.
     * @return returns a table representation of the chunk and the validation problems found.
     * @throws EvaluateException in case the evaluation fails.
     */
    @Throws(EvaluateException::class)
    fun evaluateValidateAndComputeChunkTableFrame(
        chunk: ChunkRegion,
        excludeRowHeader: Boolean,
        excludeColumnHeader: Boolean,
    ): ValidatedTableFrame
}

@Serializable
data class TableFrameLegend(val index: List<String>, val column: List<String>)

@Serializable
data class TableFrameColumn(val dtype: String, val labels: List<String>, val describe: Map<String, String>? = null)

@Serializable
data class TableFrameCell(val value: String, val css: Map<String, String>?)

@Serializable
data class TableFrame(
    @SerialName("index_labels") val indexLabels: List<List<String>>?,
    val columns: List<TableFrameColumn>,
    val cells: List<List<TableFrameCell>>,
    val legend: TableFrameLegend?,
    )

@Serializable
data class ValidatedTableFrame(
    val frame: TableFrame,
    val problems: List<StyleFunctionValidationProblem>,
)

enum class DataSourceTransformHint {
    DictKeysAsRows,
}

enum class CreateTableSourceErrorKind {
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
data class CreateTableSourceConfig(
    @SerialName("temp_var_slot_id") val tempVarSlotId: String? = null,
    @SerialName("data_source_transform_hint") val dataSourceTransformHint: DataSourceTransformHint? = null,
    @SerialName("previous_fingerprint") val previousFingerprint: String? = null,
    @SerialName("filter_eval_expr") val filterEvalExpr: String? = null,
    @SerialName("filter_eval_expr_provide_frame")
    @Serializable(PythonBooleanSerializer::class)
    val filterEvalExprProvideFrame: Boolean = false,
)

@Serializable
data class CreateTableSourceFailure(
    /**
     * Describes the error kind.
     */
    @SerialName("error_kind") val errorKind: CreateTableSourceErrorKind,
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