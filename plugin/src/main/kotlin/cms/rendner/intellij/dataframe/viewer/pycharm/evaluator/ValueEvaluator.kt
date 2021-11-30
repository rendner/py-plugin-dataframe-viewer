package cms.rendner.intellij.dataframe.viewer.pycharm.evaluator

import cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.exceptions.EvaluateException
import com.jetbrains.python.debugger.PyDebugValue
import com.jetbrains.python.debugger.PyDebuggerException
import com.jetbrains.python.debugger.PyFrameAccessor

class ValueEvaluator(private val frameAccessor: PyFrameAccessor): IValueEvaluator {

    @Throws(EvaluateException::class)
    override fun evaluate(expression: String): PyDebugValue {
        val result = try {
            frameAccessor.evaluate(expression, false, false)
        } catch (ex: PyDebuggerException) {
            throw EvaluateException("Couldn't evaluate expression.", ex, expression)
        }
        if (result.isErrorOnEval) {
            throw EvaluateException(result.value ?: "Couldn't evaluate expression.", expression)
        }
        return result
    }

    @Throws(EvaluateException::class)
    override fun execute(expression: String): PyDebugValue {
        try {
            return frameAccessor.evaluate(expression, true, false)
        } catch (ex: PyDebuggerException) {
            throw EvaluateException("Couldn't evaluate expression.", ex, expression)
        }
    }
}