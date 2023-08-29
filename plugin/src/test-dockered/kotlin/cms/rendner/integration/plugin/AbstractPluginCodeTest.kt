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
package cms.rendner.integration.plugin

import cms.rendner.debugger.AbstractPipEnvEnvironmentTest
import cms.rendner.debugger.impl.IPythonDebuggerApi
import cms.rendner.intellij.dataframe.viewer.python.bridge.PythonPluginCodeInjector

/**
 * Abstract class to run tests with a Python interpreter.
 * The plugin code is automatically injected. Use [runPythonDebuggerWithoutPluginCode] in case the plugin code should not be injected.
 */
internal abstract class AbstractPluginCodeTest : AbstractPipEnvEnvironmentTest() {

    override fun createPythonDebuggerWithSourceFile(
        sourceFile: String,
        block: (debuggerApi: IPythonDebuggerApi) -> Unit,
    ) {
        super.createPythonDebuggerWithSourceFile(sourceFile) { debuggerApi ->
            PythonPluginCodeInjector.injectIfRequired(debuggerApi.evaluator, ::pluginCodeEscaper)
            block(debuggerApi)
        }
    }

    override fun createPythonDebuggerWithCodeSnippet(
        codeSnippet: String,
        block: (debuggerApi: IPythonDebuggerApi) -> Unit,
    ) {
        super.createPythonDebuggerWithCodeSnippet(codeSnippet) { debuggerApi ->
            PythonPluginCodeInjector.injectIfRequired(debuggerApi.evaluator, ::pluginCodeEscaper)
            block(debuggerApi)
        }
    }

    fun runPythonDebuggerWithoutPluginCode(
        block: (debuggerApi: IPythonDebuggerApi) -> Unit,
    ) {
        super.createPythonDebuggerWithCodeSnippet("breakpoint()") { debuggerApi ->
            block(debuggerApi)
        }
    }

    protected fun pluginCodeEscaper(code: String): String {
        return code
            .replace("\\n", "\\\n")
            .replace("\\t", "\\\t")
    }
}