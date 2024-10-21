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
package cms.rendner.intellij.dataframe.viewer.python.bridge.providers

import cms.rendner.intellij.dataframe.viewer.python.DataFrameLibrary
import cms.rendner.intellij.dataframe.viewer.python.bridge.IPyTableSourceRef
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import cms.rendner.intellij.dataframe.viewer.python.pycharm.PyDebugValueEvalExpr

enum class TableSourceKind {
    TABLE_SOURCE,
    PATCHED_STYLER,
}

/**
 * Contains information to import a Python TableSourceFactory.
 * The [packageName] and [className] are required to build a full qualified import.
 *
 * @param packageName the package name of the factory
 * @param className the class name of the factory
 * @param tableSourceKind the kind of the table source created by the factory.
 * Can be specified if a factory always returns the same kind or the kind is known upfront.
 */
data class TableSourceFactoryImport(
    val packageName: String,
    val className: String,
    val tableSourceKind: TableSourceKind? = null,
)

/**
 *
 * @param source the source for a [IPyTableSourceRef]
 * @param tableSourceFactoryImport the factory to create a [IPyTableSourceRef] on Python side
 * @param hasIndexLabels true if table has index labels
 * @param sortable true if table source can be sorted
 * @param filterable true if table source can be filtered
 */
data class DataSourceInfo(
    val source: PyDebugValueEvalExpr,
    val tableSourceFactoryImport: TableSourceFactoryImport,
    val hasIndexLabels: Boolean,
    val sortable: Boolean,
    val filterable: Boolean,
)

/**
 * A code provider provides code to extract table like data from a Python object (a data source).
 * A data source can be a normal Python dictionary or a more complex object like a pandas DataFrame.
 */
interface ITableSourceCodeProvider {
    /**
     * Returns the name of the data frame library supported by the code provider.
     */
    fun getDataFrameLibrary(): DataFrameLibrary

    /**
     * Creates a source info for the [PyDebugValueEvalExpr].
     *
     * The [evaluator] can be used to check for the shape (dimension) of the table source to create
     * to decide if it is sortable/filterable or not.
     *
     * @param source the source.
     * @param evaluator an evaluator to retrieve information from Python.
     */
    fun createSourceInfo(source: PyDebugValueEvalExpr, evaluator: IPluginPyValueEvaluator): DataSourceInfo

    /**
     * Returns the modules dump. The dump contains Python code required to fetch the table frame like data
     * from an underlying Python data source.
     *
     * The [evaluator] can be used to check for versions of installed Python modules.
     * The [evaluator] shouldn't be used to check:
     * - if the module dump was already injected
     * - to inject any additional code OR the plugin code itself
     *
     * @param evaluator an evaluator to retrieve information from Python.
     */
    fun getModulesDump(evaluator: IPluginPyValueEvaluator): String

    /**
     * Called to check if the provider could create a Python TableSource for a data source.
     *
     * @param fqClassName the full qualified class name of the data source
     */
    fun isApplicable(fqClassName: String): Boolean
}