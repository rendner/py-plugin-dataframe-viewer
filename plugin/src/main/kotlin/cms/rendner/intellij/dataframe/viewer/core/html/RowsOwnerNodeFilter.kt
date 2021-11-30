package cms.rendner.intellij.dataframe.viewer.core.html

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.select.NodeFilter

/**
 * Fast node filter which searches for the tbody and thead of a table.
 *
 * This implementation is faster than a default NodeFilter because it reduces the search space (nodes to check).
 * In case of a table it doesn't make sense to search inside a thead or tbody. And the search can be aborted
 * after the two elements were found.
 */
class RowsOwnerNodeFilter: NodeFilter {

    var headerRowsOwner:Element? = null
        private set

    var bodyRowsOwner:Element? = null
        private set

    override fun head(node: Node?, depth: Int): NodeFilter.FilterResult {
        if (node is Element) {
            return when (node.tagName()) {
                "table" -> NodeFilter.FilterResult.CONTINUE
                "thead" -> {
                    headerRowsOwner = node
                    NodeFilter.FilterResult.SKIP_CHILDREN
                }
                "tbody" -> {
                    bodyRowsOwner = node
                    NodeFilter.FilterResult.STOP
                }
                // This case should not happen - Jsoup wraps tr-elements with a
                // tbody-element during parsing if no tbody-element is present.
                "tr" -> {
                    bodyRowsOwner = node.parent()
                    NodeFilter.FilterResult.STOP
                }
                else -> NodeFilter.FilterResult.SKIP_ENTIRELY
            }
        }

        return NodeFilter.FilterResult.SKIP_ENTIRELY
    }

    override fun tail(node: Node?, depth: Int): NodeFilter.FilterResult {
        return if(bodyRowsOwner != null) NodeFilter.FilterResult.STOP else NodeFilter.FilterResult.CONTINUE
    }
}