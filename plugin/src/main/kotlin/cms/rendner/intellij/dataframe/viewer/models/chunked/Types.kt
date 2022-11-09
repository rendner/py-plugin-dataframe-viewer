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
package cms.rendner.intellij.dataframe.viewer.models.chunked

import cms.rendner.intellij.dataframe.viewer.models.IHeaderLabel
import cms.rendner.intellij.dataframe.viewer.models.LegendHeaders
import cms.rendner.intellij.dataframe.viewer.models.Value
import cms.rendner.intellij.dataframe.viewer.python.bridge.HTMLPropsTable
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An interface to evaluate data of a pandas DataFrame.
 */
interface IChunkEvaluator {
    /**
     * Evaluates the HTML properties for a chunk of a pandas DataFrame.
     *
     * Pandas generates HTML properties to populate an HTML template when a styler is rendered to HTML.
     * The Python plugin code transforms these HTML properties into an easier processable data structure.
     *
     * Excluding already fetched headers reduces the amount of data which to be fetched and parsed.
     *
     * @param chunkRegion the region of the data to evaluate
     * @param excludeRowHeaders if result should not include the headers of the rows
     * @param excludeColumnHeaders if result should not include the headers of the columns
     * @return returns a table like structure, of HTML properties, similar to the evaluated chunk.
     * @throws EvaluateException in case the evaluation fails.
     */
    @Throws(EvaluateException::class)
    fun evaluateHTMLProps(
        chunkRegion: ChunkRegion,
        excludeRowHeaders: Boolean,
        excludeColumnHeaders: Boolean
    ): HTMLPropsTable

    /**
     * Applies the sort criteria to the styled pandas DataFrame.
     * @throws EvaluateException in case the evaluation fails.
     */
    @Throws(EvaluateException::class)
    fun setSortCriteria(sortCriteria: SortCriteria)
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
 * @property legend the legend headers (contain additional information for multi index DataFrames)
 * @property columns list of column headers
 * @property rows list of rows headers
 */
data class ChunkHeaderLabels(
    val legend: LegendHeaders,
    val columns: List<IHeaderLabel>,
    val rows: List<IHeaderLabel>
)

/**
 * Describes the location and size of a chunk inside a pandas DataFrame.
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
    val headerLabels: ChunkHeaderLabels,
    val values: IChunkValues
)

/**
 * Describes the size of a chunk.
 *
 * @property rows number of rows in the chunk
 * @property columns number of columns in the chunk
 */
data class ChunkSize(val rows: Int, val columns: Int)

/**
 * Describes the table structure of a pandas DataFrame.
 *
 * @param orgRowsCount number of rows of the original unfiltered DataFrame
 * @param orgColumnsCount number of visible columns of the original unfiltered DataFrame
 * @param rowsCount number of rows in the DataFrame
 * @param columnsCount number of columns in the DataFrame
 * @param rowLevelsCount number of headers which build the label/index of a row, number >= 0
 * @param columnLevelsCount number of headers which build the label of a column, number >= 0
 * @param hideRowHeader is true when no row-header should be displayed
 * @param hideColumnHeader is true when no column-header should be displayed
 */
@Serializable
data class TableStructure(
    @SerialName("org_rows_count") val orgRowsCount: Int,
    @SerialName("org_columns_count") val orgColumnsCount: Int,
    @SerialName("rows_count") val rowsCount: Int,
    @SerialName("columns_count") val columnsCount: Int,
    @SerialName("row_levels_count") val rowLevelsCount: Int,
    @SerialName("column_levels_count") val columnLevelsCount: Int,
    @SerialName("hide_row_header") val hideRowHeader: Boolean,
    @SerialName("hide_column_header") val hideColumnHeader: Boolean
)

/**
 * Describes the sort criteria for a pandas DataFrame.
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
