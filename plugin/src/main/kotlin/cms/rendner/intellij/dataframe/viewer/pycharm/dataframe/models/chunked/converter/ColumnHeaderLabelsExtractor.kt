package cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.converter

import cms.rendner.intellij.dataframe.viewer.core.component.models.HeaderLabel
import cms.rendner.intellij.dataframe.viewer.core.component.models.IHeaderLabel
import cms.rendner.intellij.dataframe.viewer.core.component.models.LegendHeaders
import cms.rendner.intellij.dataframe.viewer.core.component.models.LeveledHeaderLabel
import cms.rendner.intellij.dataframe.viewer.core.html.TableHeaderRow
import com.intellij.util.SmartList
import kotlin.math.max

class ColumnHeaderLabelsExtractor {

    data class Result(val legend: LegendHeaders, val columns: List<IHeaderLabel>)

    companion object {
        val EMPTY_RESULT = Result(LegendHeaders(), emptyList())
    }

    fun extract(headerRows: List<TableHeaderRow>): Result {
        val (rowHeadersRow, columnHeadersRow) = headerRows.partition { isIndexColumnRow(it) }

        val columnHeaderLabels = extractHeaderLabels(columnHeadersRow)
        val rowHeaderLabels = extractHeaderLabels(rowHeadersRow)

        return Result(
            LegendHeaders(
                rowHeaderLabels.indexColumn,
                columnHeaderLabels.indexColumn,
            ),
            columnHeaderLabels.columns
        )
    }

    private data class HeaderLabels(val indexColumn: IHeaderLabel?, val columns: List<IHeaderLabel>)

    private fun extractHeaderLabels(rows: List<TableHeaderRow>): HeaderLabels {
        val columns = mutableListOf<MutableList<String>>()
        val indexColumn = mutableListOf<String>()
        rows.forEach { row ->
            var columnIndex = 0
            row.headers.forEach { header ->
                val classNames = header.classNames()
                if (classNames.contains(HeaderCssClasses.INDEX_NAME_CLASS.value)) {
                    indexColumn.add(header.text().ifEmpty { "level_${indexColumn.size}" })
                } else if (classNames.contains(HeaderCssClasses.COL_HEADING_CLASS.value)) {
                    val text = header.text()
                    val colSpan = max(1, header.attr("colSpan").toIntOrNull() ?: 0)
                    for (spanIndex in 0 until colSpan) {
                        (columnIndex + spanIndex).let {
                            while (columns.size <= it) {
                                columns.add(mutableListOf())
                            }
                            columns[it].add(text)
                        }
                    }
                    columnIndex += colSpan
                }
            }
        }

        // the cache is used to reduce the amount of used lists
        val levelsCache = mutableMapOf<List<String>, List<String>>()

        return HeaderLabels(
            if (indexColumn.isEmpty()) null else convertToHeaderLabel(indexColumn, levelsCache),
            columns.map { convertToHeaderLabel(it, levelsCache) }
        )
    }

    private fun convertToHeaderLabel(
        labels: List<String>,
        levelsCache: MutableMap<List<String>, List<String>>,
    ): IHeaderLabel {
        return when {
            labels.isEmpty() -> {
                HeaderLabel()
            }
            labels.size == 1 -> {
                HeaderLabel(labels.first())
            }
            else -> {
                val labelsExcludingLast = labels.subList(0, labels.size - 1)
                var cachedLevels = levelsCache[labelsExcludingLast]
                if (cachedLevels == null) {
                    cachedLevels = SmartList(labelsExcludingLast)
                    levelsCache[labelsExcludingLast] = cachedLevels
                }
                LeveledHeaderLabel(labels.last(), cachedLevels)
            }
        }
    }

    // use it to separate rows in a map
    private fun isIndexColumnRow(row: TableHeaderRow): Boolean {
        var hasIndexNameClass = false
        var hasColHeadingClass = false
        var startsWithBlankClass = false
        row.headers.forEachIndexed { index, element ->
            val classes = element.classNames()
            if (index == 0) {
                startsWithBlankClass = classes.contains(HeaderCssClasses.BLANK_CLASS.value)
            }
            if (!hasIndexNameClass) {
                hasIndexNameClass = classes.contains(HeaderCssClasses.INDEX_NAME_CLASS.value)
            }
            if (!hasColHeadingClass) {
                hasColHeadingClass = classes.contains(HeaderCssClasses.COL_HEADING_CLASS.value)
            }
        }

        return !startsWithBlankClass && !hasColHeadingClass && hasIndexNameClass
    }
}