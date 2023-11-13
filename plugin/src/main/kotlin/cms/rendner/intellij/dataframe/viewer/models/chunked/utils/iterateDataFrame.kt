/*
 * Copyright 2021-2023 cms.rendner (Daniel Schmidt)
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
package cms.rendner.intellij.dataframe.viewer.models.chunked.utils

import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkRegion
import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkSize

/**
 * Yields chunk regions to the [Iterator] being built and suspends until the next chunk region is requested.
 * Guarantees that the returned chunk region is always in bounds of iterated DataFrame.
 *
 * @param rowsInFrame number of rows in the DataFrame to iterate, >= 0
 * @param columnsInFrame number of columns in the DataFrame to iterate, >= 0
 * @param chunkSize the requested size of the chunks.
 * It is allowed to specify a size which is out of bounds, the produced regions are clamped to the bounds of the DataFrame
 * specified by [rowsInFrame] and [columnsInFrame].
 */
fun iterateDataFrame(rowsInFrame: Int, columnsInFrame: Int, chunkSize: ChunkSize) = sequence {
    if (rowsInFrame < 0) throw IllegalArgumentException("Parameter 'rowsInFrame' is < 0.")
    if (columnsInFrame < 0) throw IllegalArgumentException("Parameter 'columnsInFrame' is < 0.")
    if (chunkSize.rows <= 0) throw IllegalArgumentException("Parameter 'chunkSize.rows' is <= 0.")
    if (chunkSize.columns <= 0) throw IllegalArgumentException("Parameter 'chunkSize.columns' is <= 0.")
    var rowsProcessed = 0
    while (rowsProcessed < rowsInFrame) {
        var colsInRowProcessed = 0
        val rows = Integer.min(chunkSize.rows, rowsInFrame - rowsProcessed)
        while(colsInRowProcessed < columnsInFrame) {
            val cols = Integer.min(chunkSize.columns, columnsInFrame - colsInRowProcessed)
            yield(ChunkRegion(rowsProcessed, colsInRowProcessed, rows, cols))
            colsInRowProcessed += cols
        }
        rowsProcessed += rows
    }
}