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
package cms.rendner.intellij.dataframe.viewer.core.html

import org.jsoup.nodes.Element

/**
 * Isn't thread-safe, should be used by one single thread.
 */
class HtmlTableElementProvider(
    private val headerRowsOwner: Element? = null,
    private val bodyRowsOwner: Element? = null
) {
    /**
     * Read all rows from the [bodyRowsOwner].
     */
    fun bodyRows(): List<TableBodyRow> {
        return bodyRowsOwner?.let {
            it.children().mapNotNull { row ->
                val childrenByTag = row.children().groupBy { child -> child.tagName() }
                if(childrenByTag.isEmpty()) {
                    // do nothing to remove empty rows
                    /*
                    When "styler.hide_index(subset=...)" and "styler.render()" was used, each hidden row is presented
                    as an empty row ("<tr>\n</tr>") in the returned HTML.

                    This isn't the case for the HTML returned by "render_chunk" to limit the amount of generated data
                    these rows are not included in the chunks. Because in the worst case between two visible rows
                    there could be hundreds or thousands of empty rows.

                    The hidden empty rows in the unchunked HTML are required, even if they don't contain additional data.
                    Because omitting them, would break the "nth-child" css-selectors. These selectors are currently
                    not supported for chunked HTML parts. Supporting this would need additional tweaks, because
                    the naive approach
                        - include all rows of a DataFrame when evaluating the final css for each table element -
                        would be very slow when having all rows of a huge DataFrame in memory.
                     */
                    null
                } else {
                    TableBodyRow(
                        row,
                        childrenByTag.getOrDefault("th", emptyList()),
                        childrenByTag.getOrDefault("td", emptyList())
                    )
                }
            }
        } ?: emptyList()
    }

    /**
     * Read all rows from the [headerRowsOwner].
     *
     * Most tables have one single row, but HTML-table can have multiple rows in a <thead>-element.
     *
     * @return list of all rows.
     */
    fun headerRows(): List<TableHeaderRow> {
        return headerRowsOwner?.let {
            it.children().map { row ->
                val childrenByTag = row.children().groupBy { child -> child.tagName() }
                TableHeaderRow(row, childrenByTag.getOrDefault("th", emptyList()))
            }
        } ?: emptyList()
    }
}