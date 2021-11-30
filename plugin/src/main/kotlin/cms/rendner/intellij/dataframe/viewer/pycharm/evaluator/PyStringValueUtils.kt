package cms.rendner.intellij.dataframe.viewer.pycharm.evaluator

fun convertStringifiedDictionary(dictionary: String?): Map<String, String> {
    if (dictionary == null) return emptyMap()
    return dictionary.removeSurrounding("{", "}")
        .split(", ")
        .associate { entry ->
            val separator = entry.indexOf(":")
            Pair(
                entry.substring(0, separator).removeSurrounding("'"),
                entry.substring(separator + 1).trim()
            )
        }
}