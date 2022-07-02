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
import cms.rendner.intellij.dataframe.viewer.models.LeveledHeaderLabel
import cms.rendner.intellij.dataframe.viewer.python.bridge.HTMLPropsTable
import cms.rendner.intellij.dataframe.viewer.python.bridge.RowElementKind
import cms.rendner.intellij.dataframe.viewer.python.bridge.RowElementType
import com.intellij.util.SmartList

class RowHeaderLabelsExtractor {

    companion object {
        val EMPTY_HEADER_LABEL = HeaderLabel()
    }

    fun extract(table: HTMLPropsTable): List<IHeaderLabel> {
        // the cache is used to reduce the amount of used lists
        val levelsCache = mutableMapOf<List<String>, List<String>>()
        return SmartList(table.body.map { row ->
            val headers = mutableListOf<String>()
            for (element in row) {
                if (element.type != RowElementType.TH) break
                if (element.kind == RowElementKind.ROW_HEADING) {
                    headers.add(element.displayValue)
                }
            }
            convertToHeaderLabel(headers, levelsCache)
        })
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
}