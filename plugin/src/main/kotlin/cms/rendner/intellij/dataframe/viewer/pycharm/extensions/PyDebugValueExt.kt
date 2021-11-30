package cms.rendner.intellij.dataframe.viewer.pycharm.extensions

import cms.rendner.intellij.dataframe.viewer.pycharm.PythonQualifiedTypes
import com.jetbrains.python.debugger.PyDebugValue

fun PyDebugValue.isDataFrame(): Boolean = this.qualifiedType == "pandas.core.frame.DataFrame"
fun PyDebugValue.isStyler(): Boolean = this.qualifiedType == "pandas.io.formats.style.Styler"
fun PyDebugValue.isNone(): Boolean = qualifiedType == PythonQualifiedTypes.None.value
fun PyDebugValue.varName(): String {
    return if (this.name.startsWith("'")) {
        // like: "'styler' (1234567890)"
        this.name.substring(1, this.name.indexOf("'", 1))
    }
    // like "0" (for example index 0 in a tuple)
    else this.name
}