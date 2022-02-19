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
package cms.rendner.intellij.dataframe.viewer.models.chunked.converter.css

import cms.rendner.intellij.dataframe.viewer.models.StyleProperties
import cms.rendner.intellij.dataframe.viewer.models.chunked.converter.html.extensions.toStyleDeclarationBlock
import com.steadystate.css.parser.CSSOMParser
import com.steadystate.css.parser.SACParserCSS3
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.w3c.css.sac.InputSource
import java.io.StringReader

class StyleComputer(
    private val document: Document
) : IStyleComputer {
    private val valueConverter = CSSValueConverter()
    private val parser = CSSOMParser(SACParserCSS3())
    private val rulesets: List<ParsedRuleset>

    init {
        @Suppress("UNNECESSARY_SAFE_CALL")
        rulesets = document.selectFirst("style")?.let {
            RulesetExtractor(parser).extract(it)
        } ?: emptyList()
    }

    // todo: we currently don't evaluate the whole parent chain (styles are inherited)
    override fun computeStyle(element: Element): StyleProperties {
        val matchingRulesets = getMatchingRulesets(element).let {
            if(it.size > 1) it.sortedWith(::sortMatchesBySpecificityComparator) else it
        }
        val declarationBlock = createMergedDeclarationBlock(element, matchingRulesets)

        return StyleProperties(
            valueConverter.convertColorValue(declarationBlock.textColor.value),
            valueConverter.convertColorValue(declarationBlock.backgroundColor.value),
            valueConverter.convertTextAlign(declarationBlock.textAlign.value)
        )
    }

    private fun createMergedDeclarationBlock(
        element: Element,
        rulesets: List<MatchingRuleset>
    ): MutableStyleDeclarationBlock {
        val result = MutableStyleDeclarationBlock()
        rulesets.forEach { result.merge(it.ruleset.declarationBlock) }

        val inlineStyle = element.attr("style")
        if (inlineStyle.isNotEmpty()) {
            val style = StringReader(inlineStyle).use {
                parser.parseStyleDeclaration(InputSource(it))
            }
            result.merge(style.toStyleDeclarationBlock())
        }

        return result
    }

    private fun getMatchingRulesets(element: Element): List<MatchingRuleset> {
        return rulesets.flatMap { ruleset ->
            ruleset.getMatchingSelectors(document, element).map { MatchingRuleset(it, ruleset) }
        }
    }

    private fun sortMatchesBySpecificityComparator(o1: MatchingRuleset, o2: MatchingRuleset): Int {
        val s = o1.matchingSelector.specificity.compareTo(o2.matchingSelector.specificity)
        return if (s == 0) o1.ruleset.ordinalIndex - o2.ruleset.ordinalIndex else s
    }
}

private data class MatchingRuleset(
    val matchingSelector: ParsedSelector,
    val ruleset: ParsedRuleset,
)