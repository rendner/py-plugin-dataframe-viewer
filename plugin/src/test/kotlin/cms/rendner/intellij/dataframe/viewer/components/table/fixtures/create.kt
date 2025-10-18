package cms.rendner.intellij.dataframe.viewer.components.table.fixtures

import cms.rendner.intellij.dataframe.viewer.components.table.models.HeaderLabel
import cms.rendner.intellij.dataframe.viewer.python.bridge.Cell
import cms.rendner.intellij.dataframe.viewer.python.bridge.ChunkData
import cms.rendner.intellij.dataframe.viewer.python.bridge.ChunkRegion

fun createDefaultChunkData(region: ChunkRegion): ChunkData {
    val cells = if (region.numberOfRows > 0 && region.numberOfColumns > 0) {
        List(region.numberOfRows) { rowIdx ->
            List(region.numberOfColumns) { colIdx ->
                Cell("cell_${region.firstRow + rowIdx}_${region.firstColumn + colIdx}")
            }
        }
    } else null

    val rowHeaders = if (region.numberOfRows > 0) {
        List(region.numberOfRows) { rowIdx ->
            HeaderLabel("row_${region.firstRow + rowIdx}")
        }
    } else null

    return ChunkData(cells = cells, rowHeaders = rowHeaders)
}