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
import org.jsoup.nodes.Element
import org.jsoup.select.Evaluator
import org.jsoup.select.QueryParser
import org.w3c.css.sac.Selector

interface IStyleComputer {
    fun computeStyle(element: Element): StyleProperties
}

data class StyleDeclarationBlock(
    val textColor: StyleDeclaration? = null,
    val backgroundColor: StyleDeclaration? = null,
    val textAlign: StyleDeclaration? = null
)

data class StyleDeclaration(
    val value: String,
    val important: Boolean = false
)

data class MutableStyleDeclarationBlock(
    val textColor: MutableStyleDeclaration = MutableStyleDeclaration(),
    val backgroundColor: MutableStyleDeclaration = MutableStyleDeclaration(),
    val textAlign: MutableStyleDeclaration = MutableStyleDeclaration()
) {
    fun merge(style: StyleDeclarationBlock) {
        textColor.merge(style.textColor)
        backgroundColor.merge(style.backgroundColor)
        textAlign.merge(style.textAlign)
    }
}

data class MutableStyleDeclaration(
    var value: String? = null,
    var important: Boolean = false
) {

    fun merge(source: StyleDeclaration?) {
        source?.let {
            merge(it.value, it.important)
        }
    }

    private fun merge(newValue: String, valueIsImportant: Boolean) {
        if (valueIsImportant || !important) {
            value = newValue
            important = valueIsImportant
        }
    }
}

data class ParsedRuleset(
    val declarationBlock: StyleDeclarationBlock,
    val ordinalIndex: Int,
    private val selectors: GroupedSelectors,
) {
    /**
     * Returns all selectors of the rule which match the element.
     *
     * @param root    Root of the matching subtree
     * @param element tested element
     * @return Returns a list of matching selectors.
     */
    fun getMatchingSelectors(root: Element, element: Element): List<ParsedSelector> =
        selectors.getMatchingSelectors(root, element)
}

data class GroupedSelectors(
    private val simpleIdSelectors: Map<String, ParsedSelector>,
    private val simpleClassSelectors: Map<String, ParsedSelector>,
    private val otherSelectors: List<ParsedSelector>,
) {
    /**
     * Returns all selectors of the rule which match the element.
     *
     * @param root    Root of the matching subtree
     * @param element tested element
     * @return Returns a list of matching selectors.
     */
    fun getMatchingSelectors(root: Element, element: Element): List<ParsedSelector> {
        val idName = element.id()
        val classNames = element.classNames()
        val matchingSelectors = mutableListOf<ParsedSelector>()

        simpleIdSelectors[idName]?.let {
            matchingSelectors.add(it)
        }
        if (simpleClassSelectors.isNotEmpty() && classNames.isNotEmpty()) {
            classNames.forEach { className ->
                simpleClassSelectors[className]?.let {
                    matchingSelectors.add(it)
                }
            }
        }
        otherSelectors.forEach {
            if (it.matches(root, element)) {
                matchingSelectors.add(it)
            }
        }
        return matchingSelectors
    }
}

data class ParsedSelector(val selector: Selector, val specificity: Specificity) {
    private val evaluator: Evaluator by lazy { QueryParser.parse(selector.toString()) }

    /**
     * Test if the element meets the evaluator's requirements.
     *
     * @param root    Root of the matching subtree
     * @param element tested element
     * @return Returns true if the requirements are met or false otherwise
     */
    fun matches(root: Element, element: Element): Boolean = evaluator.matches(root, element)
}

data class Specificity(val a: Int = 0, val b: Int = 0, val c: Int = 0) : Comparable<Specificity> {
    // ascending: low to high
    override fun compareTo(other: Specificity): Int {
        val a = a - other.a
        if (a == 0) {
            val b = b - other.b
            if (b == 0) {
                return this.c - other.c
            }
            return b
        }
        return a
    }
}