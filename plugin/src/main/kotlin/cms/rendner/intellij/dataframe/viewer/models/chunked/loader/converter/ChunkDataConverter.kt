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
        fun convert(bridgeChunkData: BridgeChunkData): ChunkData {
            return ChunkData(
                values = if (bridgeChunkData.cells != null) toChunkValues(bridgeChunkData.cells) else null,
                rowHeaderLabels = bridgeChunkData.rowHeaders?.let { convertRowHeaderLabels(it) },
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
            return ChunkValues(SmartList(cells.map { rowOfCells ->  ChunkValuesRow(SmartList(rowOfCells))}))
        }

        private fun convertRowHeaderLabels(labels: List<List<String>>): List<IHeaderLabel> {
            return SmartList(labels.map {  convertHeaderLabel(it) })
        }
    }
}