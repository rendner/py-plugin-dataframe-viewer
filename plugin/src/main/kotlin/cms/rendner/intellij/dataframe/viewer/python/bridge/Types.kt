/*
 * Copyright 2021 cms.rendner (Daniel Schmidt)
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
package cms.rendner.intellij.dataframe.viewer.python.bridge

import cms.rendner.intellij.dataframe.viewer.models.chunked.TableStructure
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import com.intellij.openapi.Disposable

data class PandasVersion(val major: Int, val minor: Int, val patch: String = "") {
    companion object {
        fun fromString(value: String): PandasVersion {
            val parts = value.split(".")
            return PandasVersion(
                parts[0].toInt(),
                parts[1].toInt(),
                value.substring(parts[0].length + parts[1].length + 2)
            )
        }
    }
}

class PythonCodeProvider(
    val version: PandasVersion,
    private val codeResourcePath: String,
) {
    fun getCode(): String {
        return PythonCodeProvider::class.java.getResource(codeResourcePath)!!.readText()
    }
}

interface IPyPatchedStylerRef : Disposable {
    @Throws(EvaluateException::class)
    fun evaluateTableStructure(): TableStructure

    @Throws(EvaluateException::class)
    fun evaluateRenderChunk(
        firstRow: Int,
        firstColumn: Int,
        numberOfRows: Int,
        numberOfColumns: Int,
        excludeRowHeader: Boolean,
        excludeColumnHeader: Boolean
    ): String

    @Throws(EvaluateException::class)
    fun evaluateRenderUnpatched(): String
}