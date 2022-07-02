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

import cms.rendner.intellij.dataframe.viewer.models.*
import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkData
import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkHeaderLabels
import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkValues
import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkValuesRow
import cms.rendner.intellij.dataframe.viewer.models.chunked.converter.css.CSSValueConverter
import cms.rendner.intellij.dataframe.viewer.python.bridge.HTMLPropsTable
import cms.rendner.intellij.dataframe.viewer.python.bridge.RowElementType
import com.intellij.util.SmartList

class HTMLPropsChunkConverter {

    private val valueConverter = CSSValueConverter()

    fun extractData(table: HTMLPropsTable, excludeRowHeader: Boolean, excludeColumnHeader: Boolean): ChunkData {
        return ChunkData(
            convertHeaderLabels(table, excludeRowHeader, excludeColumnHeader),
            convertValues(table),
        )
    }

    private fun convertHeaderLabels(table: HTMLPropsTable, excludeRowHeader: Boolean, excludeColumnHeader: Boolean): ChunkHeaderLabels {
        val columnHeaderLabels = if (excludeColumnHeader) {
            ColumnHeaderLabelsExtractor.EMPTY_RESULT
        } else ColumnHeaderLabelsExtractor().extract(table)

        val rowHeaderLabels = if (excludeRowHeader) {
            emptyList()
        } else RowHeaderLabelsExtractor().extract(table)

        return ChunkHeaderLabels(
            columnHeaderLabels.legend,
            columnHeaderLabels.columns,
            rowHeaderLabels,
        )
    }

    private fun convertValues(table: HTMLPropsTable): ChunkValues {
        val values = SmartList<ChunkValuesRow>()
        for (row in table.body) {
            val rowValues = SmartList<Value>()
            for (element in row) {
                if (element.type == RowElementType.TD) {
                    val styleProps = element.cssProps?.let{ getStyleProperties(it) }
                    if (styleProps == null) {
                        rowValues.add(StringValue(element.displayValue))
                    } else {
                        rowValues.add(StyledValue(element.displayValue, styleProps))
                    }
                }
            }
            rowValues.trimToSize()
            values.add(ChunkValuesRow(rowValues))
        }
        values.trimToSize()

        return ChunkValues(values)
    }

    private fun getStyleProperties(cssProps: Map<String, String>): StyleProperties? {
        val color = cssProps["color"]
        val backgroundColor = cssProps["background-color"]
        val textAlign = cssProps["text-align"]
        if (color == null && backgroundColor == null && textAlign == null) return null
        return StyleProperties(
            valueConverter.convertColorValue(color),
            valueConverter.convertColorValue(backgroundColor),
            valueConverter.convertTextAlign(textAlign)
        )
    }
}