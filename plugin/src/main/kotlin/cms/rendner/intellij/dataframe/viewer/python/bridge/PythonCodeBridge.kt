/*
 * Copyright 2022 cms.rendner (Daniel Schmidt)
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
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.ProblemReason
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.StyleFunctionDetails
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.StyleFunctionValidationProblem
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.ValidationStrategyType
import cms.rendner.intellij.dataframe.viewer.python.bridge.PythonCodeBridge.PyPatchedStylerRef
import cms.rendner.intellij.dataframe.viewer.python.bridge.exceptions.InjectException
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import cms.rendner.intellij.dataframe.viewer.python.debugger.PluginPyValue
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import cms.rendner.intellij.dataframe.viewer.python.utils.*
import com.intellij.openapi.diagnostic.Logger

/**
 * Creates [PyPatchedStylerRef] instances to enable "communication" with pandas objects.
 *
 * This class injects the Python-specific plugin code into the Python process.
 *
 * This class calls methods of the injected "StyledDataFrameViewerBridge" Python class.
 * Therefore, the method signatures of the Python class "StyledDataFrameViewerBridge"
 * have to match with the ones used by this class.
 */
class PythonCodeBridge {

    companion object {
        private val logger = Logger.getInstance(PythonCodeBridge::class.java)
    }

    fun getBridgeExpr(evaluator: IPluginPyValueEvaluator): String {
        return evaluator.getFromPluginGlobalsExpr("StyledDataFrameViewerBridge")
    }

    fun createPatchedStyler(frameOrStyler: PluginPyValue): IPyPatchedStylerRef {
        ensurePluginCodeIsInjected(frameOrStyler.evaluator)

        val patchedStyler = frameOrStyler.evaluator.let {
            it.evaluate(
                stringifyMethodCall(getBridgeExpr(it), "create_patched_styler") {
                    refParam(frameOrStyler.refExpr)
                }
            )
        }
        return PyPatchedStylerRef(
            patchedStyler,
            this::disposePatchedStylerRef,
        )
    }

    private fun disposePatchedStylerRef(patchedStylerRef: PyPatchedStylerRef) {
        try {
            patchedStylerRef.pythonValue.evaluator.let {
                it.evaluate(
                    stringifyMethodCall(getBridgeExpr(it), "delete_patched_styler") {
                        refParam(patchedStylerRef.pythonValue.refExpr)
                    }
                )
            }
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
            injectCodeIntoPluginGlobals(evaluator, pandasCodeProvider)
        }
    }

    @Throws(InjectException::class)
    private fun injectCodeIntoPluginGlobals(
        evaluator: IPluginPyValueEvaluator,
        pandasCodeProvider: PythonCodeProvider,
    ) {
        try {
            val createPluginGlobalsExpr = "${evaluator.pluginGlobalsName} = {}"
            val execExpr = "exec('''${pandasCodeProvider.getCode()}''', ${evaluator.pluginGlobalsName})"
            evaluator.execute("$createPluginGlobalsExpr\n$execExpr")
        } catch (ex: EvaluateException) {
            throw InjectException("Failed to inject python plugin code.", ex)
        }
    }

    private fun isInitialized(evaluator: IPluginPyValueEvaluator): Boolean {
        val isInitialized = try {
            evaluator.evaluate("${getBridgeExpr(evaluator)}.check()")
        } catch (ignore: EvaluateException) {
            null
        }

        return isInitialized?.value == "True"
    }

