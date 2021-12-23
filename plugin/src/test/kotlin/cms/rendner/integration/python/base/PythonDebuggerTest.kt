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
package cms.rendner.integration.python.base

import cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.exceptions.EvaluateException
import com.jetbrains.python.debugger.PyDebuggerException
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
        runWithPythonDebugger { debugger ->

            val evaluator = createValueEvaluator(debugger)

            assertFailsWith<EvaluateException> {
                evaluator.execute("multi = 'line_1\nline_2'")
            }.also {
                assertThat(it).hasCauseInstanceOf(PyDebuggerException::class.java)
                assertThat(it.cause).hasMessage("*** SyntaxError: EOL while scanning string literal")
            }

            assertFailsWith<EvaluateException> {
                evaluator.evaluate("'line_1\nline_2'")
            }.also {
                assertThat(it).hasMessage("*** SyntaxError: EOL while scanning string literal")
            }
        }
    }

    @Test
    fun shouldNotFailIfLinebreakIsInTripleQuotes() {
        runWithPythonDebugger { debugger ->

            val evaluator = createValueEvaluator(debugger)

            evaluator.execute("multi = '''line_1\nline_2'''")
            val multi = evaluator.evaluate("multi")
            assertThat(multi.value).isEqualTo("line_1\nline_2")

            val multi2 = evaluator.evaluate("'''line_1\nline_2'''")
            assertThat(multi2.value).isEqualTo("line_1\nline_2")
        }
    }

    @Test
    fun shouldNotFailIfLinebreakIsInMultilineString() {
        runWithPythonDebugger { debugger ->

            val evaluator = createValueEvaluator(debugger)

            evaluator.execute("""multi = 'line_1\nline_2'""")
            val multi = evaluator.evaluate("multi")
            assertThat(multi.value).isEqualTo("line_1\nline_2")

            val multi2 = evaluator.evaluate("""'line_1\nline_2'""")
            assertThat(multi2.value).isEqualTo("line_1\nline_2")
        }
    }


    @Test
    fun shouldEvaluateExpression() {
        runWithPythonDebugger { debugger ->

            val evaluator = createValueEvaluator(debugger)
            val result = evaluator.evaluate("1 + 2")

            assertThat(result.value).isEqualTo("3")
        }
    }

    @Test
    fun shouldFailOnInvalidCodeForEval() {
        runWithPythonDebugger { debugger ->

            val evaluator = createValueEvaluator(debugger)

            assertFailsWith<EvaluateException> {
                evaluator.evaluate("abc(42)")
            }.also {
                assertThat(it).hasMessage("*** NameError: name 'abc' is not defined")
            }
        }
    }

    @Test
    fun shouldHaveCorrectTypeInformation() {
        runWithPythonDebugger { debugger ->

            val evaluator = createValueEvaluator(debugger)
            val result = evaluator.evaluate("{}")

            assertThat(result.type).isEqualTo("dict")
            assertThat(result.typeQualifier).isEqualTo("builtins")
        }
    }

    @Test
    fun shouldAllowToExecuteCode() {
        runWithPythonDebugger { debugger ->

            val evaluator = createValueEvaluator(debugger)
            assertThatNoException().isThrownBy { evaluator.execute(getPythonSnippet()) }
        }
    }

    @Test
    fun shouldFailOnInvalidCodeForExec() {
        runWithPythonDebugger { debugger ->

            val evaluator = createValueEvaluator(debugger)

            assertFailsWith<EvaluateException> { evaluator.execute("import") }
                .also {
                    assertThat(it).hasCauseInstanceOf(PyDebuggerException::class.java)
                    assertThat(it.cause).hasMessage("*** SyntaxError: invalid syntax")
                }
        }
    }

    @Test
    fun shouldBeAbleToAccessPreviousEvaluatedValues() {
        runWithPythonDebugger { debugger ->

            val evaluator = createValueEvaluator(debugger)
            evaluator.execute("d = {'a': 10}")

            assertThat(evaluator.evaluate("d['a']").value).isEqualTo("10")
        }
    }

    @Test
    fun shouldBeAbleToReferToPreviousEvaluatedValues() {
        runWithPythonDebugger { debugger ->

            val evaluator = createValueEvaluator(debugger)
            val dict = evaluator.evaluate("{'a': 10}")

            assertThat(evaluator.evaluate("${dict.tempName}['a']").value).isEqualTo("10")
        }
    }

    @Test
    fun shouldEvaluateValuesFromExecutedCode() {
        runWithPythonDebugger { debugger ->

            val evaluator = createValueEvaluator(debugger)
            evaluator.execute(getPythonSnippet())
            val result = evaluator.evaluate("o.scheme")

            assertThat(result.value).isEqualTo("http")
            assertThat(result.type).isEqualTo("str")
            assertThat(result.typeQualifier).isEqualTo("builtins")
        }
    }

    @Test
    fun shouldDeleteEvaluatedResult() {
        runWithPythonDebugger { debugger ->

            val evaluator = createValueEvaluator(debugger)
            val dict = evaluator.evaluate("{'a': 12}")

            evaluator.execute("del ${dict.tempName}")

            assertFailsWith<EvaluateException> {
                evaluator.evaluate("${dict.tempName}['a']")
            }.also {
                assertThat(it).hasMessage("*** NameError: name '${dict.tempName}' is not defined")
            }
        }
    }

    @Test
    fun shouldAllowToReferenceEvalExecObjects() {
        runWithPythonDebugger { debugger ->

            val evaluator = createValueEvaluator(debugger)
            val evalDict = evaluator.evaluate("{'a': 12}")

            evaluator.execute("exec_dict = {'a': ${evalDict.tempName}['a'] + 1}")

            val execDict = evaluator.evaluate("exec_dict['a']")

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