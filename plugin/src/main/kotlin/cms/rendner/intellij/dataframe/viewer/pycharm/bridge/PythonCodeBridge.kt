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
package cms.rendner.intellij.dataframe.viewer.pycharm.bridge

import cms.rendner.intellij.dataframe.viewer.pycharm.bridge.exceptions.InjectException
import cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.IValueEvaluator
import cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.ValueEvaluator
import cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.exceptions.EvaluateException
import cms.rendner.intellij.dataframe.viewer.pycharm.extensions.isDisconnectException
import com.intellij.openapi.diagnostic.Logger
import com.jetbrains.python.debugger.PyDebugValue

/**
 * Creates [PyPatchedStylerRef] instances to enable "communication" with pandas objects.
 *
 * This class inserts the Python-specific plugin code that allows to interact with Pandas objects.
 * After the plugin code was inserted, this class calls methods of the "StyledDataFrameViewerBridge"
 * Python class. Therefore, the method signatures of the Python class "StyledDataFrameViewerBridge"
 * have to match with the ones used by this class.
 */
class PythonCodeBridge {

    companion object {
        private val logger = Logger.getInstance(PythonCodeBridge::class.java)

        // use a "dunder name" (name with double underscore) for the name, to be listed under "special var" in the debugger
        private const val NAMESPACE_NAME = "__styled_data_frame_plugin__"
    }

    // todo: check for required jinja2 installation -> add new "check_requirements" on python side

    fun getBridgeExpr(): String {
        return "$NAMESPACE_NAME.get('StyledDataFrameViewerBridge')"
    }

    fun createPatchedStyler(frameOrStyler: PyDebugValue): PyPatchedStylerRef {
        val evaluator = ValueEvaluator(frameOrStyler.frameAccessor)
        ensurePluginCodeIsInjected(evaluator)

        val patchedStyler = evaluator.evaluate(
            "${getBridgeExpr()}.create_patched_styler(${frameOrStyler.evaluationExpression})"
        )
        return PyPatchedStylerRef(
            patchedStyler
        ) { eval: IValueEvaluator, pyValueRefExpr: String -> disposePatchedStylerRef(eval, pyValueRefExpr) }
    }

    private fun disposePatchedStylerRef(evaluator: IValueEvaluator, pythonValueRefExpr: String) {
        try {
            evaluator.evaluate("${getBridgeExpr()}.delete_patched_styler(${pythonValueRefExpr})")
        } catch (ignore: EvaluateException) {
            if (ignore.cause?.isDisconnectException() == false) {
                logger.warn("Dispose PatchedStylerRef failed.", ignore)
            }
        }
    }

    @Throws(InjectException::class)
    private fun ensurePluginCodeIsInjected(evaluator: IValueEvaluator) {
        if (!isInitialized(evaluator)) {
            val version = getPandasVersion(evaluator)
            logger.info("inject code for version: $version")
            val pandasCodeProvider = PythonCodeProviderFactory.createProviderFor(version)
                ?: throw InjectException("Unsupported $version.")
            logger.info("use code provider for version: ${pandasCodeProvider.version}")
            injectCodeIntoNamespace(evaluator, pandasCodeProvider)
        }
    }

    @Throws(InjectException::class)
    private fun injectCodeIntoNamespace(evaluator: IValueEvaluator, pandasCodeProvider: PythonCodeProvider) {
        try {
            val createGlobalsExpr = "$NAMESPACE_NAME = {}"
            val execExpr = "exec('''${pandasCodeProvider.getCode()}''', $NAMESPACE_NAME)"
            evaluator.execute("$createGlobalsExpr\n$execExpr")
        } catch (ex: EvaluateException) {
            throw InjectException("Failed to inject python plugin code.", ex)
        }
    }

    private fun isInitialized(evaluator: IValueEvaluator): Boolean {
        val isInitialized = try {
            evaluator.evaluate("${getBridgeExpr()}.check()")
        } catch (ignore: EvaluateException) {
            null
        }

        return isInitialized?.value == "True"
    }

    @Throws(InjectException::class)
    private fun getPandasVersion(evaluator: IValueEvaluator): PandasVersion {
        try {
            val pandasImport = "import pandas as pd"
            evaluator.execute(pandasImport)
            val evaluateResult = evaluator.evaluate("pd.__version__")
            return PandasVersion.fromString(evaluateResult.value!!)
        } catch (ex: EvaluateException) {
            throw InjectException("Failed to determine pandas version.", ex)
        }
    }
}