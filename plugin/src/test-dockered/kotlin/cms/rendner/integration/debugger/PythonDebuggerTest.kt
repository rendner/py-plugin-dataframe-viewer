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
package cms.rendner.integration.debugger

import cms.rendner.debugger.AbstractPipEnvEnvironmentTest
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.PluginPyDebuggerException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatNoException
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

/**
 * Tests the base functionality of the Python debugger used in the integration tests.
 * These tests should always run first to guarantee that the debugger works as expected,
 * before running the other integration tests.
 *
 * This ordering is achieved by using the @Order annotation and setting "junit.jupiter.testclass.order.default"
 * to "org.junit.jupiter.api.ClassOrderer$OrderAnnotation" in "src/test/resources/junit-platform.properties".
 */
@Order(0)
internal class PythonDebuggerTest : AbstractPipEnvEnvironmentTest() {

    @Test
    fun shouldFailIfLinebreakIsInSingleLineString() {
        runWithPythonDebugger { valueEvaluator, _ ->

            assertFailsWith<EvaluateException> {
                valueEvaluator.execute("multi = 'line_1\nline_2'")
            }.also {
                assertThat(it).hasCauseInstanceOf(PluginPyDebuggerException::class.java)
                assertThat(it.cause).hasMessage("*** SyntaxError: EOL while scanning string literal")
            }

            assertFailsWith<EvaluateException> {
                valueEvaluator.evaluate("'line_1\nline_2'")
            }.also {
                assertThat(it).hasMessage("*** SyntaxError: EOL while scanning string literal")
            }
        }
    }

    @Test
    fun shouldNotFailIfLinebreakIsInTripleQuotes() {
        runWithPythonDebugger { valueEvaluator, _ ->

            valueEvaluator.execute("multi = '''line_1\nline_2'''")
            val multi = valueEvaluator.evaluate("multi")
            assertThat(multi.value).isEqualTo("line_1\nline_2")

            val multi2 = valueEvaluator.evaluate("'''line_1\nline_2'''")
            assertThat(multi2.value).isEqualTo("line_1\nline_2")
        }
    }

    @Test
    fun shouldNotFailIfLinebreakIsInMultilineString() {
        runWithPythonDebugger { valueEvaluator, _ ->

            valueEvaluator.execute("""multi = 'line_1\nline_2'""")
            val multi = valueEvaluator.evaluate("multi")
            assertThat(multi.value).isEqualTo("line_1\nline_2")

            val multi2 = valueEvaluator.evaluate("""'line_1\nline_2'""")
            assertThat(multi2.value).isEqualTo("line_1\nline_2")
        }
    }


    @Test
    fun shouldEvaluateExpression() {
        runWithPythonDebugger { valueEvaluator, _ ->

            val result = valueEvaluator.evaluate("1 + 2")

            assertThat(result.value).isEqualTo("3")
        }
    }

    @Test
    fun shouldFailOnInvalidCodeForEval() {
        runWithPythonDebugger { valueEvaluator, _ ->

            assertFailsWith<EvaluateException> {
                valueEvaluator.evaluate("abc(42)")
            }.also {
                assertThat(it).hasMessage("*** NameError: name 'abc' is not defined")
            }
        }
    }

    @Test
    fun shouldHaveCorrectTypeInformation() {
        runWithPythonDebugger { valueEvaluator, _ ->

            val result = valueEvaluator.evaluate("{}")

            assertThat(result.type).isEqualTo("dict")
            assertThat(result.typeQualifier).isEqualTo("builtins")
        }
    }

    @Test
    fun shouldAllowToExecuteCode() {
        runWithPythonDebugger { valueEvaluator, _ ->
            assertThatNoException().isThrownBy { valueEvaluator.execute(getPythonSnippet()) }
        }
    }

    @Test
    fun shouldFailOnInvalidCodeForExec() {
        runWithPythonDebugger { valueEvaluator, _ ->

            assertFailsWith<EvaluateException> { valueEvaluator.execute("import") }
                .also {
                    assertThat(it).hasCauseInstanceOf(PluginPyDebuggerException::class.java)
                    assertThat(it.cause).hasMessage("*** SyntaxError: invalid syntax")
                }
        }
    }

    @Test
    fun shouldBeAbleToAccessPreviousEvaluatedValues() {
        runWithPythonDebugger { valueEvaluator, _ ->

            valueEvaluator.execute("d = {'a': 10}")

            assertThat(valueEvaluator.evaluate("d['a']").value).isEqualTo("10")
        }
    }

    @Test
    fun shouldBeAbleToReferToPreviousEvaluatedValues() {
        runWithPythonDebugger { valueEvaluator, _ ->

            val dict = valueEvaluator.evaluate("{'a': 10}")

            assertThat(valueEvaluator.evaluate("${dict.pythonRefEvalExpr}['a']").value).isEqualTo("10")
        }
    }

    @Test
    fun shouldEvaluateValuesFromExecutedCode() {
        runWithPythonDebugger { valueEvaluator, _ ->

            valueEvaluator.execute(getPythonSnippet())
            val result = valueEvaluator.evaluate("o.scheme")

            assertThat(result.value).isEqualTo("http")
            assertThat(result.type).isEqualTo("str")
            assertThat(result.typeQualifier).isEqualTo("builtins")
        }
    }

    @Test
    fun shouldDeleteEvaluatedResult() {
        runWithPythonDebugger { valueEvaluator, _ ->

            val dict = valueEvaluator.evaluate("{'a': 12}")

            valueEvaluator.execute("del ${dict.pythonRefEvalExpr}")

            assertFailsWith<EvaluateException> {
                valueEvaluator.evaluate("${dict.pythonRefEvalExpr}['a']")
            }.also {
                assertThat(it).hasMessage("*** NameError: name '${dict.pythonRefEvalExpr}' is not defined")
            }
        }
    }

    @Test
    fun shouldAllowToReferenceEvalExecObjects() {
        runWithPythonDebugger { valueEvaluator, _ ->

            val evalDict = valueEvaluator.evaluate("{'a': 12}")

            valueEvaluator.execute("exec_dict = {'a': ${evalDict.pythonRefEvalExpr}['a'] + 1}")

            val execDict = valueEvaluator.evaluate("exec_dict['a']")

            assertThat(execDict.value).isEqualTo("13")
        }
    }


    private fun getPythonSnippet(): String {
        return """
            from urllib.parse import urlparse
            
            o = urlparse('http://test.123')
        """.trimIndent()
    }
}