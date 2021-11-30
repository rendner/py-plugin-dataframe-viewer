package cms.rendner.intellij.dataframe.viewer.pycharm.extensions

import com.jetbrains.python.debugger.PyDebuggerException

fun PyDebuggerException.isDisconnectException(): Boolean {
    return message == "Disconnected"
}