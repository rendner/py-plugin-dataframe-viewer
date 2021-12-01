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
package cms.rendner.intellij.dataframe.viewer.pycharm.injector

import cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.IValueEvaluator
import cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.exceptions.EvaluateException
import cms.rendner.intellij.dataframe.viewer.pycharm.injector.exceptions.InjectException
import cms.rendner.intellij.dataframe.viewer.pycharm.injector.snippets.common.pythonGetPandasVersion
import cms.rendner.intellij.dataframe.viewer.pycharm.injector.snippets.common.pythonStyledDataFrameViewerBridgeClass
import com.google.common.collect.ImmutableList
import com.intellij.openapi.diagnostic.Logger

class PluginPythonCodeInjector {

    companion object {
        private val logger = Logger.getInstance(PluginPythonCodeInjector::class.java)

        // use a "dunder name" (name with double underscore) for the module name, to be listed under "special var" in the debugger
        private const val NAMESPACE_NAME = "__styled_data_frame_plugin__"
    }

    @Throws(InjectException::class)
    fun ensurePluginCodeIsInjected(evaluator: IValueEvaluator) {
        if (!isInitialized(evaluator)) {
            val version = getPandasVersion(evaluator)
            logger.info("inject code for version: $version")
            val pandasCodeProvider = createMatchingCodeProvider(version)
                ?: throw InjectException("Unsupported $version.")
            logger.info("use code provider for version: ${pandasCodeProvider.getMajorMinorVersion()}")
            injectCodeIntoNamespace(evaluator, pandasCodeProvider)
        }
    }

    fun getBridgeExpr(): String {
        return "$NAMESPACE_NAME.get('StyledDataFrameViewerBridge')"
    }

    private fun isInitialized(evaluator: IValueEvaluator): Boolean {
        val isInitialized = try {
            evaluator.evaluate("${getBridgeExpr()}.check()")
        } catch (ignore: EvaluateException) {
            null
        }

        return isInitialized?.value == "True"
    }

    private fun createMatchingCodeProvider(version: PandasVersion): IPandasCodeProvider? {
        if (version.major == 1) {
            if (version.minor == 1) {
                return cms.rendner.intellij.dataframe.viewer.pycharm.injector.snippets.major1.minor1.PandasCodeProvider()
            }
            if (version.minor == 2) {
                return cms.rendner.intellij.dataframe.viewer.pycharm.injector.snippets.major1.minor2.PandasCodeProvider()
            }
            if (version.minor == 3) {
                return cms.rendner.intellij.dataframe.viewer.pycharm.injector.snippets.major1.minor3.PandasCodeProvider()
            }
        }

        return null
    }

    @Throws(InjectException::class)
    private fun injectCodeIntoNamespace(evaluator: IValueEvaluator, pandasCodeProvider: IPandasCodeProvider) {
        try {
            val codeSnippets = ImmutableList.of(
                pandasCodeProvider.getCode(),
                pythonStyledDataFrameViewerBridgeClass
            )
            val createGlobalsExpr = "$NAMESPACE_NAME = {}"
            val execExpr = "exec('''${codeSnippets.joinToString("", "\n", "\n\n") { it }}''', $NAMESPACE_NAME)"
            evaluator.execute("$createGlobalsExpr\n$execExpr")
        } catch (ex: EvaluateException) {
            throw InjectException("Inject plugin classes failed.", ex)
        }
    }

    @Throws(InjectException::class)
    private fun getPandasVersion(evaluator: IValueEvaluator): PandasVersion {
        try {
            val pandasImport = "import pandas as pd"
            evaluator.execute(pandasImport)
            val evaluateResult = evaluator.evaluate(pythonGetPandasVersion)
            return PandasVersion.fromString(evaluateResult.value!!)
        } catch (ex: EvaluateException) {
            throw InjectException("Inject plugin classes failed.", ex)
        }
    }

    private data class PandasVersion(val major: Int, val minor: Int, val patch: String) {
        companion object {
            fun fromString(value: String): PandasVersion {
                val parts = value.split(".")
                return PandasVersion(
                    parts[0].toInt(),
                    parts[1].toInt(),
                    value.substring(parts[0].length + parts[1].length + 2)
                )
            }
        }
    }
}