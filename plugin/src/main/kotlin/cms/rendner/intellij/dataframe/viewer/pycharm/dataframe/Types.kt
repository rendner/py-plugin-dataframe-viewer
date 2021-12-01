/*
 * Copyright 2021 cms.rendner (Daniel Schmidt)
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
package cms.rendner.intellij.dataframe.viewer.pycharm.dataframe

data class ChunkSize(val rows: Int, val columns: Int)

/**
 * @param rowsCount number of rows
 * @param columnsCount number of columns
 * @param visibleRowsCount number of visible rows
 * @param visibleColumnsCount number of visible columns
 * @param rowLevelsCount number of headers which build the label/index of a row.
 * In most cases 1, if a multi-index is used the value can be >= 1.
 * @param columnLevelsCount number of headers which build the label of a column.
 * In most cases 1, if a multi-index is used the value can be >= 1.
 * @param hideRowHeader is true when no row-header should be displayed
 * @param hideColumnHeader is true when no column-header should be displayed
 */
data class TableStructure(
    val rowsCount: Int,
    val columnsCount: Int,
    val visibleRowsCount: Int,
    val visibleColumnsCount: Int,
    val rowLevelsCount: Int,
    val columnLevelsCount: Int,
    val hideRowHeader: Boolean,
    val hideColumnHeader: Boolean
)