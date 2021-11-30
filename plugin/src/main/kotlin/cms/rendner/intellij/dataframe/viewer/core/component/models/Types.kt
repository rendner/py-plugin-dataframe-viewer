package cms.rendner.intellij.dataframe.viewer.core.component.models

import com.intellij.openapi.Disposable
import java.awt.Color
import javax.swing.table.TableModel

sealed class Value {
    open fun text(): String {
        return ""
    }
}

data class StringValue(val value: String) : Value() {
    override fun text() = value
}

data class StyledValue(val value: String, val styles: StyleProperties) : Value() {
    override fun text() = value
}

interface IHeaderLabel {
    fun text(): String
}

data class HeaderLabel(val label: String = "") : IHeaderLabel {
    override fun text() = label

}

data class LeveledHeaderLabel(val lastLevel: String = "", val leadingLevels: List<String> = emptyList()) :
    IHeaderLabel {
    override fun text(): String {
        return text("/")
    }

    fun text(separator: String): String {
        val postfix = if (leadingLevels.isNotEmpty()) "$separator$lastLevel" else lastLevel
        return leadingLevels.joinToString(separator, postfix = postfix)
    }
}

data class LegendHeaders(val row: IHeaderLabel? = null, val column: IHeaderLabel? = null)

@Suppress("EnumEntryName")
enum class TextAlign {
    left,
    right,
    center
}

data class StyleProperties(
    val textColor: Color? = null,
    val backgroundColor: Color? = null,
    val textAlign: TextAlign? = TextAlign.center
) {
    fun isEmpty(): Boolean {
        return textColor == null && backgroundColor == null && textAlign == null
    }
}

interface ITableDataModel : TableModel {
    fun isLeveled(): Boolean
    fun shouldHideHeaders(): Boolean
    fun getLegendHeader(): IHeaderLabel
    fun getLegendHeaders(): LegendHeaders
}

interface ITableIndexDataModel : ITableDataModel {

    fun getValueAt(rowIndex: Int): IHeaderLabel

    @Deprecated(message = "use 'getValueAt(rowIndex)'", replaceWith = ReplaceWith("getValueAt(rowIndex)"))
    override fun getValueAt(rowIndex: Int, columnIndex: Int) = getValueAt(rowIndex)

    fun getColumnName(): String

    @Deprecated(message = "use 'getColumnName()'", replaceWith = ReplaceWith("getColumnName()"))
    override fun getColumnName(columnIndex: Int) = getLegendHeader().text()

    fun getColumnHeader(): IHeaderLabel
}

interface ITableValueDataModel : ITableDataModel {
    override fun getValueAt(rowIndex: Int, columnIndex: Int): Value
    fun getColumnHeaderAt(columnIndex: Int): IHeaderLabel
}

interface IDataFrameModel : Disposable {
    fun getValueDataModel(): ITableValueDataModel
    fun getIndexDataModel(): ITableIndexDataModel
}