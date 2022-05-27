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
package cms.rendner.intellij.dataframe.viewer.models.chunked.helper

import cms.rendner.intellij.dataframe.viewer.models.StyleProperties
import cms.rendner.intellij.dataframe.viewer.models.chunked.converter.ChunkConverter
import cms.rendner.intellij.dataframe.viewer.models.chunked.converter.css.CSSValueConverter
import cms.rendner.intellij.dataframe.viewer.models.chunked.converter.css.IStyleComputer
import cms.rendner.intellij.dataframe.viewer.models.chunked.converter.css.MutableStyleDeclarationBlock
import cms.rendner.intellij.dataframe.viewer.models.chunked.converter.html.extensions.toStyleDeclarationBlock
import com.steadystate.css.parser.CSSOMParser
import com.steadystate.css.parser.SACParserCSS3
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.w3c.css.sac.InputSource
import java.io.StringReader

class ComputedCSSChunkConverter(
    document: Document
) : ChunkConverter(document) {

    override fun createTableStyleComputer(): IStyleComputer {
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