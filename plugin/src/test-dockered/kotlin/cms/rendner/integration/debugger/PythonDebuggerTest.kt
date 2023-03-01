/*
 * Copyright 2023 cms.rendner (Daniel Schmidt)
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
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

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
        runPythonDebugger { valueEvaluator, _ ->

            assertThatExceptionOfType(EvaluateException::class.java).isThrownBy {
                valueEvaluator.execute("multi = 'line_1\nline_2'")
            }.withMessageContaining("{SyntaxError} EOL while scanning string literal")

            assertThatExceptionOfType(EvaluateException::class.java).isThrownBy {
                valueEvaluator.evaluate("'line_1\nline_2'")
            }.withMessageContaining("{SyntaxError} EOL while scanning string literal")
        }
    }

    @Test
    fun shouldNotFailIfLinebreakIsInTripleQuotes() {
        runPythonDebugger { valueEvaluator, _ ->

            valueEvaluator.execute("multi = '''line_1\nline_2'''")
            val multi = valueEvaluator.evaluate("multi")
            assertThat(multi.value).isEqualTo("line_1\nline_2")

            val multi2 = valueEvaluator.evaluate("'''line_1\nline_2'''")
            assertThat(multi2.value).isEqualTo("line_1\nline_2")
        }
    }

    @Test
    fun shouldEvaluateExpression() {
        runPythonDebugger { valueEvaluator, _ ->

            val result = valueEvaluator.evaluate("1 + 2")

            assertThat(result.value).isEqualTo("3")
        }
    }

    @Test
    fun shouldFailOnInvalidCodeForEval() {
        runPythonDebugger { valueEvaluator, _ ->

            assertThatExceptionOfType(EvaluateException::class.java).isThrownBy {
                valueEvaluator.evaluate("abc(42)")
            }.withMessageContaining("{NameError} name 'abc' is not defined")
        }
    }

    @Test
    fun shouldHaveCorrectTypeInformation() {
        runPythonDebugger { valueEvaluator, _ ->

            val result = valueEvaluator.evaluate("{}")

            assertThat(result.type).isEqualTo("dict")
            assertThat(result.typeQualifier).isEqualTo("builtins")
        }
    }

    @Test
    fun shouldHaveCorrectTypeInformationWhenValueIsMultilineString() {
        runPythonDebugger { valueEvaluator, _ ->

            valueEvaluator.execute(
                """
                from pandas import DataFrame
                df = DataFrame.from_dict({
                    "col_0": [0, 1],
                    "col_1": [2, 3],
                })
                """.trimIndent()
            )
            val result = valueEvaluator.evaluate("df")

            assertThat(result.value).isEqualTo(
                """
               col_0  col_1
            0      0      2
            1      1      3
            """.trimIndent())
            assertThat(result.type).isEqualTo("DataFrame")
            assertThat(result.typeQualifier).isEqualTo("pandas.core.frame")
        }
    }

    @Test
    fun shouldAllowToExecuteCode() {
        runPythonDebugger { valueEvaluator, _ ->
            assertThatNoException().isThrownBy {
                valueEvaluator.execute(
                    """
                    from urllib.parse import urlparse
            
                    o = urlparse('http://test.123')
                    """.trimIndent()
                )
            }
        }
    }

    @Test
    fun shouldFailOnInvalidCodeForExec() {
        runPythonDebugger { valueEvaluator, _ ->

            assertThatExceptionOfType(EvaluateException::class.java).isThrownBy {
                valueEvaluator.execute("import")
            }.withMessageContaining("{SyntaxError} invalid syntax")
        }
    }

    @Test
    fun shouldBeAbleToAccessPreviousEvaluatedValues() {
        runPythonDebugger { valueEvaluator, _ ->

            valueEvaluator.execute("d = {'a': 10}")

            assertThat(valueEvaluator.evaluate("d['a']").value).isEqualTo("10")
        }
    }

    @Test
    fun shouldBeAbleToReferToPreviousEvaluatedValues() {
        runPythonDebugger { valueEvaluator, _ ->

            val dict = valueEvaluator.evaluate("{'a': 10}")

            assertThat(valueEvaluator.evaluate("${dict.refExpr}['a']").value).isEqualTo("10")
        }
    }

    @Test
    fun shouldEvaluateValuesFromExecutedCode() {
        runPythonDebugger { valueEvaluator, _ ->

            valueEvaluator.execute(
                """
                from urllib.parse import urlparse
            
                o = urlparse('http://test.123')
                """.trimIndent()
            )
            val result = valueEvaluator.evaluate("o.scheme")

            assertThat(result.value).isEqualTo("http")
            assertThat(result.type).isEqualTo("str")
            assertThat(result.typeQualifier).isEqualTo("builtins")
        }
    }

    @Test
    fun shouldDeleteEvaluatedResult() {
        runPythonDebugger { valueEvaluator, _ ->

            val dict = valueEvaluator.evaluate("{'a': 12}")

            valueEvaluator.execute("del ${dict.refExpr}")

            assertThatExceptionOfType(EvaluateException::class.java).isThrownBy {
                valueEvaluator.evaluate("${dict.refExpr}['a']")
            }.withMessageContaining("{NameError} name '${dict.refExpr}' is not defined")
        }
    }

    @Test
    fun shouldAllowToReferenceEvalExecObjects() {
        runPythonDebugger { valueEvaluator, _ ->

            val evalDict = valueEvaluator.evaluate("{'a': 12}")

            valueEvaluator.execute("exec_dict = {'a': ${evalDict.refExpr}['a'] + 1}")

            val execDict = valueEvaluator.evaluate("exec_dict['a']")

            assertThat(execDict.value).isEqualTo("13")
        }
    }

    @Test
    fun shouldReturnIdentifierAsRefExprWhenEvaluatingAnIdentifier() {
        runPythonDebugger { valueEvaluator, _ ->

            valueEvaluator.execute("a = []")

            val result = valueEvaluator.evaluate("a")

            assertThat(result.refExpr).isEqualTo("a")
        }
    }

    @Test
    fun shouldAllowToContinueFromABreakpoint() {
        runPythonDebuggerWithCodeSnippet(
            """
            a = 2
            breakpoint()
            a = 5
            breakpoint()
            """.trimIndent()
        ) { valueEvaluator, debugger ->

            val result = valueEvaluator.evaluate("a")
            assertThat(result.value).isEqualTo("2")

            debugger.submitContinue().get()

            val result2 = valueEvaluator.evaluate("a")
            assertThat(result2.value).isEqualTo("5")
        }
    }

    @Test
    fun shouldWorkWithDifferentStackFrames() {
        runPythonDebuggerWithCodeSnippet(
            """
            def my_func():
              a = 2
              breakpoint()
            
            my_func()
            a = 5
            breakpoint()
            """.trimIndent()
        ) { valueEvaluator, debugger ->

            val result = valueEvaluator.evaluate("a")
            assertThat(result.value).isEqualTo("2")

            debugger.submitContinue().get()

            val result2 = valueEvaluator.evaluate("a")
            assertThat(result2.value).isEqualTo("5")
        }
    }
}