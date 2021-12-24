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

import com.google.common.collect.ImmutableList
import com.steadystate.css.parser.CSSOMParser
import com.steadystate.css.parser.SACParserCSS3
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class SpecificityCalculatorTest {

    data class TestCase(val selector: String, val expectedSpecificity: Specificity) {
        override fun toString() = "'$selector' should have $expectedSpecificity"
    }

    @Suppress("unused")
    private fun getTestCases() = ImmutableList.of(
        // https://www.w3.org/TR/selectors-3/#specificity
        TestCase("*", Specificity()),
        TestCase("LI", Specificity(c = 1)),
        TestCase("UL LI", Specificity(c = 2)),
        TestCase("UL OL+LI", Specificity(c = 3)),
        TestCase("H1 + *[REL=up]", Specificity(b = 1, c = 1)),
        TestCase("UL OL LI.red", Specificity(b = 1, c = 3)),
        TestCase("LI.red.level", Specificity(b = 2, c = 1)),
        TestCase("#x34y", Specificity(a = 1)),
        TestCase("#s12:not(FOO)", Specificity(a = 1, c = 1)),

        // https://css-tricks.com/specifics-on-css-specificity/
        TestCase("ul#nav li.active a", Specificity(a = 1, b = 1, c = 3)),
        TestCase("body.ie7 .col_3 h2 ~ h2", Specificity(b = 2, c = 3)),
        TestCase("#footer *:not(nav) li", Specificity(a = 1, c = 2)),
        TestCase("ul > li ul li ol li:first-letter", Specificity(c = 7))
    )

    @ParameterizedTest(name = "{0}")
    @MethodSource("getTestCases")
    fun testCalculate(testCase: TestCase) {
        val cut = SpecificityCalculator(CSSOMParser(SACParserCSS3()))
        assertThat(cut.calculate(testCase.selector))
            .isEqualTo(testCase.expectedSpecificity)
    }
}