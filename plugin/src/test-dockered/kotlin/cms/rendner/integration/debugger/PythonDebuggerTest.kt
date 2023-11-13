/*
 * Copyright 2021-2023 cms.rendner (Daniel Schmidt)
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
    fun shouldFailWithASyntaxErrorIfLinebreakIsInSingleLineString() {
        createPythonDebugger { debuggerApi ->

            assertThatExceptionOfType(EvaluateException::class.java).isThrownBy {
                debuggerApi.evaluator.execute("multi = 'line_1\nline_2'")
            }.withMessageStartingWith("{SyntaxError}")

            assertThatExceptionOfType(EvaluateException::class.java).isThrownBy {
                debuggerApi.evaluator.evaluate("'line_1\nline_2'")
            }.withMessageStartingWith("{SyntaxError}")
        }
    }

    @Test
    fun shouldNotFailIfLinebreakIsInTripleQuotes() {
        createPythonDebugger { debuggerApi ->

            debuggerApi.evaluator.execute("multi = '''line_1\nline_2'''")
            val multi = debuggerApi.evaluator.evaluate("multi")
            assertThat(multi.value).isEqualTo("line_1\nline_2")

            val multi2 = debuggerApi.evaluator.evaluate("'''line_1\nline_2'''")
            assertThat(multi2.value).isEqualTo("line_1\nline_2")
        }
    }

    @Test
    fun shouldEvaluateExpression() {
        createPythonDebugger { debuggerApi ->

            val result = debuggerApi.evaluator.evaluate("1 + 2")

            assertThat(result.value).isEqualTo("3")
        }
    }

    @Test
    fun shouldFailOnInvalidCodeForEval() {
        createPythonDebugger { debuggerApi ->

            assertThatExceptionOfType(EvaluateException::class.java).isThrownBy {
                debuggerApi.evaluator.evaluate("abc(42)")
            }.withMessageContaining("{NameError} name 'abc' is not defined")
        }
    }

    @Test
    fun shouldHaveCorrectTypeInformation() {
        createPythonDebugger { debuggerApi ->

            val result = debuggerApi.evaluator.evaluate("{}")

            assertThat(result.type).isEqualTo("dict")
            assertThat(result.typeQualifier).isEqualTo("builtins")
        }
    }

    @Test
    fun shouldHaveCorrectTypeInformationWhenValueIsMultilineString() {
        createPythonDebugger { debuggerApi ->

            debuggerApi.evaluator.execute(
                """
                from pandas import DataFrame
                df = DataFrame.from_dict({
                    "col_0": [0, 1],
                    "col_1": [2, 3],
                })
                """.trimIndent()
            )
            val result = debuggerApi.evaluator.evaluate("df")

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
        createPythonDebugger { debuggerApi ->
            assertThatNoException().isThrownBy {
                debuggerApi.evaluator.execute(
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
        createPythonDebugger { debuggerApi ->

            assertThatExceptionOfType(EvaluateException::class.java).isThrownBy {
                debuggerApi.evaluator.execute("import")
            }.withMessageContaining("{SyntaxError} invalid syntax")
        }
    }

    @Test
    fun shouldBeAbleToAccessPreviousEvaluatedValues() {
        createPythonDebugger { debuggerApi ->

            debuggerApi.evaluator.execute("d = {'a': 10}")

            assertThat(debuggerApi.evaluator.evaluate("d['a']").value).isEqualTo("10")
        }
    }

    @Test
    fun shouldBeAbleToReferToPreviousEvaluatedValues() {
        createPythonDebugger { debuggerApi ->

            val dict = debuggerApi.evaluator.evaluate("{'a': 10}")

            assertThat(debuggerApi.evaluator.evaluate("${dict.refExpr}['a']").value).isEqualTo("10")
        }
    }

    @Test
    fun shouldEvaluateValuesFromExecutedCode() {
        createPythonDebugger { debuggerApi ->

            debuggerApi.evaluator.execute(
                """
                from urllib.parse import urlparse
            
                o = urlparse('http://test.123')
                """.trimIndent()
            )
            val result = debuggerApi.evaluator.evaluate("o.scheme")

            assertThat(result.value).isEqualTo("http")
            assertThat(result.type).isEqualTo("str")
            assertThat(result.typeQualifier).isEqualTo("builtins")
        }
    }

    @Test
    fun shouldDeleteEvaluatedResult() {
        createPythonDebugger { debuggerApi ->

            val dict = debuggerApi.evaluator.evaluate("{'a': 12}")

            debuggerApi.evaluator.execute("del ${dict.refExpr}")

            assertThatExceptionOfType(EvaluateException::class.java).isThrownBy {
                debuggerApi.evaluator.evaluate("${dict.refExpr}['a']")
            }.withMessageContaining("{NameError} name '${dict.refExpr}' is not defined")
        }
    }

    @Test
    fun shouldAllowToReferenceEvalExecObjects() {
        createPythonDebugger { debuggerApi ->

            val evalDict = debuggerApi.evaluator.evaluate("{'a': 12}")

            debuggerApi.evaluator.execute("exec_dict = {'a': ${evalDict.refExpr}['a'] + 1}")

            val execDict = debuggerApi.evaluator.evaluate("exec_dict['a']")

            assertThat(execDict.value).isEqualTo("13")
        }
    }

    @Test
    fun shouldReturnIdentifierAsRefExprWhenEvaluatingAnIdentifier() {
        createPythonDebugger { debuggerApi ->

            debuggerApi.evaluator.execute("a = []")

            val result = debuggerApi.evaluator.evaluate("a")

            assertThat(result.refExpr).isEqualTo("a")
        }
    }

    @Test
    fun shouldAllowToContinueFromABreakpoint() {
        createPythonDebuggerWithCodeSnippet(
            """
            a = 2
            breakpoint()
            a = 5
            breakpoint()
            """.trimIndent()
        ) { debuggerApi ->

            val result = debuggerApi.evaluator.evaluate("a")
            assertThat(result.value).isEqualTo("2")

            debuggerApi.continueFromBreakpoint()

            val result2 = debuggerApi.evaluator.evaluate("a")
            assertThat(result2.value).isEqualTo("5")
        }
    }

    @Test
    fun shouldWorkWithDifferentStackFrames() {
        createPythonDebuggerWithCodeSnippet(
            """
            def my_func():
              a = 2
              breakpoint()
            
            my_func()
            a = 5
            breakpoint()
            """.trimIndent()
        ) { debuggerApi ->

            val result = debuggerApi.evaluator.evaluate("a")
            assertThat(result.value).isEqualTo("2")

            debuggerApi.continueFromBreakpoint()

            val result2 = debuggerApi.evaluator.evaluate("a")
            assertThat(result2.value).isEqualTo("5")
        }
    }
}