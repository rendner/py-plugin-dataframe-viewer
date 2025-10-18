/*
 * Copyright 2021-2025 cms.rendner (Daniel Schmidt)
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
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyFrameAccessor
import cms.rendner.junit.RequiresPandas
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

@Order(1)
internal class PythonPluginCodeInjectorTest: AbstractPluginCodeTest() {

    @Test
    fun shouldInjectCodeWithoutAnError() {
        runPythonDebuggerWithoutPluginCode { debuggerApi ->
            registerCodeProviders(debuggerApi.frameAccessor)
        }
    }

    @Test
    @RequiresPandas
    fun shouldThrowExceptionForUnsupportedPandasMajorVersion() {
        runPythonDebuggerWithoutPluginCode { debuggerApi ->

            val pandasVersion = getPandasVersion(debuggerApi.frameAccessor)

            val unsupportedMajorPandasVersion = "99.0.0"
            debuggerApi.addInterceptor(object: IDebuggerInterceptor {
                override fun onResponse(response: EvalOrExecResponse): EvalOrExecResponse {
                    if (response.value == pandasVersion) {
                        return response.copy(value = unsupportedMajorPandasVersion)
                    }
                    return response
                }
            })

            Assertions.assertThatExceptionOfType(InjectException::class.java).isThrownBy {
                registerCodeProviders(debuggerApi.frameAccessor)
            }.withMessageContaining(
                "Unsupported ${PandasVersion.fromString(unsupportedMajorPandasVersion)}",
            )
        }
    }

    @Test
    @RequiresPandas
    fun shouldThrowExceptionForTooEarlyUnsupportedPandasMinorVersion() {
        runPythonDebuggerWithoutPluginCode { debuggerApi ->

            val pandasVersion = getPandasVersion(debuggerApi.frameAccessor)

            // support starts for pandas 1.1
            val tooEarlyMinorVersion = "1.0.0"
            debuggerApi.addInterceptor(object: IDebuggerInterceptor {
                override fun onResponse(response: EvalOrExecResponse): EvalOrExecResponse {
                    if (response.value == pandasVersion) {
                        return response.copy(value = tooEarlyMinorVersion)
                    }
                    return response
                }
            })

            Assertions.assertThatExceptionOfType(InjectException::class.java).isThrownBy {
                registerCodeProviders(debuggerApi.frameAccessor)
            }.withMessageContaining(
                "Unsupported ${PandasVersion.fromString(tooEarlyMinorVersion)}",
            )
        }
    }

    @Test
    @RequiresPandas
    fun shouldUseLatestAvailablePandasMinorVersionForUnsupportedLaterMinorVersion() {
        runPythonDebuggerWithoutPluginCode { debuggerApi ->

            val pandasVersion = getPandasVersion(debuggerApi.frameAccessor)

            val unsupportedMinorPandasVersion = "2.99.0"
            debuggerApi.addInterceptor(object: IDebuggerInterceptor {
                override fun onResponse(response: EvalOrExecResponse): EvalOrExecResponse {
                    if (response.value == pandasVersion) {
                        return response.copy(value = unsupportedMinorPandasVersion)
                    }
                    return response
                }
            })

            registerCodeProviders(debuggerApi.frameAccessor)
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
                registerCodeProviders(debuggerApi.frameAccessor)
            }

            assertThat(codeInjectDetected).isOne()
        }
    }

    private fun getPandasVersion(frameAccessor: IPluginPyFrameAccessor): String {
        return frameAccessor.evaluate("__import__('pandas').__version__").forcedValue
    }
}