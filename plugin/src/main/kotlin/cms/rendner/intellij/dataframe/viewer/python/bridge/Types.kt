/*
 * Copyright 2021-2026 cms.rendner (Daniel Schmidt)
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

import cms.rendner.intellij.dataframe.viewer.components.table.models.IHeaderLabel
import cms.rendner.intellij.dataframe.viewer.components.table.models.LegendHeaders
import cms.rendner.intellij.dataframe.viewer.components.table.models.TextAlign
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.DataSourceInfo
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyFrameAccessor
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import javax.swing.RowSorter

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

interface TestOnlyIPyTableSourceRefApi {
    /**
     * The identifier to refer to the table source on Python side.
     */
    fun testOnly_getRefExpr(): String
}

class TableSourceContext(
    val frameAccessor: IPluginPyFrameAccessor,
    val dataSourceInfo: DataSourceInfo,
) {
    val syntheticIdentifierType = dataSourceInfo.library
}

/**
 * Interface to evaluate values of the Python class "TableSource".
 */
interface IPyTableSourceRef {
    fun isAlive(): Boolean

    /**
     * Deletes the table source on Python side.
     */
    fun disposeTableSource()

    /**
     * Information about the visible rows and columns of a DataFrame.
     * This data is not updated when the table source is filtered.
     *
     * @return structural information about the DataFrame.
     */
    val initialTableStructure: TableStructure

    val context: TableSourceContext

    /**
     * Calls the "get_column_statistics" method of the Python class.
     * The returned result contains a statistic about the values of the specified column.
     *
     * @param colIndex the index of the column for which the statistics are to be loaded.
     * @return the column statistics.
     * @throws EvaluateException in case the evaluation fails.
     */
    @Throws(EvaluateException::class)
    fun getColumnStatistics(colIndex: Int): Map<String, String>

    /**
     * Calls the "compute_chunk_data" method of the Python class.
     *
     * @param chunkRegion the region of the data to evaluate
     * @param dataRequest the data which should be fetched.
     * @return returns requested chunk data.
     * @throws EvaluateException in case the evaluation fails.
     */
    @Throws(EvaluateException::class)
    fun computeChunkData(chunkRegion: ChunkRegion, dataRequest: ChunkDataRequest): ChunkData

    /**
     * Calls the "apply_sort" method of the Python class.
     *
     * @param config the new sort configuration to apply.
     * @throws EvaluateException in case the evaluation fails.
     */
    @Throws(EvaluateException::class)
    fun applySort(config: SortConfig)

    /**
     * Calls the "apply_filter" method of the Python class.
     *
     * Filtering a table source may reduce the amount of columns.
     * Therefore, the returned table structure and sort has to be respected.
     *
     * In case of an internal error, on Python side, the previous filter and sort state
     * are restored to stay in sync with the displayed data.
     *
     * @param config the new filter configuration to apply.
     * @throws EvaluateException in case the evaluation fails.
     */
    @Throws(EvaluateException::class)
    fun applyFilter(config: FilterConfig): FilterResult

    /**
     * Calls the "get_column_name_completion_variants" method of the Python class.
     *
     * Provides completion variants for column names.
     *
     * @param identifier identifier to reference the DataFrame.
     * @param isSyntheticIdentifier true if the identifier is the synthetic DataFrame identifier.
     */
    @Throws(EvaluateException::class)
    fun getColumnNameCompletionVariants(
        identifier: String,
        isSyntheticIdentifier: Boolean,
    ): List<ICompletionVariant>
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
     * Calls the "validate_and_compute_chunk_data" method of the Python class.
     *
     * @param chunkRegion the region of the data to evaluate and validate
     * @param dataRequest the data which should be fetched.
     * @return returns a table representation of the chunk and the validation problems found.
     * @throws EvaluateException in case the evaluation fails.
     */
    @Throws(EvaluateException::class)
    fun validateAndComputeChunkData(
        chunkRegion: ChunkRegion,
        dataRequest: ChunkDataRequest,
    ): ValidatedChunkData
}

