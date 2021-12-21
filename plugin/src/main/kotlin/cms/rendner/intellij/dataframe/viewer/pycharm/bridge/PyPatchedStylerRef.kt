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
package cms.rendner.intellij.dataframe.viewer.pycharm.bridge

import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.TableStructure
import cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.ValueEvaluator
import cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.convertStringifiedDictionary
import cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.exceptions.EvaluateException
import com.intellij.openapi.Disposable
import com.jetbrains.python.debugger.PyDebugValue

/**
 * Wraps an instance of the "PatchedStyler" Python class.
 * All calls are forwarded to the passed instance. Therefore, the method signatures of
 * the Python class "PatchedStyler" have to match with the ones used by this class.
 *
 * @param pythonValueRef has to be a "PatchedStyler" Python instance.
 */
class PyPatchedStylerRef(
    pythonValueRef: PyDebugValue,
    private val disposeCallback: (ValueEvaluator, String) -> Unit
) : Disposable {

    private val pythonValueRefExpr = pythonValueRef.evaluationExpression
    private val evaluator = ValueEvaluator(pythonValueRef.frameAccessor)
    private var disposed = false

    @Throws(EvaluateException::class)
    fun evaluateTableStructure(): TableStructure {
        val evalResult = evaluator.evaluate("str(${pythonValueRefExpr}.get_table_structure().__dict__)")
        val propsMap = convertStringifiedDictionary(evalResult.value)

        val visibleRowsCount = propsMap["visible_rows_count"]?.toInt() ?: 0

        return TableStructure(
            rowsCount = propsMap["rows_count"]?.toInt() ?: 0,
            columnsCount = propsMap["columns_count"]?.toInt() ?: 0,
            visibleRowsCount = visibleRowsCount,
            // if we have no rows we interpret the DataFrame as empty - therefore no columns
            visibleColumnsCount = if (visibleRowsCount > 0) propsMap["visible_columns_count"]?.toInt() ?: 0 else 0,
            rowLevelsCount = propsMap["row_levels_count"]?.toInt() ?: 0,
            columnLevelsCount = propsMap["column_levels_count"]?.toInt() ?: 0,
            hideRowHeader = propsMap["hide_row_header"] == "True",
            hideColumnHeader = propsMap["hide_column_header"] == "True"
        )
    }

    @Throws(EvaluateException::class)
    fun evaluateRenderChunk(
        firstRow: Int,
        firstColumn: Int,
        lastRow: Int,
        lastColumn: Int,
        excludeRowHeader: Boolean,
        excludeColumnHeader: Boolean
    ): String {
        return evaluator.evaluate(
            "${pythonValueRefExpr}.render_chunk($firstRow, $firstColumn, $lastRow, $lastColumn, ${pythonBool(excludeRowHeader)}, ${pythonBool(excludeColumnHeader)})"
        ).value!!
    }

    @Throws(EvaluateException::class)
    fun evaluateRenderUnpatched(): String {
        // Note:
        // Each time this method is called on the same instance style-properties are re-created without
        // clearing previous ones. Calling this method n-times results in n-times duplicated properties.
        // At least this is the behaviour in pandas 1.2.0 and looks like a bug in pandas.
        return evaluator.evaluate("${pythonValueRefExpr}.render_unpatched()").value!!
    }

    override fun dispose() {
        if (!disposed) {
            disposed = true
            disposeCallback(evaluator, pythonValueRefExpr)
        }
    }

    private fun pythonBool(value: Boolean): String {
        return if (value) "True" else "False"
    }
}