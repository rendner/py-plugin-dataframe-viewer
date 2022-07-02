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
package cms.rendner.intellij.dataframe.viewer.models.chunked.converter.htmlprops

import cms.rendner.intellij.dataframe.viewer.models.HeaderLabel
import cms.rendner.intellij.dataframe.viewer.models.IHeaderLabel
import cms.rendner.intellij.dataframe.viewer.models.LegendHeaders
import cms.rendner.intellij.dataframe.viewer.models.LeveledHeaderLabel
import cms.rendner.intellij.dataframe.viewer.python.bridge.HTMLPropsRowElement
import cms.rendner.intellij.dataframe.viewer.python.bridge.HTMLPropsTable
import cms.rendner.intellij.dataframe.viewer.python.bridge.RowElementKind
import com.intellij.util.SmartList

class ColumnHeaderLabelsExtractor {

    data class Result(val legend: LegendHeaders, val columns: List<IHeaderLabel>)

    companion object {
        val EMPTY_RESULT = Result(LegendHeaders(), emptyList())
        val EMPTY_HEADER_LABEL = HeaderLabel()
    }

    fun extract(table: HTMLPropsTable): Result {
        val rowCount = table.head.size
        if (rowCount == 0) return EMPTY_RESULT

        var rowHeaderLabels: HeaderLabels? = null
        val columnHeaderLabels: HeaderLabels?
        if (isIndexColumnRow(table.head.last())) {
                rowHeaderLabels = extractHeaderLabels(listOf(table.head.last()))
                columnHeaderLabels = extractHeaderLabels(table.head.subList(0, rowCount - 1))
        } else {
            columnHeaderLabels = extractHeaderLabels(table.head)
        }

        return Result(
            LegendHeaders(
                rowHeaderLabels?.indexColumn,
                columnHeaderLabels.indexColumn,
            ),
            SmartList(columnHeaderLabels.columns)
        )
    }

    private data class HeaderLabels(val indexColumn: IHeaderLabel?, val columns: List<IHeaderLabel>)

    private fun extractHeaderLabels(rows: List<List<HTMLPropsRowElement>>): HeaderLabels {
        val columns = mutableListOf<MutableList<String>>()
        val indexColumn = mutableListOf<String>()
        rows.forEach { row ->
            var headingColInRowIndex = 0
            for (element in row) {
                if (element.kind == RowElementKind.INDEX_NAME) {
                    indexColumn.add(element.displayValue.ifEmpty { "level_${indexColumn.size}" })
                } else if (element.kind == RowElementKind.COL_HEADING) {
                    if (headingColInRowIndex == columns.size) {
                        columns.add(mutableListOf())
                    }
                    columns[headingColInRowIndex].add(element.displayValue)
                    headingColInRowIndex++
                }
            }
        }

        // the cache is used to reduce the amount of used lists
        val levelsCache = mutableMapOf<List<String>, List<String>>()

        return HeaderLabels(
            if (indexColumn.isEmpty()) null else convertToHeaderLabel(indexColumn, levelsCache),
            SmartList(columns.map { convertToHeaderLabel(it, levelsCache) })
        )
    }

    private fun convertToHeaderLabel(
        labels: List<String>,
        levelsCache: MutableMap<List<String>, List<String>>,
    ): IHeaderLabel {
        return when {
            labels.isEmpty() -> EMPTY_HEADER_LABEL
            labels.size == 1 -> HeaderLabel(labels.first())
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

    private fun isIndexColumnRow(row: List<HTMLPropsRowElement>): Boolean {
        var hasIndexNameColumn = false
        var hasColHeadingColumn = false
        var startsWithBlankColumn = false

        row.forEachIndexed { index, element ->
            if (index == 0) {
                startsWithBlankColumn = element.kind == RowElementKind.BLANK
            }
            if (!hasIndexNameColumn) {
                hasIndexNameColumn = element.kind == RowElementKind.INDEX_NAME
            }
            if (!hasColHeadingColumn) {
                hasColHeadingColumn = element.kind == RowElementKind.COL_HEADING
            }
        }

        return !startsWithBlankColumn && !hasColHeadingColumn && hasIndexNameColumn
    }
}