/**
 * Represents a single cell in a table.
 *
 * @property value the string representation of the cell value
 * @property meta optional metadata associated with the cell (e.g., styling information)
 */
@Serializable
data class Cell(val value: String, val meta: String? = null)

/**
 * Contains the data of a chunk from a table source.
 *
 * @property rowHeaders the header labels for each row in the chunk, if requested
 * @property cells the cell values organized as a 2D list (rows × columns), if requested
 */
@Serializable
data class ChunkData(
    @SerialName("row_headers")
    @Serializable(RowHeadersSerializer::class)
    val rowHeaders: List<IHeaderLabel>?,
    val cells: List<List<Cell>>?,
)

/**
 * Contains chunk data along with validation problems from styling functions (pandas).
 *
 * @property data the chunk data
 * @property problems list of validation problems detected during styling function evaluation, if any
 */
@Serializable
data class ValidatedChunkData(
    val data: ChunkData,
    val problems: List<StyleFunctionValidationProblem>? = null,
)

/**
 * Describes the location and size of a chunk inside a table source.
 *
 * @property firstRow index of the first row of the chunk
 * @property firstColumn index of the first column of the chunk
 * @property numberOfRows number of rows in the chunk
 * @property numberOfColumns number of columns in the chunk
 */
data class ChunkRegion(
    val firstRow: Int,
    val firstColumn: Int,
    val numberOfRows: Int,
    val numberOfColumns: Int,
)

/**
 * Describes which data of a specific chunk has to be fetched.
 *
 * @property withCells if the cell values should be loaded.
 * @property withRowHeaders if the row headers of the DataFrame should be included.
 */
data class ChunkDataRequest(
    val withCells: Boolean,
    val withRowHeaders: Boolean,
)

/**
 * Describes the size of a chunk.
 *
 * @property rows number of rows in the chunk
 * @property columns number of columns in the chunk
 */
data class ChunkSize(val rows: Int, val columns: Int) {
    fun getIndexOfFirstRowInChunk(rowIndex: Int): Int {
        val rowBlockIndex = rowIndex / rows
        return rowBlockIndex * rows
    }

    fun getIndexOfFirstColumnInChunk(columnIndex: Int): Int {
        val columnBlockIndex = columnIndex / columns
        return columnBlockIndex * columns
    }
}

/**
 * Legend information for table structure with multi-level indices.
 *
 * @property index legend labels for the row index
 * @property column legend labels for the column index
 */
@Serializable
data class TableStructureLegend(
    val index: List<String>,
    val column: List<String>,
)

/**
 * Describes a single column in a table structure.
 *
 * @property id the column index
 * @property dtype the data type of the column
 * @property labels the column header labels (can be multi-level)
 * @property textAlign the text alignment for the column values, if specified
 */
@Serializable
data class TableStructureColumn(
    val id: Int,
    val dtype: String,
    @Serializable(HeaderLabelSerializer::class)
    val labels: IHeaderLabel,
    @SerialName("text_align")
    @Serializable(TextAlignSerializer::class)
    val textAlign: TextAlign? = null,
)

/**
 * Information about the columns of a table structure.
 *
 * @property columns list of column descriptions
 * @property legend legend headers for multi-level indices, if present
 */
@Serializable
data class TableStructureColumnInfo(
    val columns: List<TableStructureColumn>,
    @Serializable(LegendHeadersSerializer::class)
    val legend: LegendHeaders? = null,
)

/**
 * Describes the table structure of a table source.
 *
 * @param orgRowsCount number of rows of the original unfiltered table source
 * @param orgColumnsCount number of visible columns of the original unfiltered table source
 * @param rowsCount number of rows in the table source
 * @param columnsCount number of columns in the table source
 * @param columnInfo information about the columns of the table source
 */
@Serializable
data class TableStructure(
    @SerialName("org_rows_count") val orgRowsCount: Int,
    @SerialName("org_columns_count") val orgColumnsCount: Int,
    @SerialName("rows_count") val rowsCount: Int,
    @SerialName("columns_count") val columnsCount: Int,
    @SerialName("column_info") val columnInfo: TableStructureColumnInfo,
)

