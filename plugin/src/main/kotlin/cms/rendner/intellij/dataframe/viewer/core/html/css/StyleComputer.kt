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
package cms.rendner.intellij.dataframe.viewer.core.html.css

import cms.rendner.intellij.dataframe.viewer.core.component.models.StyleProperties
import cms.rendner.intellij.dataframe.viewer.core.html.extensions.toStyleDeclarationBlock
import com.steadystate.css.parser.CSSOMParser
import org.jsoup.nodes.Element
import org.jsoup.select.QueryParser
import org.w3c.css.sac.InputSource
import org.w3c.css.sac.Selector
import java.io.StringReader

// not thread safe - we don't synchronize on parser to compute styles as fast as possible
class StyleComputer(
    private val parser: CSSOMParser,
    private val rules: List<RuleSet>,
    private val root: Element
) : IStyleComputer {
    private val valueConverter = CSSValueConverter()

    // todo: we currently don't evaluate the whole parent chain (styles are inherited)
    override fun computeStyle(element: Element): StyleProperties {
        val idName = element.id()
        val classNames = element.classNames()
        val matchingSelectors = mutableSetOf<SelectorMatch>()
        rules.forEach { rule ->
            rule.selectors.simpleIdSelectors[idName]?.let {
                matchingSelectors.add(SelectorMatch(it, rule))
            }
            if (rule.selectors.simpleClassSelectors.isNotEmpty() && classNames.isNotEmpty()) {
                val className = classNames.firstOrNull { rule.selectors.simpleClassSelectors.containsKey(it) }
                className?.let {
                    matchingSelectors.add(SelectorMatch(rule.selectors.simpleClassSelectors[it]!!, rule))
                }
            }
            if (rule.selectors.otherSelectors.isNotEmpty()) {
                rule.selectors.otherSelectors.forEach {
                    val evaluator = QueryParser.parse(it.toString())
                    if (evaluator.matches(root, element)) {
                        matchingSelectors.add(SelectorMatch(it, rule))
                    }
                }
            }
        }

        val sortedDeclarationBlocks = if (matchingSelectors.size > 1) {
            val specificityCalculator = SpecificityCalculator(parser)
            sortBySpecificity(matchingSelectors, specificityCalculator)
        } else {
            matchingSelectors.map { it.ruleSet.declarationBlock }
        }

        val declarationBlock = MutableStyleDeclarationBlock()
        sortedDeclarationBlocks.forEach { declarationBlock.merge(it) }

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

    // todo make this code accessible for unit tests
    // -> test: Equal specificity: the latest rule counts (https://www.w3schools.com/css/css_specificity.asp)
    private fun sortBySpecificity(
        matchingSelectors: Set<SelectorMatch>,
        specificityCalculator: SpecificityCalculator
    ): List<StyleDeclarationBlock> {
        return matchingSelectors
            .map {
                SelectorMatchWithSpecificity(it, specificityCalculator.calculate(it.selector))
            }
            .sortedWith { o1, o2 ->
                val s = o1.specificity.compareTo(o2.specificity)
                if (s == 0) o1.match.ruleSet.ordinalIndex - o2.match.ruleSet.ordinalIndex else s
            }.map { it.match.ruleSet.declarationBlock }
    }

    private data class SelectorMatchWithSpecificity(
        val match: SelectorMatch,
        val specificity: Specificity
    )

    private data class SelectorMatch(
        val selector: Selector,
        val ruleSet: RuleSet
    )
}