package cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.converter

// https://github.com/pandas-dev/pandas/blob/1.2.x/pandas/io/formats/style.py#L266-L272
enum class HeaderCssClasses(val value: String) {
    ROW_HEADING_CLASS("row_heading"),
    COL_HEADING_CLASS("col_heading"),
    INDEX_NAME_CLASS("index_name"),

    DATA_CLASS("data"),
    BLANK_CLASS("blank"),
    // don't use the def of "BLANK_VALUE" because it has changed between 1.2.x and 1.3.x
}

interface IndexTranslator {
    fun translate(index: Int): Int
}
class SequenceIndex(private val sequence: IntArray): IndexTranslator {
    override fun translate(index: Int) = sequence[index]
}
class OffsetIndex(private val offset: Int): IndexTranslator {
    override fun translate(index: Int) = offset + index
}
class NOOPTranslator: IndexTranslator {
    override fun translate(index: Int) = index
}
class RowColTranslator(
    val row: IndexTranslator,
    val column: IndexTranslator
)