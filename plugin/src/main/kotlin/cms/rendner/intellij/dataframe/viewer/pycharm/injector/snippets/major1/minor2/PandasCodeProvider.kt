package cms.rendner.intellij.dataframe.viewer.pycharm.injector.snippets.major1.minor2

import cms.rendner.intellij.dataframe.viewer.pycharm.injector.IPandasCodeProvider

class PandasCodeProvider : IPandasCodeProvider {

    override fun getMajorMinorVersion() = "1.2"

    override fun getCode(): String {
        return IPandasCodeProvider::class.java.getResource("/pandas_1.2/plugin_code")!!.readText()
    }
}