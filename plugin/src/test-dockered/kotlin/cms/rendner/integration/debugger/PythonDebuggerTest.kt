/*
 * Copyright 2021-2026 cms.rendner (Daniel Schmidt)
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
import cms.rendner.intellij.dataframe.viewer.python.PythonQualifiedTypes
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
                debuggerApi.frameAccessor.execute("multi = 'line_1\nline_2'")
            }.extracting("pythonErrorQName").isEqualTo(PythonQualifiedTypes.SyntaxError)

            assertThatExceptionOfType(EvaluateException::class.java).isThrownBy {
                debuggerApi.frameAccessor.evaluate("'line_1\nline_2'")
            }.extracting("pythonErrorQName").isEqualTo(PythonQualifiedTypes.SyntaxError)
        }
    }

    @Test
    fun shouldNotFailIfLinebreakIsInTripleQuotes() {
        createPythonDebugger { debuggerApi ->

            debuggerApi.frameAccessor.execute("multi = '''line_1\nline_2'''")
            val multi = debuggerApi.frameAccessor.evaluate("multi")
            assertThat(multi.value).isEqualTo("line_1\nline_2")

            val multi2 = debuggerApi.frameAccessor.evaluate("'''line_1\nline_2'''")
            assertThat(multi2.value).isEqualTo("line_1\nline_2")
        }
    }

    @Test
    fun shouldEvaluateExpression() {
        createPythonDebugger { debuggerApi ->

            val result = debuggerApi.frameAccessor.evaluate("1 + 2")

            assertThat(result.value).isEqualTo("3")
        }
    }

    @Test
    fun shouldFailOnInvalidCodeForEval() {
        createPythonDebugger { debuggerApi ->

            assertThatExceptionOfType(EvaluateException::class.java).isThrownBy {
                debuggerApi.frameAccessor.evaluate("abc(42)")
            }.extracting("pythonErrorQName").isEqualTo(PythonQualifiedTypes.NameError)
        }
    }

    @Test
    fun shouldHaveCorrectTypeInformation() {
        createPythonDebugger { debuggerApi ->

            val result = debuggerApi.frameAccessor.evaluate("{}")

            assertThat(result.type).isEqualTo("dict")
            assertThat(result.typeQualifier).isEqualTo("builtins")
        }
    }

    @Test
    fun shouldHaveCorrectTypeInformationWhenValueIsMultilineString() {
        createPythonDebugger { debuggerApi ->

            debuggerApi.frameAccessor.execute(
                """
                multiline_str = '''
                First line
                Second line
                '''
                """.trimIndent()
            )
            val result = debuggerApi.frameAccessor.evaluate("multiline_str")

            assertThat(result.value).isEqualTo("""
                
                First line
                Second line
                
                """.trimIndent())
            assertThat(result.type).isEqualTo("str")
            assertThat(result.typeQualifier).isEqualTo("builtins")
        }
    }

    @Test
    fun shouldAllowToExecuteCode() {
        createPythonDebugger { debuggerApi ->
            assertThatNoException().isThrownBy {
                debuggerApi.frameAccessor.execute(
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
                debuggerApi.frameAccessor.execute("import")
            }.extracting("pythonErrorQName").isEqualTo(PythonQualifiedTypes.SyntaxError)
        }
    }

    @Test
    fun shouldBeAbleToAccessPreviousEvaluatedValues() {
        createPythonDebugger { debuggerApi ->

            debuggerApi.frameAccessor.execute("d = {'a': 10}")

            assertThat(debuggerApi.frameAccessor.evaluate("d['a']").value).isEqualTo("10")
        }
    }

    @Test
    fun shouldBeAbleToReferToPreviousEvaluatedValues() {
        createPythonDebugger { debuggerApi ->

            val dict = debuggerApi.frameAccessor.evaluate("{'a': 10}")

            assertThat(debuggerApi.frameAccessor.evaluate("${dict.refExpr}['a']").value).isEqualTo("10")
        }
    }

    @Test
    fun shouldEvaluateValuesFromExecutedCode() {
        createPythonDebugger { debuggerApi ->

            debuggerApi.frameAccessor.execute(
                """
                from urllib.parse import urlparse
            
                o = urlparse('http://test.123')
                """.trimIndent()
            )
            val result = debuggerApi.frameAccessor.evaluate("o.scheme")

            assertThat(result.value).isEqualTo("http")
            assertThat(result.type).isEqualTo("str")
            assertThat(result.typeQualifier).isEqualTo("builtins")
        }
    }

    @Test
    fun shouldDeleteEvaluatedResult() {
        createPythonDebugger { debuggerApi ->

            val dict = debuggerApi.frameAccessor.evaluate("{'a': 12}")

            debuggerApi.frameAccessor.execute("del ${dict.refExpr}")

            assertThatExceptionOfType(EvaluateException::class.java).isThrownBy {
                debuggerApi.frameAccessor.evaluate("${dict.refExpr}['a']")
            }.extracting("pythonErrorQName").isEqualTo(PythonQualifiedTypes.NameError)
        }
    }

    @Test
    fun shouldAllowToReferenceEvalExecObjects() {
        createPythonDebugger { debuggerApi ->

            val evalDict = debuggerApi.frameAccessor.evaluate("{'a': 12}")

            debuggerApi.frameAccessor.execute("exec_dict = {'a': ${evalDict.refExpr}['a'] + 1}")

            val execDict = debuggerApi.frameAccessor.evaluate("exec_dict['a']")

            assertThat(execDict.value).isEqualTo("13")
        }
    }

    @Test
    fun shouldReturnIdentifierAsRefExprWhenEvaluatingAnIdentifier() {
        createPythonDebugger { debuggerApi ->

            debuggerApi.frameAccessor.execute("a = []")

            val result = debuggerApi.frameAccessor.evaluate("a")

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

            val result = debuggerApi.frameAccessor.evaluate("a")
            assertThat(result.value).isEqualTo("2")

            debuggerApi.continueFromBreakpoint()

            val result2 = debuggerApi.frameAccessor.evaluate("a")
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

            val result = debuggerApi.frameAccessor.evaluate("a")
            assertThat(result.value).isEqualTo("2")

            debuggerApi.continueFromBreakpoint()

            val result2 = debuggerApi.frameAccessor.evaluate("a")
            assertThat(result2.value).isEqualTo("5")
        }
    }

    @Test
    fun shouldFindReferrerChains() {
        createPythonDebuggerWithCodeSnippet(
            """
            a = [1, 2, 3]
            
            class B:
                def __init__(self, value):
                    self.value = value
                    
            d = B({ "A": a })
            
            breakpoint()
            """.trimIndent()
        ) { debuggerApi ->

            val result = debuggerApi.findReferrerChains("a")
            // Python 3.10:     "<class '__main__.B'>, <class 'dict'>, <class 'dict'>, <class 'list'>"
            // Python >= 3.11:  "<class '__main__.B'>, <class 'dict'>, <class 'list'>"
            assertThat(result).filteredOn { it.contains("<class '__main__.B'>") && it.endsWith("<class 'list'>") }.isNotEmpty()
        }
    }

    @Test
    fun shouldEvaluateSimulatedExec() {
        createPythonDebugger { debuggerApi ->

            val statement = """
                from urllib.parse import urlparse
            
                o = urlparse('http://test.123')
                """.trimIndent()
            val escaped = debuggerApi.frameAccessor.toEscapedSingleLineExec(statement)
            // some frameAccessor do evaluate when calling execute, to simulate this evaluate is called on purpose
            debuggerApi.frameAccessor.evaluate(escaped)

            val result = debuggerApi.frameAccessor.evaluate("o.scheme")

            assertThat(result.value).isEqualTo("http")
            assertThat(result.type).isEqualTo("str")
            assertThat(result.typeQualifier).isEqualTo("builtins")
        }
    }
}
