/*
 * Copyright 2021-2025 cms.rendner (Daniel Schmidt)
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
package cms.rendner.intellij.dataframe.viewer.models.chunked.loader.converter

import cms.rendner.intellij.dataframe.viewer.models.*
import cms.rendner.intellij.dataframe.viewer.models.chunked.*
import cms.rendner.intellij.dataframe.viewer.python.bridge.ChunkData as BridgeChunkData
import cms.rendner.intellij.dataframe.viewer.python.bridge.Cell as BridgeCell
import com.intellij.util.SmartList

class ChunkDataConverter {
    companion object {
        private val valueConverter = CSSValueConverter()

        fun convert(bridgeChunkData: BridgeChunkData, withRowHeaders: Boolean): ChunkData {
            return ChunkData(
                toChunkValues(bridgeChunkData.cells),
                if (withRowHeaders) bridgeChunkData.indexLabels.let { if (it == null) null else convertRowHeaderLabels(it) } else null,
            )
        }

        fun convertHeaderLabel(label: List<String>?): IHeaderLabel {
            return when(label?.size) {
                0, null -> HeaderLabel()
                1 -> HeaderLabel(label[0])
                else -> LeveledHeaderLabel(label.last(), SmartList(label.subList(0, label.size - 1)))
            }
        }

        private fun toChunkValues(cells: List<List<BridgeCell>>): IChunkValues {
            val values = SmartList<ChunkValuesRow>()
            for (row in cells) {
                val rowValues = SmartList<Value>()
                for (element in row) {
                    val styleProps = element.css?.let{ getStyleProperties(it) }
                    if (styleProps == null) {
                        rowValues.add(StringValue(element.value))
                    } else {
                        rowValues.add(StyledValue(element.value, styleProps))
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

        private fun convertRowHeaderLabels(labels: List<List<String>>): List<IHeaderLabel> {
            return SmartList(labels.map {  convertHeaderLabel(it) })
        }
    }
}