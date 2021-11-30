package cms.rendner.intellij.dataframe.viewer.pycharm.injector

interface IPandasCodeProvider {
    fun getMajorMinorVersion(): String
    fun getCode(): String
}