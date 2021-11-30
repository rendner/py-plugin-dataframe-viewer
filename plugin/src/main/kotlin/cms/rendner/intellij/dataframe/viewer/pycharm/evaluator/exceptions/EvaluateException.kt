package cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.exceptions

import com.jetbrains.python.debugger.PyDebuggerException
import java.lang.StringBuilder

class EvaluateException : Exception {

    private val expression: String

    constructor(message: String, cause: PyDebuggerException, expression: String) : super(message, cause) {
        this.expression = expression
    }

    override val cause: PyDebuggerException?
        get() = super.cause as PyDebuggerException?

    constructor(message: String, expression: String) : super(message) {
        this.expression = expression
    }

    override fun getLocalizedMessage(): String {
        val msg = super.getLocalizedMessage()
        return cause?.let {
            return "$msg ${it.localizedMessage}"
        } ?: msg
    }

    fun userFriendlyMessage(): String {
        return this.message ?: "Couldn't evaluate expression."
    }

    fun logMessage() = StringBuilder()
        .appendLine(message)
        .appendLine("\texpression: '${expression}'")
        .toString()
}