    @Throws(InjectException::class)
    private fun getPandasVersion(evaluator: IPluginPyValueEvaluator): PandasVersion {
        try {
            evaluator.execute("import pandas as pd")
            val evaluateResult = evaluator.evaluate("pd.__version__")
            return PandasVersion.fromString(evaluateResult.forcedValue)
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
        private val disposeCallback: (PyPatchedStylerRef) -> Unit,
    ) : IPyPatchedStylerRef {

        private var disposed = false

        @Throws(EvaluateException::class)
        override fun evaluateTableStructure(): TableStructure {
            val methodCallExpr = stringifyMethodCall(pythonValue.refExpr, "get_table_structure")
            return pythonValue.evaluator.evaluate("str($methodCallExpr)").forcedValue.let {
                val propsMap = parsePythonDictionary(it)
                val rowsCount = parsePythonInt(propsMap.getValue("rows_count"))

                TableStructure(
                    rowsCount = rowsCount,
                    // if we have no rows we interpret the DataFrame as empty - therefore no columns
                    columnsCount = if (rowsCount > 0) parsePythonInt(propsMap.getValue("columns_count")) else 0,
                    rowLevelsCount = parsePythonInt(propsMap.getValue("row_levels_count")),
                    columnLevelsCount = parsePythonInt(propsMap.getValue("column_levels_count")),
                    hideRowHeader = parsePythonBool(propsMap.getValue("hide_row_header")),
                    hideColumnHeader = parsePythonBool(propsMap.getValue("hide_column_header"))
                )
            }
        }

        @Throws(EvaluateException::class)
        override fun evaluateStyleFunctionDetails(): List<StyleFunctionDetails> {
            return pythonValue.evaluator.evaluateStringyfiedList(
                stringifyMethodCall(pythonValue.refExpr, "get_style_function_details"),
            ).map { detail ->
                val propsMap = parsePythonDictionary(detail)
                StyleFunctionDetails(
                    index = parsePythonInt(propsMap.getValue("index")),
                    qname = parsePythonString(propsMap.getValue("qname")),
                    resolvedName = parsePythonString(propsMap.getValue("resolved_name")),
                    axis = parsePythonString(propsMap.getValue("axis")),
                    isPandasBuiltin = parsePythonBool(propsMap.getValue("is_pandas_builtin")),
                    isSupported = parsePythonBool(propsMap.getValue("is_supported")),
                    isApply = parsePythonBool(propsMap.getValue("is_apply")),
                    isChunkParentRequested = parsePythonBool(propsMap.getValue("is_chunk_parent_requested")),
                )
            }
        }

        override fun evaluateValidateStyleFunctions(
            firstRow: Int,
            firstColumn: Int,
            numberOfRows: Int,
            numberOfColumns: Int,
            validationStrategy: ValidationStrategyType,
        ): List<StyleFunctionValidationProblem> {
            if (validationStrategy == ValidationStrategyType.DISABLED) return emptyList()
            return pythonValue.evaluator.evaluateStringyfiedList(
                stringifyMethodCall(pythonValue.refExpr, "validate_style_functions") {
                    numberParam(firstRow)
                    numberParam(firstColumn)
                    numberParam(numberOfRows)
                    numberParam(numberOfColumns)
                    refParam("${pythonValue.evaluator.getFromPluginGlobalsExpr("ValidationStrategyType")}.${validationStrategy.name}")
                },
            ).map { info ->
                val propsMap = parsePythonDictionary(info)
                StyleFunctionValidationProblem(
                    index = parsePythonInt(propsMap.getValue("index")),
                    reason = ProblemReason.valueOfOrUnknown(parsePythonString(propsMap.getValue("reason"))),
                    message = parsePythonString(propsMap.getValue("message")),
                )
            }
        }

        @Throws(EvaluateException::class)
        override fun evaluateRenderChunk(
            firstRow: Int,
            firstColumn: Int,
            numberOfRows: Int,
            numberOfColumns: Int,
            excludeRowHeader: Boolean,
            excludeColumnHeader: Boolean
        ): String {
            return pythonValue.evaluator.evaluate(
                stringifyMethodCall(pythonValue.refExpr, "render_chunk") {
                    numberParam(firstRow)
                    numberParam(firstColumn)
                    numberParam(numberOfRows)
                    numberParam(numberOfColumns)
                    boolParam(excludeRowHeader)
                    boolParam(excludeColumnHeader)
                }
            ).forcedValue
        }

        @Throws(EvaluateException::class)
        override fun evaluateRenderUnpatched(): String {
            // Note:
            // Each time this method is called on the same instance, style-properties are re-created without
            // clearing previous ones. Calling this method n-times results in n-times duplicated properties.
            // At least this is the behaviour in pandas 1.2.0 and looks like a bug in pandas.
            return pythonValue.evaluator.evaluate(
                stringifyMethodCall(pythonValue.refExpr, "render_unpatched")
            ).forcedValue
        }

        override fun dispose() {
            if (!disposed) {
                disposed = true
                disposeCallback(this)
            }
        }
    }
}