/*
 * Copyright 2021-2025 cms.rendner (Daniel Schmidt)
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
import cms.rendner.intellij.dataframe.viewer.models.TextAlign
import cms.rendner.intellij.dataframe.viewer.python.bridge.Cell
import cms.rendner.intellij.dataframe.viewer.python.bridge.ChunkData
import cms.rendner.intellij.dataframe.viewer.python.bridge.TextAlignSerializer
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An interface to evaluate data of a table source.
 */
interface IChunkEvaluator {
    /**
     * Evaluates chunk data of a table source.
     *
     * @param chunkRegion the region of the data to evaluate
     * @param dataRequest the data which should be fetched.
     * @param newSorting if not null, sorting is applied and data is taken from the updated DataFrame.
     * @return returns the chunk data of the specified region.
     * @throws EvaluateException in case the evaluation fails.
     */
    @Throws(EvaluateException::class)
    fun evaluateChunkData(
        chunkRegion: ChunkRegion,
        dataRequest: ChunkDataRequest,
        newSorting: SortCriteria?,
    ): ChunkData

    @Throws(EvaluateException::class)
    fun evaluateColumnStatistics(columnIndex: Int): Map<String, String>
}

interface IChunkValues {
    fun getValue(rowIndexInChunk: Int, columnIndexInChunk: Int): Cell
}

/**
 * The values of a row, without header.
 */
data class ChunkValuesRow(private var values: List<Cell>) {
    fun getValue(index: Int): Cell = values[index]
}

/**
 * A placeholder for a pending chunk, until data is fetched.
 */
data class ChunkValuesPlaceholder(private val placeholder: Cell) : IChunkValues {
    override fun getValue(rowIndexInChunk: Int, columnIndexInChunk: Int) = placeholder
}

/**
 * The values of a chunk.
 */
data class ChunkValues(private val rows: List<ChunkValuesRow>) : IChunkValues {
    override fun getValue(rowIndexInChunk: Int, columnIndexInChunk: Int) = rows[rowIndexInChunk].getValue(columnIndexInChunk)
}

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
 * @property values (optional), the cell values of the chunk.
 * @property rowHeaderLabels (optional), the row labels of the chunk.
*/
data class ChunkData(
    val values: IChunkValues? = null,
    val rowHeaderLabels: List<IHeaderLabel>? = null,
)

/**
 * Describes which data of a specific chunk has to be fetched.
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
    @SerialName("text_align")
    @Serializable(TextAlignSerializer::class)
    val textAlign: TextAlign? = null,
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
