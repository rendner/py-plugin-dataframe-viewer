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
package cms.rendner.integration.plugin.python.bridge

import cms.rendner.debugger.impl.EvalOrExecRequest
import cms.rendner.debugger.impl.EvalOrExecResponse
import cms.rendner.debugger.impl.IDebuggerInterceptor
import cms.rendner.integration.plugin.AbstractPluginCodeTest
import cms.rendner.intellij.dataframe.viewer.python.bridge.PandasVersion
import cms.rendner.intellij.dataframe.viewer.python.bridge.exceptions.InjectException
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

@Order(1)
internal class PythonPluginCodeInjectorTest: AbstractPluginCodeTest() {

    @Test
    fun shouldInjectCodeWithoutAnError() {
        runPythonDebuggerWithoutPluginCode { debuggerApi ->
            registerCodeProviders(debuggerApi.evaluator)
        }
    }

    @Test
    fun shouldThrowExceptionForUnsupportedPandasVersion() {
        runPythonDebuggerWithoutPluginCode { debuggerApi ->

            val pandasVersion = getPandasVersion(debuggerApi.evaluator)

            val nonExistingPandasVersion = "99.99.0"
            debuggerApi.addInterceptor(object: IDebuggerInterceptor {
                override fun onResponse(response: EvalOrExecResponse): EvalOrExecResponse {
                    if (response.value == pandasVersion) {
                        return response.copy(value = nonExistingPandasVersion)
                    }
                    return response
                }
            })

            Assertions.assertThatExceptionOfType(InjectException::class.java).isThrownBy {
                registerCodeProviders(debuggerApi.evaluator)
            }.withMessageContaining(
                "Unsupported ${PandasVersion.fromString(nonExistingPandasVersion)}",
            )
        }
    }

    @Test
    fun shouldInjectCodeOnlyOnceWhenCalledMultipleTimes() {
        runPythonDebuggerWithoutPluginCode { debuggerApi ->

            var codeInjectDetected = 0
            debuggerApi.addInterceptor(object: IDebuggerInterceptor {
                override fun onRequest(request: EvalOrExecRequest): EvalOrExecRequest {
                    if (request.expression.contains("SDFVPluginModulesImporter") ) codeInjectDetected++
                    return request
                }
            })

            repeat(3) {
                registerCodeProviders(debuggerApi.evaluator)
            }

            assertThat(codeInjectDetected).isOne()
        }
    }

    private fun getPandasVersion(evaluator: IPluginPyValueEvaluator): String {
        return evaluator.evaluate("__import__('pandas').__version__").forcedValue
    }
}