/**
 * Information about a table source.
 *
 * @property kind the kind of table source (e.g., "TABLE_SOURCE", "PATCHED_STYLER")
 * @property structure the structural information about the table
 */
@Serializable
data class TableInfo(
    @SerialName("kind") val kind: String,
    @SerialName("structure") val structure: TableStructure,
)

/**
 * Describes the sort config for a table source.
 *
 * @param byIndex list of column indices to be sorted
 * @param ascending the sort order for each specified column, must match the length of [byIndex]
 */
@Serializable
data class SortConfig(
    @SerialName("column_idx") val byIndex: List<Int> = emptyList(),
    @SerialName("ascending") val ascending: List<Boolean> = emptyList(),
) {
    init {
        require(byIndex.size == ascending.size) { "The length of 'byIndex' and 'ascending' must match." }
    }

    companion object {
        fun fromSortKeys(sortKeys: List<RowSorter.SortKey>): SortConfig {
            val (columns, ascending) = sortKeys.fold(
                Pair(mutableListOf<Int>(), mutableListOf<Boolean>())
            ) { pair, sortKey ->
                pair.first.add(sortKey.column)
                pair.second.add(sortKey.sortOrder == javax.swing.SortOrder.ASCENDING)
                pair
            }
            return SortConfig(columns, ascending)
        }
    }
}

/**
 * Hints for transforming data sources before creating a table source.
 */
enum class DataSourceTransformHint {
    /**
     * Transform a dictionary by using its keys as rows.
     */
    DictKeysAsRows,
}

/**
 * Enumeration of error kinds that can occur when creating a table source.
 */
enum class TableSourceErrorKind {
    UNKNOWN_ERROR,
    /**
     * The evaluation resulted in an [EvaluateException].
     */
    EVAL_FAILED,
    /**
     * The converted data source is unknown to the plugin.
     * A data source is converted into a table source.
     */
    UNSUPPORTED_TABLE_SOURCE_TYPE,
    /**
     * The data source is not supported by the plugin.
     */
    UNSUPPORTED_DATA_SOURCE_TYPE,
    /**
     * The type of the evaluated filter is not supported by the plugin.
     * The field [TableSourceExceptionData.info] contains the type of the evaluated filter.
     */
    UNSUPPORTED_FILTER_FRAME_TYPE,
}

/**
 * Configuration options for creating a table source.
 *
 * @property dataSourceTransformHint hint for transforming the data source (e.g., dict keys as rows)
 */
@Serializable
data class CreateTableSourceConfig(
    @SerialName("data_source_transform_hint") val dataSourceTransformHint: DataSourceTransformHint? = null,
)

/**
 * Configuration options for filtering the table source.
 *
 * @property expr Python expression to filter the data source
 * @property syntheticIdentifierUsed whether to provide the data source (DataFrame) variable when evaluating the filter expression
 */
@Serializable
data class FilterConfig(
    @SerialName("expr") val expr: String? = null,
    @SerialName("synthetic_identifier_used")
    @Serializable(PythonBooleanSerializer::class)
    val syntheticIdentifierUsed: Boolean = false,
)

@Serializable
data class FilterResult(
    @SerialName("sort") val sort: SortConfig,
    @SerialName("table_structure") val tableStructure: TableStructure,
)

@Serializable
data class TableSourceExceptionData(
    /**
     * Describes the error kind.
     */
    @SerialName("error_kind") val errorKind: TableSourceErrorKind,
    /**
     * Can be a failure message or any other information (depends on the [errorKind])
     */
    @SerialName("info") val info: String,
    @SerialName("cause")
    @Serializable(PythonOptionalStringSerializer::class)
    val cause: String? = null,
)

/**
 * Completion variant for column names.
 *
 * @property fqType the fully qualified type of the completion variant
 */
sealed interface ICompletionVariant {
    val fqType: String
}

/**
 * A single completion variant for a column name.
 *
 * @property fqType the fully qualified type of the completion variant
 * @property value the completion value (e.g., column name)
 */
@Serializable
data class CompletionVariant(
    @SerialName("fq_type") override val fqType: String,
    @SerialName("value") val value: String,
): ICompletionVariant

