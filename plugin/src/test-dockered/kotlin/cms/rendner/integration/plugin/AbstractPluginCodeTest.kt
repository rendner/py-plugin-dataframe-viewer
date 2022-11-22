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
package cms.rendner.integration.plugin

import cms.rendner.debugger.AbstractPipEnvEnvironmentTest
import cms.rendner.debugger.impl.PythonEvalDebugger
import cms.rendner.intellij.dataframe.viewer.python.bridge.PythonPluginCodeInjector
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator

/**
 * Abstract class to run tests with a Python interpreter.
 * The plugin code
 */
internal abstract class AbstractPluginCodeTest : AbstractPipEnvEnvironmentTest() {

    override fun runPythonDebuggerWithSourceFile(
        sourceFile: String,
        block: (evaluator: IPluginPyValueEvaluator, debugger: PythonEvalDebugger) -> Unit,
    ) {
        super.runPythonDebuggerWithSourceFile(sourceFile) { evaluator, debugger ->
            PythonPluginCodeInjector.injectIfRequired(evaluator, ::pluginCodeEscaper)
            block(evaluator, debugger)
        }
    }

    override fun runPythonDebuggerWithCodeSnippet(
        codeSnippet: String,
        block: (evaluator: IPluginPyValueEvaluator, debugger: PythonEvalDebugger) -> Unit,
    ) {
        super.runPythonDebuggerWithCodeSnippet(codeSnippet) { evaluator, debugger ->
            PythonPluginCodeInjector.injectIfRequired(evaluator, ::pluginCodeEscaper)
            block(evaluator, debugger)
        }
    }

    fun runPythonDebuggerWithoutPluginCode(
        block: (evaluator: IPluginPyValueEvaluator, debugger: PythonEvalDebugger) -> Unit,
    ) {
        super.runPythonDebuggerWithCodeSnippet("breakpoint()") { evaluator, debugger ->
            block(evaluator, debugger)
        }
    }

    protected fun pluginCodeEscaper(code: String): String {
        return code
            .replace("\\n", "\\\n")
            .replace("\\t", "\\\t")
    }
}