/*
 * Copyright 2021-2023 cms.rendner (Daniel Schmidt)
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

import javax.swing.RowSorter
import javax.swing.table.AbstractTableModel

class EmptyDataFrameModel : IDataFrameModel {
    private val myEmptyIndexModel: ITableIndexDataModel = MyEmptyIndexModel()
    private val myEmptyValueModel: ITableValueDataModel = MyEmptyValueModel()

    override fun getValueDataModel() = myEmptyValueModel

    override fun getIndexDataModel() = myEmptyIndexModel

    override fun getDataSourceFingerprint() = null

    override fun dispose() {}
}

private class MyEmptyValueModel : AbstractTableModel(), ITableValueDataModel {

    override fun getRowCount() = 0
    override fun getColumnCount() = 0
    override fun enableDataFetching(enabled: Boolean) {}

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Value {
        throw UnsupportedOperationException("Operation 'getValueAt' isn't supported.")
    }

    override fun getColumnHeaderAt(columnIndex: Int): IHeaderLabel {
        throw UnsupportedOperationException("Operation 'getColumnHeaderAt' isn't supported.")
    }

    override fun setSortKeys(sortKeys: List<RowSorter.SortKey>) {
        throw UnsupportedOperationException("Operation 'setSortKeys' isn't supported.")
    }

    override fun convertToFrameColumnIndex(columnIndex: Int): Int {
        throw UnsupportedOperationException("Operation 'convertToFrameColumnIndex' isn't supported.")
    }

    override fun getColumnName(columnIndex: Int): String {
        throw UnsupportedOperationException("Operation 'getColumnName' isn't supported.")
    }

    override fun getLegendHeader(): IHeaderLabel {
        throw UnsupportedOperationException("Operation 'getLegendHeader' isn't supported.")
    }

    override fun getLegendHeaders(): LegendHeaders {
        throw UnsupportedOperationException("Operation 'getLegendHeaders' isn't supported.")
    }
}

private class MyEmptyIndexModel : AbstractTableModel(), ITableIndexDataModel {

    override fun getRowCount() = 0
    override fun getColumnCount() = 0
    override fun getColumnName(columnIndex: Int) = getColumnName()
    override fun enableDataFetching(enabled: Boolean) {}

    override fun getValueAt(rowIndex: Int): IHeaderLabel {
        throw UnsupportedOperationException("Operation 'getValueAt' isn't supported.")
    }

    override fun getColumnHeader(): IHeaderLabel {
        throw UnsupportedOperationException("Operation 'getColumnHeader' isn't supported.")
    }

    override fun getColumnName(): String {
        throw UnsupportedOperationException("Operation 'getColumnName' isn't supported.")
    }

    override fun getLegendHeader(): IHeaderLabel {
        throw UnsupportedOperationException("Operation 'getLegendHeader' isn't supported.")
    }

    override fun getLegendHeaders(): LegendHeaders {
        throw UnsupportedOperationException("Operation 'getLegendHeaders' isn't supported.")
    }
}