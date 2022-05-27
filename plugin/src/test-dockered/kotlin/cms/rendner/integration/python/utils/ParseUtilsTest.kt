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
package cms.rendner.integration.python.utils

import cms.rendner.debugger.AbstractPipEnvEnvironmentTest
import cms.rendner.intellij.dataframe.viewer.python.utils.*
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith


@Order(1)
internal class ParseUtilsTest : AbstractPipEnvEnvironmentTest() {
    @Test
    fun fromPythonDictionary_shouldParseSimpleValues() {
        runWithPythonDebugger { valueEvaluator, _ ->
            Assertions.assertThat(
                parsePythonDictionary(valueEvaluator.evaluate("{'a': 1, 'b': 'xyz'}").forcedValue)
            ).isEqualTo(
                mapOf(Pair("a", "1"), Pair("b", "'xyz'"))
            )
        }
    }

    @Test
    fun fromPythonDictionary_shouldFailWhenValuesContainDelimiter() {
        runWithPythonDebugger { valueEvaluator, _ ->
            assertFailsWith<Exception> {
                parsePythonDictionary(valueEvaluator.evaluate("{'a': [1, 2]}").forcedValue)
            }
        }
    }

    @Test
    fun fromPythonDictionary_shouldFailIfNone() {
        runWithPythonDebugger { valueEvaluator, _ ->
            assertFailsWith<IllegalArgumentException> {
                parsePythonDictionary(valueEvaluator.evaluate("None").forcedValue)
            }
        }
    }

    @Test
    fun fromPythonList_shouldParseSimpleValues() {
        runWithPythonDebugger { valueEvaluator, _ ->
            Assertions.assertThat(
                parsePythonList(valueEvaluator.evaluate("[1, 'xyz']").forcedValue)
            ).isEqualTo(
                listOf("1", "'xyz'")
            )
        }
    }

    @Test
    fun fromPythonList_shouldFailWhenValuesContainDelimiter() {
        runWithPythonDebugger { valueEvaluator, _ ->
            assertFailsWith<Exception> {
                parsePythonList(valueEvaluator.evaluate("[a, [1, 2]]").forcedValue)
            }
        }
    }

    @Test
    fun fromPythonList_shouldFailIfNone() {
        runWithPythonDebugger { valueEvaluator, _ ->
            assertFailsWith<IllegalArgumentException> {
                parsePythonList(valueEvaluator.evaluate("None").forcedValue)
            }
        }
    }

    @Test
    fun fromPythonTuple_shouldParseSimpleValues() {
        runWithPythonDebugger { valueEvaluator, _ ->
            Assertions.assertThat(
                parsePythonTuple(valueEvaluator.evaluate("(1, 'xyz')").forcedValue)
            ).isEqualTo(
                listOf("1", "'xyz'")
            )
        }
    }

    @Test
    fun fromPythonTuple_shouldFailWhenValuesContainDelimiter() {
        runWithPythonDebugger { valueEvaluator, _ ->
            assertFailsWith<Exception> {
                parsePythonTuple(valueEvaluator.evaluate("(a, [1, 2])").forcedValue)
            }
        }
    }

    @Test
    fun fromPythonTuple_shouldFailIfNone() {
        runWithPythonDebugger { valueEvaluator, _ ->
            assertFailsWith<IllegalArgumentException> {
                parsePythonTuple(valueEvaluator.evaluate("None").forcedValue)
            }
        }
    }

    @Test
    fun fromPythonBool_shouldParseTrue() {
        runWithPythonDebugger { valueEvaluator, _ ->
            Assertions.assertThat(
                parsePythonBool(valueEvaluator.evaluate("True").forcedValue)
            ).isTrue()
        }
    }

    @Test
    fun fromPythonBool_shouldParseFalse() {
        runWithPythonDebugger { valueEvaluator, _ ->
            Assertions.assertThat(
                parsePythonBool(valueEvaluator.evaluate("False").forcedValue)
            ).isFalse()
        }
    }

    @Test
    fun fromPythonBool_shouldFailIfNone() {
        runWithPythonDebugger { valueEvaluator, _ ->
            assertFailsWith<IllegalArgumentException> {
                parsePythonBool(valueEvaluator.evaluate("None").forcedValue)
            }
        }
    }

    @Test
    fun fromPythonInt_shouldParseInt() {
        runWithPythonDebugger { valueEvaluator, _ ->
            Assertions.assertThat(
                parsePythonInt(valueEvaluator.evaluate("100").forcedValue)
            ).isEqualTo(100)
        }
    }

    @Test
    fun fromPythonInt_shouldFailIfNone() {
        runWithPythonDebugger { valueEvaluator, _ ->
            assertFailsWith<IllegalArgumentException> {
                parsePythonInt(valueEvaluator.evaluate("None").forcedValue)
            }
        }
    }

    @Test
    fun fromPythonString_shouldParseInt() {
        runWithPythonDebugger { valueEvaluator, _ ->
            Assertions.assertThat(
                parsePythonString(valueEvaluator.evaluate("'100'").forcedValue)
            ).isEqualTo("100")
        }
    }

    @Test
    fun fromPythonString_shouldFailIfNone() {
        runWithPythonDebugger { valueEvaluator, _ ->
            assertFailsWith<IllegalArgumentException> {
                parsePythonString(valueEvaluator.evaluate("None").forcedValue)
            }
        }
    }
}