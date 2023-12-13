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

import cms.rendner.TestProperty
import cms.rendner.debugger.AbstractPipEnvEnvironmentTest
import cms.rendner.debugger.impl.IPythonDebuggerApi
import cms.rendner.intellij.dataframe.viewer.python.bridge.*
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.ITableSourceCodeProvider
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.TableSourceCodeProviderRegistry
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.PandasCodeProvider
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
        val availableFrameLibs = TestProperty.getDataFrameLibraries()
        TableSourceCodeProviderRegistry.getProviders().values
            .filter { availableFrameLibs.contains(it.getDataFrameLibrary()) }
            .forEach { PythonPluginCodeInjector.injectIfRequired(evaluator, it) }
    }

    protected inline fun <reified T: IPyTableSourceRef>createPandasTableSource(
        evaluator: IPluginPyValueEvaluator,
        dataSourceExpr: String,
        config: CreateTableSourceConfig? = null,
    ): T {
        val tableSource = createTableSource(evaluator, dataSourceExpr, PandasCodeProvider(), config)
        check(tableSource is T)
        return tableSource
    }

    protected fun createTableSource(
        evaluator: IPluginPyValueEvaluator,
        dataSourceExpr: String,
        codeProvider: ITableSourceCodeProvider,
        config: CreateTableSourceConfig? = null,
    ): IPyTableSourceRef {
        val evalExpr = evaluator.evaluate(dataSourceExpr).toValueEvalExpr()
        val dataSourceInfo = codeProvider.createSourceInfo(evalExpr, evaluator)
        val tableSource = TableSourceFactory.create(
            evaluator,
            dataSourceInfo.tableSourceFactoryImport,
            dataSourceInfo.source.currentStackFrameRefExpr,
            config,
        )
        return tableSource
    }
}