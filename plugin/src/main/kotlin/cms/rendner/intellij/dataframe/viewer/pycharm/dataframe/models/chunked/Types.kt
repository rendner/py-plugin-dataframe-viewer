package cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked

import cms.rendner.intellij.dataframe.viewer.core.component.models.IHeaderLabel
import cms.rendner.intellij.dataframe.viewer.core.component.models.LegendHeaders
import cms.rendner.intellij.dataframe.viewer.core.component.models.Value
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.ChunkSize

interface IChunkEvaluator {
    fun evaluate(chunkCoordinates: ChunkCoordinates, excludeRowHeaders: Boolean, excludeColumnHeaders: Boolean): String
    val chunkSize: ChunkSize
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

data class ChunkCoordinates(
    val indexOfFirstRow: Int,
    val indexOfFirstColumn: Int
)

data class ChunkData(
    val headerLabels: ChunkHeaderLabels,
    val values: ChunkValues
)

