package cms.rendner.intellij.dataframe.viewer.pycharm.injector.snippets.major1.minor1

import cms.rendner.intellij.dataframe.viewer.pycharm.injector.IPandasCodeProvider
import cms.rendner.intellij.dataframe.viewer.pycharm.injector.snippets.major1.minor3.PandasCodeProvider

class PandasCodeProvider : IPandasCodeProvider {

    override fun getMajorMinorVersion() = "1.1"

    override fun getCode(): String {
        return PandasCodeProvider::class.java.getResource("/pandas_1.1/plugin_code")!!.readText()
    }
}