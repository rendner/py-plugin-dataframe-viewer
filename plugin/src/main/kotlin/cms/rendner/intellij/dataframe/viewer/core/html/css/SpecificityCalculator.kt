package cms.rendner.intellij.dataframe.viewer.core.html.css

import com.steadystate.css.parser.CSSOMParser
import com.steadystate.css.parser.selectors.SyntheticElementSelectorImpl
import org.w3c.css.sac.*
import java.io.StringReader

/**
 * https://www.w3.org/TR/selectors-3/#specificity
 */
class SpecificityCalculator(private val parser: CSSOMParser) {

    @Deprecated(message = "Please use: calculate(selector: Selector)")
    fun calculate(selectorText: String): Specificity {
        val selectors = StringReader(selectorText).use {
                synchronized(parser) {
                    parser.parseSelectors(InputSource(it))
                }
            }
        return calculate(selectors.item(0))
    }

    fun calculate(selector: Selector): Specificity {
        val counter = Counter()
        countSelectors(selector, counter)
        return createSpecificity(counter)
    }

    private fun createSpecificity(counter: Counter): Specificity {
        val a = counter.idSelectors
        val b = counter.classSelectors + counter.attributeSelectors + counter.pseudoClasses
        val c = counter.typeSelectors + counter.pseudoElements
        return Specificity(a, b, c)
    }

    private fun countSelectors(selector: Selector, counter: Counter) {
        when (selector) {
            is SyntheticElementSelectorImpl -> return
            is DescendantSelector -> {
                countSelectors(selector.simpleSelector, counter)
                countSelectors(selector.ancestorSelector, counter)
            }
            is ConditionalSelector -> {
                countSelectors(selector.simpleSelector, counter)
                countSelectors(selector.condition, counter)
            }
            is ElementSelector -> {
                if (selector.toString() != "*") counter.typeSelectors++
            }
            is SiblingSelector -> {
                countSelectors(selector.selector, counter)
                countSelectors(selector.siblingSelector, counter)
            }
            else -> TODO("Not implemented yet")
        }
    }

    private fun countSelectors(condition: Condition, counter: Counter) {
        when (condition.conditionType) {
            Condition.SAC_CLASS_CONDITION -> counter.classSelectors++
            Condition.SAC_ID_CONDITION -> counter.idSelectors++
            Condition.SAC_ATTRIBUTE_CONDITION -> counter.attributeSelectors++
            Condition.SAC_PSEUDO_CLASS_CONDITION -> {
                if ((condition as AttributeCondition).value.startsWith("not(")) {
                    val selectorText = condition.value.substring(4, condition.value.length - 1)
                    val selector = synchronized(parser) {
                        // todo: do we have to take into account that there could be a comma separated list of selectors in the string?
                        parser.parseSelectors(InputSource(StringReader(selectorText))).item(0)
                    }
                    countSelectors(selector, counter)
                } else {
                    counter.pseudoClasses++
                }
            }
            Condition.SAC_AND_CONDITION -> {
                countSelectors((condition as CombinatorCondition).firstCondition, counter)
                countSelectors((condition as CombinatorCondition).secondCondition, counter)
            }
            else -> TODO("Not implemented yet")
        }
    }

    private data class Counter(
        var idSelectors: Int = 0,
        var classSelectors: Int = 0,
        var attributeSelectors: Int = 0,
        var pseudoClasses: Int = 0,
        var typeSelectors: Int = 0,
        var pseudoElements: Int = 0
    )
}