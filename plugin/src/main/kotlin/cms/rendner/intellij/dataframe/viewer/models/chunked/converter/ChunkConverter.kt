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
package cms.rendner.intellij.dataframe.viewer.models.chunked.converter

import cms.rendner.intellij.dataframe.viewer.models.StringValue
import cms.rendner.intellij.dataframe.viewer.models.StyledValue
import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkData
import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkHeaderLabels
import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkValues
import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkValuesRow
import cms.rendner.intellij.dataframe.viewer.models.chunked.converter.css.IStyleComputer
import cms.rendner.intellij.dataframe.viewer.models.chunked.converter.html.TableBodyRow
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.SmartList
import org.jsoup.nodes.Document

/**
 * The converter uses [SmartList] where possible to reduce the memory footprint.
 */
open class ChunkConverter(document: Document): AbstractChunkConverter(document) {

    companion object {
        private val logger = Logger.getInstance(ChunkConverter::class.java)
    }

    private val tableRowsProvider = createTableRowsProvider()

    override fun extractData(excludeRowHeader: Boolean, excludeColumnHeader: Boolean): ChunkData {
        val columnHeaderLabels = if (excludeColumnHeader) {
            ColumnHeaderLabelsExtractor.EMPTY_RESULT
        } else ColumnHeaderLabelsExtractor().extract(tableRowsProvider.headerRows)

        val rowHeaderLabels = if (excludeRowHeader) {
            emptyList()
        } else RowHeaderLabelsExtractor().extract(tableRowsProvider.bodyRows)

        return ChunkData(
            ChunkHeaderLabels(
                columnHeaderLabels.legend,
                columnHeaderLabels.columns,
                rowHeaderLabels
            ),
            ChunkValues(
                SmartList(
                    tableRowsProvider.bodyRows.map { row -> ChunkValuesRow(row.data.map { StringValue(it.text()) }) }
                )
            )
        )
    }

    override fun mergeWithStyles(values: ChunkValues): ChunkValues {
        val styleComputer = createTableStyleComputer()
        return ChunkValues(
            SmartList(tableRowsProvider.bodyRows.zip(values.rows) { tableRow, unstyledRow ->
                createStyledRow(tableRow, unstyledRow, styleComputer)
            })
        )
    }

    private fun createStyledRow(
        bodyRow: TableBodyRow,
        unstyledRow: ChunkValuesRow,
        styleComputer: IStyleComputer
    ): ChunkValuesRow {
        return ChunkValuesRow(
            SmartList(bodyRow.data.zip(unstyledRow.values) { element, unstyledValue ->
                try {
                    styleComputer.computeStyle(element).let {
                        if (it.isEmpty()) unstyledValue
                        else StyledValue(unstyledValue.text(), it)
                    }
                } catch (throwable: Throwable) {
                    logger.error("Styles couldn't be computed.", throwable)
                    unstyledValue
                }
            })
        )
    }
}