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
package cms.rendner.intellij.dataframe.viewer.python

import cms.rendner.intellij.dataframe.viewer.python.utils.parsePythonDictionary
import cms.rendner.intellij.dataframe.viewer.python.utils.stringifyString

object PythonQualifiedTypes {
    const val NONE = "builtins.NoneType"
    const val LIST = "builtins.list"
    const val TUPLE = "builtins.tuple"
    const val DICT = "builtins.dict"
    const val FUNCTION = "builtins.function"
    const val INT = "builtins.int"
    const val FLOAT = "builtins.float"
    const val MODULE = "builtins.module"
}

object PandasTypes {
    fun isDataFrame(qualifiedType: String?): Boolean = qualifiedType == "pandas.core.frame.DataFrame"
    fun isStyler(qualifiedType: String?): Boolean = qualifiedType == "pandas.io.formats.style.Styler"
}

data class DataFrameLibrary(val moduleName: String) {
    companion object {
        val PANDAS = DataFrameLibrary("pandas")
        val POLARS = DataFrameLibrary("polars")

        val supportedLibraries = listOf(
            PANDAS,
            POLARS,
        )
    }
}

interface IEvalAvailableDataFrameLibraries {
    fun getEvalExpression(): String {
        val libsToCheck = DataFrameLibrary.supportedLibraries.map { stringifyString(it.moduleName) }
        // the check is not guarded with a try/catch therefore an exception aborts the whole check
        return "(lambda i, s: {p: p in s.modules or i.util.find_spec(p) is not None for p in $libsToCheck})(__import__('importlib.util'), __import__('sys'))"
    }

    fun convertResult(result: String): List<DataFrameLibrary> {
        return parsePythonDictionary(result).entries.mapNotNull {
            if (it.value == "True") DataFrameLibrary(it.key) else null
        }
    }
}