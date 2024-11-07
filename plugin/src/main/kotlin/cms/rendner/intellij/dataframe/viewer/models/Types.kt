/*
 * Copyright 2021-2024 cms.rendner (Daniel Schmidt)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cms.rendner.intellij.dataframe.viewer.models

import com.intellij.openapi.Disposable
import java.awt.Color
import javax.swing.RowSorter.SortKey
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

data class ColumnHeader(val dtype: String?, val label: IHeaderLabel, val describe: Map<String, String>? = null) : IHeaderLabel {
    override fun text() = label.text()
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
    val textAlign: TextAlign? = null,
) {
    fun isEmpty(): Boolean {
        return textColor == null && backgroundColor == null && textAlign == null
    }
}

interface ITableDataModel : TableModel {
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
    fun getColumnHeaderAt(columnIndex: Int): ColumnHeader
    fun isSortable(): Boolean = false

    /**
     * Sets the sort keys.
     * If the sort keys have changed this triggers a sort.
     *
     * @throws [IllegalStateException] if model is not sortable
     */
    fun setSortKeys(sortKeys: List<SortKey>)

    /**
     * Returns a unique column-id for the column in the table source.
     * This id has to be stable and independent of the actual index of the column (index could change when columns are filtered out).
     * Same column should always return the same id. The id is used to restore the state (width, sort-state, etc.) of a column.
     *
     * @return the unique column-id in the table source or "-1" if the table source can't provide stable unique ids.
     */
    fun getUniqueColumnId(columnIndex: Int) = columnIndex

    /**
     * Enables or disables data fetching.
     * Data fetching should only be temporarily enabled during the painting process of a table.
     *
     * JTables instantiate renderers to measure all kind of things.
     * Values for these renderers are read from the model of the table.
     * In case of an async table model, the data has to be fetched from an underlying data source.
     * Such a model usually returns fallback values (maybe empty string) until the data is loaded.
     * To not trigger fetching of data during internal measuring, the data fetching should be
     * disabled most of the time.
     *
     * @param enabled indicates if data fetching should be enabled or not.
     */
    fun enableDataFetching(enabled: Boolean)
}

interface IDataFrameModel : Disposable {
    /**
     * Returns the model which provides the cell values and column labels.
     */
    fun getValueDataModel(): ITableValueDataModel

    /**
     * Returns the model which provides the index labels.
     * null if no index labels should be displayed.
     */
    fun getIndexDataModel(): ITableIndexDataModel?

    /**
     * Fingerprint of the model.
     * The fingerprint is used to check if two models represent the same DataFrame when exchanging the model.
     * If null is returned, no check is performed.
     */
    fun getFingerprint(): String?
}