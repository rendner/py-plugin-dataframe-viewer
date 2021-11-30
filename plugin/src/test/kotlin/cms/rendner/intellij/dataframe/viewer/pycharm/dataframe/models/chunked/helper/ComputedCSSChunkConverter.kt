package cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.helper

import cms.rendner.intellij.dataframe.viewer.core.component.models.StyleProperties
import cms.rendner.intellij.dataframe.viewer.core.html.css.CSSValueConverter
import cms.rendner.intellij.dataframe.viewer.core.html.css.IStyleComputer
import cms.rendner.intellij.dataframe.viewer.core.html.css.MutableStyleDeclarationBlock
import cms.rendner.intellij.dataframe.viewer.core.html.extensions.toStyleDeclarationBlock
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.converter.ChunkConverter
import com.steadystate.css.parser.CSSOMParser
import com.steadystate.css.parser.SACParserCSS3
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.w3c.css.sac.InputSource
import java.io.StringReader

class ComputedCSSChunkConverter(
    document: Document
) : ChunkConverter(document) {

    override fun createTableStyleComputer(document: Document): IStyleComputer {
        return ComputedCSSExtractor(CSSOMParser(SACParserCSS3()))
    }
}

private class ComputedCSSExtractor(
    private val parser: CSSOMParser,
) : IStyleComputer {
    private val valueConverter = CSSValueConverter()

    override fun computeStyle(element: Element): StyleProperties {
        val declarationBlock = MutableStyleDeclarationBlock()
        val inlineStyle = element.attr("style")
        if (inlineStyle.isNotEmpty()) {
            val style = StringReader(inlineStyle).use {
                parser.parseStyleDeclaration(InputSource(it))
            }
            declarationBlock.merge(style.toStyleDeclarationBlock())
        }

        return StyleProperties(
            valueConverter.convertColorValue(declarationBlock.textColor.value),
            valueConverter.convertColorValue(declarationBlock.backgroundColor.value),
            valueConverter.convertTextAlign(declarationBlock.textAlign.value)
        )
    }
}