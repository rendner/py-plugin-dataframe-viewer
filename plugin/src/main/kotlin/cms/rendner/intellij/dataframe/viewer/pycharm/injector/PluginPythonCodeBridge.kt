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
import cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.ValueEvaluator
import cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.exceptions.EvaluateException
import cms.rendner.intellij.dataframe.viewer.pycharm.extensions.isDisconnectException
import com.intellij.openapi.diagnostic.Logger
import com.jetbrains.python.debugger.PyDebugValue

class PluginPythonCodeBridge {

    companion object {
        private val logger = Logger.getInstance(PluginPythonCodeBridge::class.java)
    }

    private val codeInjector = PluginPythonCodeInjector()

    fun createPatchedStyler(frameOrStyler: PyDebugValue): PyPatchedStylerRef {
        val evaluator = ValueEvaluator(frameOrStyler.frameAccessor)
        codeInjector.ensurePluginCodeIsInjected(evaluator)

        val patchedStyler = evaluator.evaluate(
            "${codeInjector.getBridgeExpr()}.create_patched_styler(${frameOrStyler.evaluationExpression})"
        )
        return PyPatchedStylerRef(
            patchedStyler
        ) { eval: IValueEvaluator, pyValueRefExpr: String -> disposePatchedStylerRef(eval, pyValueRefExpr) }
    }

    private fun disposePatchedStylerRef(evaluator: IValueEvaluator, pythonValueRefExpr: String) {
        try {
            evaluator.evaluate("${codeInjector.getBridgeExpr()}.delete_patched_styler(${pythonValueRefExpr})")
        } catch (ignore: EvaluateException) {
            if(ignore.cause?.isDisconnectException() == false) {
                logger.warn("Dispose PatchedStylerRef failed.", ignore)
            }
        }
    }
}
