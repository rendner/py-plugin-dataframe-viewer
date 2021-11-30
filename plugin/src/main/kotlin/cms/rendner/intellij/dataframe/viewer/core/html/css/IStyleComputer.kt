package cms.rendner.intellij.dataframe.viewer.core.html.css

import cms.rendner.intellij.dataframe.viewer.core.component.models.StyleProperties
import org.jsoup.nodes.Element

interface IStyleComputer {
    fun computeStyle(element: Element): StyleProperties
}