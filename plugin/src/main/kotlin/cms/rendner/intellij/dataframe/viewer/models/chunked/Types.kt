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
package cms.rendner.intellij.dataframe.viewer.models.chunked

import cms.rendner.intellij.dataframe.viewer.models.IHeaderLabel
import cms.rendner.intellij.dataframe.viewer.models.Value
import cms.rendner.intellij.dataframe.viewer.python.bridge.TableFrame
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An interface to evaluate data of a table source.
 */
interface IChunkEvaluator {
    /**
     * Evaluates a table like representation for a chunk of a table source.
     * Excluding already fetched headers reduces the amount of data which to be fetched and parsed.
     *
     * @param chunkRegion the region of the data to evaluate
     * @param excludeRowHeader if true, the row headers are excluded from the result.
     * @param newSorting if not null, sorting is applied and data is taken from the updated DataFrame.
     * @return returns a table representation of the chunk.
     * @throws EvaluateException in case the evaluation fails.
     */
    @Throws(EvaluateException::class)
    fun evaluateTableFrame(
        chunkRegion: ChunkRegion,
        excludeRowHeader: Boolean,
        newSorting: SortCriteria?,
    ): TableFrame

    @Throws(EvaluateException::class)
    fun evaluateColumnStatistics(columnIndex: Int): Map<String, String>
}

interface IChunkValues {
    fun value(rowIndexInChunk: Int, columnIndexInChunk: Int): Value
}

/**
 * The values of a row, without header.
 */
data class ChunkValuesRow(val values: List<Value>)

/**
 * Temporary values of a row, without header, until real data is fetched.
 */
data class ChunkValuesPlaceholder(private val placeholder: Value) : IChunkValues {
    override fun value(rowIndexInChunk: Int, columnIndexInChunk: Int) = placeholder
}

/**
 * The values of a chunk.
 */
data class ChunkValues(val rows: List<ChunkValuesRow>) : IChunkValues {
    override fun value(rowIndexInChunk: Int, columnIndexInChunk: Int) = rows[rowIndexInChunk].values[columnIndexInChunk]
}

/**
 * The headers of a chunk.
 *
 * @property rows list of row headers, null if there are no row labels
 */
data class ChunkHeaderLabels(
    val rows: List<IHeaderLabel>?
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
 * The data of a chunk.
 * @property headerLabels the row and column labels of the chunk.
 * @property values the values of the chunk.
 */
data class ChunkData(
    val headerLabels: ChunkHeaderLabels?,
    val values: IChunkValues
)

/**
 * Describes the size of a chunk.
 *
 * @property rows number of rows in the chunk
 * @property columns number of columns in the chunk
 */
data class ChunkSize(val rows: Int, val columns: Int)

@Serializable
data class TableStructureLegend(
    val index: List<String>,
    val column: List<String>,
)

@Serializable
data class TableStructureColumn(
    val id: Int,
    val dtype: String,
    val labels: List<String>,
)

@Serializable
data class TableStructureColumnInfo(
    val columns: List<TableStructureColumn>,
    val legend: TableStructureLegend? = null,
)

/**
 * Describes the table structure of a table source.
 *
 * @param orgRowsCount number of rows of the original unfiltered table source
 * @param orgColumnsCount number of visible columns of the original unfiltered table source
 * @param rowsCount number of rows in the table source
 * @param columnsCount number of columns in the table source
 * @param fingerprint fingerprint of the table source
 */
@Serializable
data class TableStructure(
    @SerialName("org_rows_count") val orgRowsCount: Int,
    @SerialName("org_columns_count") val orgColumnsCount: Int,
    @SerialName("rows_count") val rowsCount: Int,
    @SerialName("columns_count") val columnsCount: Int,
    @SerialName("fingerprint") val fingerprint: String,
    @SerialName("column_info") val columnInfo: TableStructureColumnInfo,
)

@Serializable
data class TableInfo(
    @SerialName("kind") val kind: String,
    @SerialName("structure") val structure: TableStructure,
)


/**
 * Describes the sort criteria for a table source.
 *
 * @param byIndex list of column indices to be sorted
 * @param ascending the sort order for each specified column, must match the length of [byIndex]
 */
data class SortCriteria(
    val byIndex: List<Int> = emptyList(),
    val ascending: List<Boolean> = emptyList(),
) {
    init {
        require(byIndex.size == ascending.size) { "The length of 'byIndex' and 'ascending' must match." }
    }
}
