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
