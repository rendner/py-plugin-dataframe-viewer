package cms.rendner.intellij.dataframe.viewer.core.html

import org.jsoup.nodes.Element

/**
 * Structure of an HTML tbody-row.
 *
 * @param element the row element
 * @param headers all th-elements of [element]
 * @param data all td-elements of [element]
 */
data class TableBodyRow(
    val element: Element,
    val headers: List<Element> = emptyList(),
    val data: List<Element> = emptyList()
)

/**
 * Structure of an HTML thead-row.
 *
 * @param element the row element
 * @param headers all th-elements of [element]
 */
data class TableHeaderRow(
    val element: Element,
    val headers: List<Element> = emptyList()
)