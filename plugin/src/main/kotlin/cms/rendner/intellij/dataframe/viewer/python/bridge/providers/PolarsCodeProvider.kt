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
import cms.rendner.intellij.dataframe.viewer.python.PythonQualifiedTypes
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import cms.rendner.intellij.dataframe.viewer.python.pycharm.PyDebugValueEvalExpr

class PolarsCodeProvider: ITableSourceCodeProvider {
    override fun getDataFrameLibrary() = DataFrameLibrary.POLARS

    override fun createSourceInfo(source: PyDebugValueEvalExpr, evaluator: IPluginPyValueEvaluator): DataSourceInfo {
        return DataSourceInfo(
            source,
            TableSourceFactoryImport(
                "cms_rendner_sdfv.polars.table_source_factory",
                "TableSourceFactory",
            ),
            hasIndexLabels = false,
            sortable = true,
            filterable = true,
        )
    }

    override fun getModulesDump(evaluator: IPluginPyValueEvaluator): String {
        val resourcePath = "/polars_x.y/plugin_modules_dump.json"
        return PolarsCodeProvider::class.java.getResource(resourcePath)!!.readText()
    }

    override fun isApplicable(fqClassName: String): Boolean {
        return fqClassName == "polars.dataframe.frame.DataFrame" || fqClassName == PythonQualifiedTypes.DICT
    }
}