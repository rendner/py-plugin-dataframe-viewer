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
package cms.rendner.intellij.dataframe.viewer.models.chunked.converter.html

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.select.NodeFilter
import org.jsoup.select.NodeTraversor

/**
 * Isn't thread-safe, should be used by one single thread.
 */
class TableRowsProvider(table: Element) {

    private val filter: RowsParentFilter = RowsParentFilter()
    val bodyRows: List<TableBodyRow> by lazy { extractBodyRows() }
    val headerRows: List<TableHeaderRow> by lazy { extractHeaderRows() }

    init {
        NodeTraversor.filter(filter, table)
    }

    private fun extractBodyRows(): List<TableBodyRow> {
        return filter.tbodyElement?.let {
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

    private fun extractHeaderRows(): List<TableHeaderRow> {
        return filter.theadElement?.let {
            it.children().map { row ->
                val childrenByTag = row.children().groupBy { child -> child.tagName() }
                TableHeaderRow(row, childrenByTag.getOrDefault("th", emptyList()))
            }
        } ?: emptyList()
    }
}

/**
 * Fast node filter to search for the tbody and thead of a table.
 *
 * This implementation is slightly faster than a default NodeFilter because it reduces the search space (nodes to check).
 * In case of a table it doesn't make sense to search inside a thead or tbody for the other element.
 */
private class RowsParentFilter: NodeFilter {

    var theadElement: Element? = null
        private set

    var tbodyElement: Element? = null
        private set

    override fun head(node: Node, depth: Int): NodeFilter.FilterResult {
        if (node is Element) {
            return when (node.tagName()) {
                "table" -> NodeFilter.FilterResult.CONTINUE
                "thead" -> {
                    theadElement = node
                    NodeFilter.FilterResult.SKIP_CHILDREN
                }
                "tbody" -> {
                    tbodyElement = node
                    NodeFilter.FilterResult.STOP
                }
                // This case should not happen - Jsoup wraps tr-elements with a
                // tbody-element during parsing if no tbody-element is present.
                "tr" -> {
                    tbodyElement = node.parent()
                    NodeFilter.FilterResult.STOP
                }
                else -> NodeFilter.FilterResult.SKIP_ENTIRELY
            }
        }

        return NodeFilter.FilterResult.SKIP_ENTIRELY
    }

    override fun tail(node: Node, depth: Int): NodeFilter.FilterResult {
        return if (tbodyElement != null) NodeFilter.FilterResult.STOP else NodeFilter.FilterResult.CONTINUE
    }
}