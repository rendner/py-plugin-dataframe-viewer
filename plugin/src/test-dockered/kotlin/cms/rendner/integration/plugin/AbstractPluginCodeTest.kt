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
package cms.rendner.integration.plugin

import cms.rendner.debugger.AbstractPipEnvEnvironmentTest
import cms.rendner.debugger.impl.IPythonDebuggerApi
import cms.rendner.intellij.dataframe.viewer.python.bridge.*
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.pandas.PatchedStylerCodeProvider
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.TableSourceCodeProviderRegistry
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.TableSourceFactoryImport
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.pandas.DataFrameCodeProvider
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator

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
            registerCodeProviders(debuggerApi.evaluator)
            block(debuggerApi)
        }
    }

    override fun createPythonDebuggerWithCodeSnippet(
        codeSnippet: String,
        block: (debuggerApi: IPythonDebuggerApi) -> Unit,
    ) {
        super.createPythonDebuggerWithCodeSnippet(codeSnippet) { debuggerApi ->
            registerCodeProviders(debuggerApi.evaluator)
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

    protected fun registerCodeProviders(evaluator: IPluginPyValueEvaluator) {
        TableSourceCodeProviderRegistry.getProviders().forEach { PythonPluginCodeInjector.injectIfRequired(evaluator, it) }
    }

    protected fun createPatchedStyler(
        evaluator: IPluginPyValueEvaluator,
        dataSourceExpr: String,
        config: CreateTableSourceConfig? = null,
        ): IPyPatchedStylerRef {
        val patchedStyler = createTableSource(evaluator, PatchedStylerCodeProvider().getFactoryImport(), dataSourceExpr, config)
        check(patchedStyler is IPyPatchedStylerRef)
        return patchedStyler
    }

    protected fun createTableSource(
        evaluator: IPluginPyValueEvaluator,
        dataSourceExpr: String,
        config: CreateTableSourceConfig? = null,
    ): IPyTableSourceRef {
        val tableSource = createTableSource(evaluator, DataFrameCodeProvider().getFactoryImport(), dataSourceExpr, config)
        check(tableSource !is IPyPatchedStylerRef)
        return tableSource
    }

    protected fun createTableSource(
        evaluator: IPluginPyValueEvaluator,
        tableSourceFactory: TableSourceFactoryImport,
        dataSourceExpr: String,
        config: CreateTableSourceConfig? = null,
        ): IPyTableSourceRef {
        return TableSourceFactory.create(evaluator, tableSourceFactory, dataSourceExpr, config)
    }
}