/**
 * A nested completion variant for multi-level column names.
 *
 * In DataFrames with hierarchical/multi-level column indices (e.g., pandas MultiIndex columns),
 * this represents a completion variant that contains child variants for each level of the hierarchy.
 * For example, a DataFrame with columns like ('A', 'x'), ('A', 'y'), ('B', 'x') would have
 * nested variants where 'A' and 'B' are parent levels containing their respective child columns.
 *
 * @property fqType the fully qualified type of the completion variant
 * @property children list of child completion variants representing the next level in the hierarchy
 */
@Serializable
data class NestedCompletionVariant(
    @SerialName("fq_type") override val fqType: String,
    @SerialName("children") val children: List<CompletionVariant>,
): ICompletionVariant

class PythonBooleanSerializer : KSerializer<Boolean> {
    override val descriptor = PrimitiveSerialDescriptor("cms.rendner.PythonBoolean", PrimitiveKind.BOOLEAN)

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

class PythonOptionalStringSerializer : KSerializer<String?> {
    override val descriptor = PrimitiveSerialDescriptor("cms.rendner.PythonOptionalString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val value = decoder.decodeString()
        return if (value == "None") null else value
    }

    override fun serialize(encoder: Encoder, value: String?) {
        encoder.encodeString(value ?: "None")
    }
}

class TextAlignSerializer : KSerializer<TextAlign?> {
    override val descriptor = PrimitiveSerialDescriptor("cms.rendner.PythonTextAlign", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): TextAlign? {
        return TextAlign.unpackOrConvert(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: TextAlign?) {
        throw UnsupportedOperationException("Serialization of TextAlign is not supported")
    }
}

class RowHeadersSerializer : KSerializer<List<IHeaderLabel>?> {
    private val delegateSerializer = ListSerializer(ListSerializer(serializer<String>()))

    override val descriptor = delegateSerializer.descriptor

    override fun deserialize(decoder: Decoder): List<IHeaderLabel>? {
        val rawHeaders = decoder.decodeSerializableValue(delegateSerializer)
        return rawHeaders.map { IHeaderLabel.from(it) }
    }

    override fun serialize(encoder: Encoder, value: List<IHeaderLabel>?) {
        throw UnsupportedOperationException("Serialization of IHeaderLabel is not supported")
    }
}

class HeaderLabelSerializer : KSerializer<IHeaderLabel> {
    private val delegateSerializer = ListSerializer(serializer<String>())

    override val descriptor = delegateSerializer.descriptor

    override fun deserialize(decoder: Decoder): IHeaderLabel {
        val label = decoder.decodeSerializableValue(delegateSerializer)
        return IHeaderLabel.from(label)
    }

    override fun serialize(encoder: Encoder, value: IHeaderLabel) {
        throw UnsupportedOperationException("Serialization of IHeaderLabel is not supported")
    }
}

class LegendHeadersSerializer : KSerializer<LegendHeaders?> {
    @Serializable
    private data class RawLegend(val index: List<String>, val column: List<String>)

    private val delegateSerializer = RawLegend.serializer()

    override val descriptor = delegateSerializer.descriptor

    override fun deserialize(decoder: Decoder): LegendHeaders? {
        val rawLegend = decoder.decodeSerializableValue(delegateSerializer)
        return LegendHeaders(
            row = IHeaderLabel.from(rawLegend.index),
            column = IHeaderLabel.from(rawLegend.column)
        )
    }

    override fun serialize(encoder: Encoder, value: LegendHeaders?) {
        throw UnsupportedOperationException("Serialization of LegendHeaders is not supported")
    }
}

object CompletionVariantSerializer : JsonContentPolymorphicSerializer<ICompletionVariant>(ICompletionVariant::class) {
    override fun selectDeserializer(element: JsonElement) = when {
        "children" in element.jsonObject -> NestedCompletionVariant.serializer()
        else -> CompletionVariant.serializer()
    }
}


val bridgeSerializersModule = SerializersModule {
    polymorphicDefaultDeserializer(ICompletionVariant::class) { CompletionVariantSerializer }
}
