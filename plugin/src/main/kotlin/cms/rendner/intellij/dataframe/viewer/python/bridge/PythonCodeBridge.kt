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
import cms.rendner.intellij.dataframe.viewer.python.bridge.PythonCodeBridge.PyPatchedStylerRef
import cms.rendner.intellij.dataframe.viewer.python.bridge.exceptions.InjectException
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import cms.rendner.intellij.dataframe.viewer.python.debugger.PluginPyValue
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import com.intellij.openapi.diagnostic.Logger

/**
 * Creates [PyPatchedStylerRef] instances to enable "communication" with pandas objects.
 *
 * This class inserts the Python-specific plugin code that allows to interact with Pandas objects.
 * After the plugin code was inserted, this class calls methods of the "StyledDataFrameViewerBridge"
 * Python class. Therefore, the method signatures of the Python class "StyledDataFrameViewerBridge"
 * have to match with the ones used by this class.
 */
class PythonCodeBridge {

    companion object {
        private val logger = Logger.getInstance(PythonCodeBridge::class.java)

        // use a "dunder name" (name with double underscore) for the name, to be listed under "special var" in the debugger
        private const val NAMESPACE_NAME = "__styled_data_frame_plugin__"
    }

    // todo: check for required jinja2 installation -> add new "check_requirements" on python side

    fun getBridgeExpr(): String {
        return "$NAMESPACE_NAME.get('StyledDataFrameViewerBridge')"
    }

    fun createPatchedStyler(frameOrStyler: PluginPyValue): IPyPatchedStylerRef {
        ensurePluginCodeIsInjected(frameOrStyler.evaluator)

        val patchedStyler = frameOrStyler.evaluator.evaluate(
            "${getBridgeExpr()}.create_patched_styler(${frameOrStyler.pythonRefEvalExpr})"
        )
        return PyPatchedStylerRef(
            patchedStyler,
            this::disposePatchedStylerRef,
        )
    }

    private fun disposePatchedStylerRef(patchedStylerRef: PyPatchedStylerRef) {
        try {
            patchedStylerRef.pythonValue.evaluator.evaluate(
                "${getBridgeExpr()}.delete_patched_styler(${patchedStylerRef.pythonValue.pythonRefEvalExpr})"
            )
        } catch (ignore: EvaluateException) {
            if (ignore.cause?.isDisconnectException() == false) {
                logger.warn("Dispose PatchedStylerRef failed.", ignore)
            }
        }
    }

    @Throws(InjectException::class)
    private fun ensurePluginCodeIsInjected(evaluator: IPluginPyValueEvaluator) {
        if (!isInitialized(evaluator)) {
            val version = getPandasVersion(evaluator)
            logger.info("inject code for version: $version")
            val pandasCodeProvider = PythonCodeProviderFactory.createProviderFor(version)
                ?: throw InjectException("Unsupported $version.")
            logger.info("use code provider for version: ${pandasCodeProvider.version}")
            injectCodeIntoNamespace(evaluator, pandasCodeProvider)
        }
    }

    @Throws(InjectException::class)
    private fun injectCodeIntoNamespace(evaluator: IPluginPyValueEvaluator, pandasCodeProvider: PythonCodeProvider) {
        try {
            val createGlobalsExpr = "$NAMESPACE_NAME = {}"
            val execExpr = "exec('''${pandasCodeProvider.getCode()}''', $NAMESPACE_NAME)"
            evaluator.execute("$createGlobalsExpr\n$execExpr")
        } catch (ex: EvaluateException) {
            throw InjectException("Failed to inject python plugin code.", ex)
        }
    }

    private fun isInitialized(evaluator: IPluginPyValueEvaluator): Boolean {
        val isInitialized = try {
            evaluator.evaluate("${getBridgeExpr()}.check()")
        } catch (ignore: EvaluateException) {
            null
        }

        return isInitialized?.value == "True"
    }

    @Throws(InjectException::class)
    private fun getPandasVersion(evaluator: IPluginPyValueEvaluator): PandasVersion {
        try {
            val pandasImport = "import pandas as pd"
            evaluator.execute(pandasImport)
            val evaluateResult = evaluator.evaluate("pd.__version__")
            return PandasVersion.fromString(evaluateResult.value!!)
        } catch (ex: EvaluateException) {
            throw InjectException("Failed to determine pandas version.", ex)
        }
    }

    private
    /**
     * Kotlin implementation of the "PatchedStyler" Python class.
     * All calls are forwarded to the referenced Python instance. Therefore, the method signatures of
     * the Python class "PatchedStyler" have to match with the ones used by this class.
     *
     * @param pythonValue the wrapped "PatchedStyler" Python instance.
     * @param disposeCallback called when [dispose] is called (max once).
     */
    class PyPatchedStylerRef(
        val pythonValue: PluginPyValue,
        private val disposeCallback: (PyPatchedStylerRef) -> Unit
    ) : IPyPatchedStylerRef {

        private var disposed = false

        @Throws(EvaluateException::class)
        override fun evaluateTableStructure(): TableStructure {
            val evalResult =
                pythonValue.evaluator.evaluate("str(${pythonValue.pythonRefEvalExpr}.get_table_structure().__dict__)")
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
        override fun evaluateRenderChunk(
            firstRow: Int,
            firstColumn: Int,
            lastRow: Int,
            lastColumn: Int,
            excludeRowHeader: Boolean,
            excludeColumnHeader: Boolean
        ): String {
            return pythonValue.evaluator.evaluate(
                "${pythonValue.pythonRefEvalExpr}.render_chunk($firstRow, $firstColumn, $lastRow, $lastColumn, ${
                    pythonBool(
                        excludeRowHeader
                    )
                }, ${pythonBool(excludeColumnHeader)})"
            ).value!!
        }

        @Throws(EvaluateException::class)
        override fun evaluateRenderUnpatched(): String {
            // Note:
            // Each time this method is called on the same instance style-properties are re-created without
            // clearing previous ones. Calling this method n-times results in n-times duplicated properties.
            // At least this is the behaviour in pandas 1.2.0 and looks like a bug in pandas.
            return pythonValue.evaluator.evaluate("${pythonValue.pythonRefEvalExpr}.render_unpatched()").value!!
        }

        override fun dispose() {
            if (!disposed) {
                disposed = true
                disposeCallback(this)
            }
        }

        private fun pythonBool(value: Boolean): String {
            return if (value) "True" else "False"
        }

        private fun convertStringifiedDictionary(dictionary: String?): Map<String, String> {
            if (dictionary == null) return emptyMap()
            return dictionary.removeSurrounding("{", "}")
                .split(", ")
                .associate { entry ->
                    val separator = entry.indexOf(":")
                    Pair(
                        entry.substring(0, separator).removeSurrounding("'"),
                        entry.substring(separator + 1).trim()
                    )
                }
        }
    }
}