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

interface IChunkEvaluator {
    fun evaluate(chunkRegion: ChunkRegion, excludeRowHeaders: Boolean, excludeColumnHeaders: Boolean): String
}

interface IChunkValues {
    fun value(rowIndexInChunk: Int, columnIndexInChunk: Int): Value
}

data class ChunkValuesRow(val values: List<Value>)
data class ChunkValues(val rows: List<ChunkValuesRow>) : IChunkValues {
    override fun value(rowIndexInChunk: Int, columnIndexInChunk: Int) = rows[rowIndexInChunk].values[columnIndexInChunk]
}

data class ChunkValuesPlaceholder(private val placeholder: Value) : IChunkValues {
    override fun value(rowIndexInChunk: Int, columnIndexInChunk: Int) = placeholder
}

data class ChunkHeaderLabels(
    val legend: LegendHeaders,
    val columns: List<IHeaderLabel>,
    val rows: List<IHeaderLabel>
)

data class ChunkRegion(
    val firstRow: Int,
    val firstColumn: Int,
    val numberOfRows: Int,
    val numberOfColumns: Int,
)

data class ChunkData(
    val headerLabels: ChunkHeaderLabels,
    val values: ChunkValues
)

data class ChunkSize(val rows: Int, val columns: Int)

/**
 * @param rowsCount number of rows
 * @param columnsCount number of columns
 * @param rowLevelsCount number of headers which build the label/index of a row, number >= 0.
 * @param columnLevelsCount number of headers which build the label of a column, number >= 0.
 * @param hideRowHeader is true when no row-header should be displayed
 * @param hideColumnHeader is true when no column-header should be displayed
 */
data class TableStructure(
    val rowsCount: Int,
    val columnsCount: Int,
    val rowLevelsCount: Int,
    val columnLevelsCount: Int,
    val hideRowHeader: Boolean,
    val hideColumnHeader: Boolean
)
