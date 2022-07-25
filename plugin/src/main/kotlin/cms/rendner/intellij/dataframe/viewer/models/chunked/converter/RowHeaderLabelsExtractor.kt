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

import cms.rendner.intellij.dataframe.viewer.models.HeaderLabel
import cms.rendner.intellij.dataframe.viewer.models.IHeaderLabel
import cms.rendner.intellij.dataframe.viewer.models.LeveledHeaderLabel
import cms.rendner.intellij.dataframe.viewer.models.chunked.converter.html.TableBodyRow
import com.intellij.util.SmartList

class RowHeaderLabelsExtractor {

    private var openParents: MutableList<SpannedHeader> = ArrayList()
    private var parentNames: List<String> = emptyList()
    private var shouldRebuildParentNames = false

    fun extract(bodyRows: List<TableBodyRow>): List<IHeaderLabel> {
        // the cache is used to reduce the amount of used lists
        val levelsCache = mutableMapOf<List<String>, List<String>>()
        return SmartList(bodyRows.mapNotNull { row ->
            val headerRows = row.headers.filter { header -> header.hasClass(HeaderCssClasses.ROW_HEADING_CLASS) }
            when (headerRows.isEmpty()) {
                true -> null
                false -> {
                    val lastHeader = headerRows.lastOrNull()
                    headerRows.forEach { header ->
                        if (header != lastHeader) {
                            addParent(
                                header.text(),
                                header.attr("rowSpan").toIntOrNull() ?: 0
                            )
                        }
                    }
                    convertToHeaderLabel(lastHeader?.text() ?: "", levelsCache)
                }
            }
        })
    }

    private fun addParent(name: String, rowSpan: Int) {
        shouldRebuildParentNames = true
        openParents.add(SpannedHeader(name, rowSpan))
    }

    private fun convertToHeaderLabel(name: String, levelsCache: MutableMap<List<String>, List<String>>): IHeaderLabel {
        if (openParents.isEmpty()) return HeaderLabel(name)

        if (shouldRebuildParentNames) {
            shouldRebuildParentNames = false
            parentNames = SmartList(openParents.map { it.name })

            if (levelsCache.containsKey(parentNames)) {
                parentNames = levelsCache.getValue(parentNames)
            } else {
                levelsCache[parentNames] = parentNames
            }
        }

        val result = LeveledHeaderLabel(name, parentNames)
        decrementSpanOfOpenLevels()
        return result
    }

    private fun decrementSpanOfOpenLevels() {
        openParents.forEach { it.consume() }
        if (openParents.removeAll { !it.hasNext() }) {
            shouldRebuildParentNames = true
        }
    }

    data class SpannedHeader(val name: String, private var remainingSpanCount: Int) {
        fun consume() {
            remainingSpanCount--
        }

        fun hasNext() = remainingSpanCount > 0
    }
}