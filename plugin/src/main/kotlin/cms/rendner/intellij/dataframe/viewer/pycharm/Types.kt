package cms.rendner.intellij.dataframe.viewer.pycharm

enum class PythonQualifiedTypes(val value: String) {
    None("builtins.NoneType"),
    List("builtins.list"),
    Tuple("builtins.tuple"),
    Dict("builtins.dict"),
    Function("builtins.function"),
    Int("builtins.int"),
    Float("builtins.float"),
    Float64("numpy.float64"),
    Index("pandas.core.indexes.base.Index"),
    Module("builtins.module")
}