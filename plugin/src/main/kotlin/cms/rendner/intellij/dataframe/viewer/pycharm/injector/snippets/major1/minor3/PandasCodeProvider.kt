package cms.rendner.intellij.dataframe.viewer.pycharm.injector.snippets.major1.minor3

import cms.rendner.intellij.dataframe.viewer.pycharm.injector.IPandasCodeProvider

class PandasCodeProvider : IPandasCodeProvider {

    override fun getMajorMinorVersion() = "1.3"

    override fun getCode(): String {
        return PandasCodeProvider::class.java.getResource("/pandas_1.3/plugin_code")!!.readText()
    }
}