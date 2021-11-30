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