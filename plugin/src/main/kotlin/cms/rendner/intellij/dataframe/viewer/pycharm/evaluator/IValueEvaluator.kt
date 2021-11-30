package cms.rendner.intellij.dataframe.viewer.pycharm.evaluator

import cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.exceptions.EvaluateException
import com.jetbrains.python.debugger.PyDebugValue

interface IValueEvaluator {
    @Throws(EvaluateException::class)
    fun evaluate(expression: String): PyDebugValue

    @Throws(EvaluateException::class)
    fun execute(expression: String): PyDebugValue
}