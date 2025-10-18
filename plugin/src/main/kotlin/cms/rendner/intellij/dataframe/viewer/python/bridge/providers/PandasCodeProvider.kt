/*
 * Copyright 2021-2026 cms.rendner (Daniel Schmidt)
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
import cms.rendner.intellij.dataframe.viewer.python.PandasTypes
import cms.rendner.intellij.dataframe.viewer.python.PythonQualifiedTypes
import cms.rendner.intellij.dataframe.viewer.python.bridge.PandasVersion
import cms.rendner.intellij.dataframe.viewer.python.bridge.exceptions.InjectException
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyFrameAccessor
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import cms.rendner.intellij.dataframe.viewer.python.pycharm.PyDebugValueEvalExpr

private data class MinorRange(val earliest: Int, val latest: Int) {
    fun getMatchingOrLatestMinor(minor: Int): Int? {
        return when {
            minor >= latest -> latest
            minor >= earliest -> minor
            else -> null
        }
    }
}

private val SUPPORTED_VERSIONS = mapOf(
    1 to MinorRange(1, 5),
    2 to MinorRange(0, 3),
)

class PandasCodeProvider : ITableSourceCodeProvider {
    override fun getDataFrameLibrary() = DataFrameLibrary.PANDAS

    override fun createSourceInfo(source: PyDebugValueEvalExpr): DataSourceInfo {
        return DataSourceInfo(
            source,
            if (PandasTypes.isStyler(source.qualifiedType)) getPatchedStylerFactoryImport() else getTableSourceFactoryImport(),
            getDataFrameLibrary(),
            hasIndexLabels = true,
            sortable = true,
            filterable = true,
        )
    }

    override fun isApplicable(fqClassName: String): Boolean {
        return PandasTypes.isStyler(fqClassName) ||
                PandasTypes.isDataFrame(fqClassName) ||
                fqClassName == PythonQualifiedTypes.DICT
    }

    override fun getModulesDump(frameAccessor: IPluginPyFrameAccessor): String {
        val pandasVersion = try {
            PandasVersion.fromString(frameAccessor.evaluate("__import__('pandas').__version__").forcedValue)
        } catch (ex: EvaluateException) {
            throw InjectException("Failed to identify version of pandas.", ex)
        }

        val resourcePath = getPluginCodeResourcePath(pandasVersion)
        return PandasCodeProvider::class.java.getResource(resourcePath)!!.readText()
    }

    private fun getTableSourceFactoryImport(): TableSourceFactoryImport {
        return TableSourceFactoryImport(
            "cms_rendner_sdfv.pandas.frame.table_source_factory",
            "TableSourceFactory",
        )
    }

    private fun getPatchedStylerFactoryImport(): TableSourceFactoryImport {
        return TableSourceFactoryImport(
            "cms_rendner_sdfv.pandas.styler.patched_styler_factory",
            "PatchedStylerFactory",
        )
    }

    private fun getPluginCodeResourcePath(version: PandasVersion): String {
        // Allowing to use latest supported minor versions for newer versions
        // can result in compatibility problems. In the future, viewer could
        // visualize that it uses an outdated minor version (maybe include info also in errors)
        val minor = SUPPORTED_VERSIONS[version.major]?.getMatchingOrLatestMinor(version.minor)
            ?: throw InjectException("Unsupported $version.")
        return "/generated_dumps/pandas_${version.major}.${minor}/plugin_modules_dump.json"
    }
}
