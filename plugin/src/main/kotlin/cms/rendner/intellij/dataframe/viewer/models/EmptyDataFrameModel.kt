/*
 * Copyright 2021-2025 cms.rendner (Daniel Schmidt)
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
    private val myEmptyIndexModel: IDataFrameIndexDataModel = MyEmptyIndexModel()
    private val myEmptyValueModel: IDataFrameValuesDataModel = MyEmptyValuesModel()

    override fun getValuesDataModel() = myEmptyValueModel

    override fun getIndexDataModel() = myEmptyIndexModel

    override fun getFingerprint() = null

    override fun dispose() {}
}

private class MyEmptyValuesModel : AbstractTableModel(), IDataFrameValuesDataModel {

    override fun getRowCount() = 0
    override fun getColumnCount() = 0
    override fun enableDataFetching(enabled: Boolean) {}

    override fun getValueAt(rowIndex: Int, columnIndex: Int): String {
        throw UnsupportedOperationException("Operation 'getValueAt' isn't supported.")
    }

    override fun getCellMetaAt(rowIndex: Int, columnIndex: Int): String? {
        throw UnsupportedOperationException("Operation 'getCellMetaAt' isn't supported.")
    }

    override fun getColumnLabelAt(columnIndex: Int): IHeaderLabel {
        throw UnsupportedOperationException("Operation 'getColumnLabelAt' isn't supported.")
    }

    override fun getColumnDtypeAt(columnIndex: Int): String {
        throw UnsupportedOperationException("Operation 'getColumnDtypeAt' isn't supported.")
    }

    override fun getColumnStatisticsAt(columnIndex: Int): Map<String, String>? {
        throw UnsupportedOperationException("Operation 'getColumnStatisticsAt' isn't supported.")
    }

    override fun setSortKeys(sortKeys: List<RowSorter.SortKey>) {
        throw UnsupportedOperationException("Operation 'setSortKeys' isn't supported.")
    }

    override fun getColumnName(columnIndex: Int): String {
        throw UnsupportedOperationException("Operation 'getColumnName' isn't supported.")
    }

    override fun getLegendHeader(): IHeaderLabel {
        throw UnsupportedOperationException("Operation 'getLegendHeader' isn't supported.")
    }
}

private class MyEmptyIndexModel : AbstractTableModel(), IDataFrameIndexDataModel {

    override fun getRowCount() = 0
    override fun getColumnCount() = 0
    override fun getColumnName(columnIndex: Int) = getColumnName()

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