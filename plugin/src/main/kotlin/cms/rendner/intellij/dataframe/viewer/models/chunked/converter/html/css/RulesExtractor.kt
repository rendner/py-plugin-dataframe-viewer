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
package cms.rendner.intellij.dataframe.viewer.models.chunked.converter.html.css

import cms.rendner.intellij.dataframe.viewer.models.chunked.converter.html.extensions.toStyleDeclarationBlock
import com.steadystate.css.dom.CSSRuleListImpl
import com.steadystate.css.dom.CSSStyleRuleImpl
import com.steadystate.css.parser.CSSOMParser
import com.steadystate.css.parser.SelectorListImpl
import org.jsoup.nodes.Element
import org.w3c.css.sac.*
import java.io.StringReader

class RulesExtractor(private val parser: CSSOMParser) {

    /*
    Multiple cells can have the same css-declaration-block:

    #T_ebb4f652_24b9_11eb_a304_39eee03b4a9crow2108_col0,
    #T_ebb4f652_24b9_11eb_a304_39eee03b4a9crow2211_col2,
    #T_ebb4f652_24b9_11eb_a304_39eee03b4a9crow2243_col2,
    #T_ebb4f652_24b9_11eb_a304_39eee03b4a9crow2328_col3,
    #T_ebb4f652_24b9_11eb_a304_39eee03b4a9crow2424_col2,{
        background-color:  #e8adb8;
        color:  #000000;
    }

     Or there could be one declaration block for each cell (worst case):

     #T_85619eec_056e_11eb_a3ff_080027818d95row0_col0 {
        color:  black;
    }    #T_85619eec_056e_11eb_a3ff_080027818d95row0_col1 {
        color:  black;
    }    #T_85619eec_056e_11eb_a3ff_080027818d95row0_col2 {
        color:  black;
    }    #T_85619eec_056e_11eb_a3ff_080027818d95row0_col3 {
        background-color:  #92d192;
        color:  #000000;
        color:  red;
    }

    cell ids have the pattern "T_<uuid>_row<num_row>_col<num_col>"
    pandas 1.1.3 contained a bug where the pattern wasn't respected
    at least in pandas 1.2.0 it is fixed: "#T_2a42e_row0_col4"

    The <uuid> can be specified by the user and therefore also an empty string.
     */

    fun extract(stylesElement: Element): List<RuleSet> {
        val uniqueDeclarationBlocks = HashSet<StyleDeclarationBlock>()
        val getCachedOneIfExist = { element: StyleDeclarationBlock ->
            val index = uniqueDeclarationBlocks.indexOf(element)
            if (index == -1) {
                uniqueDeclarationBlocks.add(element)
                element
            } else {
                uniqueDeclarationBlocks.elementAt(index)
            }
        }
        return getCSSStyleRules(stylesElement).mapIndexed { index, rule ->
            RuleSet(
                group((rule.selectors as SelectorListImpl).selectors),
                getCachedOneIfExist(rule.style.toStyleDeclarationBlock()),
                index
            )
        }
    }

    private fun group(selectors: List<Selector>): GroupedSelectors {
        val simpleIdSelectors = mutableMapOf<String, Selector>()
        val simpleClassSelectors = mutableMapOf<String, Selector>()
        val otherSelectors = mutableListOf<Selector>()
        selectors.forEach {
            var added = false
            if (it is ConditionalSelector) {
                val condition = it.condition
                if (condition is AttributeCondition) {
                    if (condition.conditionType == Condition.SAC_ID_CONDITION) {
                        simpleIdSelectors[condition.value] = it
                        added = true
                    } else if (condition.conditionType == Condition.SAC_CLASS_CONDITION) {
                        simpleClassSelectors[condition.value] = it
                        added = true
                    }
                }
            }
            if (!added) {
                otherSelectors.add(it)
            }
        }
        return GroupedSelectors(
            simpleIdSelectors,
            simpleClassSelectors,
            otherSelectors
        )
    }

    private fun getCSSStyleRules(stylesElement: Element): List<CSSStyleRuleImpl> {
        val cssRuleList = StringReader(stylesElement.data()).use {
            synchronized(parser) { parser.parseStyleSheet(InputSource(it), null, null).cssRules }
        }
        return (cssRuleList as CSSRuleListImpl).rules.filterIsInstance<CSSStyleRuleImpl>()
    }
}