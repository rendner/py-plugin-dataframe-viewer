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
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.StyleFunctionDetails
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.StyleFunctionValidationProblem
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.ValidationStrategyType
import cms.rendner.intellij.dataframe.viewer.python.bridge.PythonCodeBridge.PyPatchedStylerRef
import cms.rendner.intellij.dataframe.viewer.python.bridge.exceptions.InjectException
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import cms.rendner.intellij.dataframe.viewer.python.debugger.PluginPyValue
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import cms.rendner.intellij.dataframe.viewer.python.utils.stringifyBool
import cms.rendner.intellij.dataframe.viewer.python.utils.stringifyMethodCall
import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

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
        private val json: Json by lazy {
            Json { ignoreUnknownKeys = true }
        }

        @Throws(EvaluateException::class)
        override fun evaluateTableStructure(): TableStructure {
            return fetchResultAsJsonAndDecode(
                stringifyMethodCall(pythonValue.refExpr, "get_table_structure")
            )
        }

        @Throws(EvaluateException::class)
        override fun evaluateStyleFunctionDetails(): List<StyleFunctionDetails> {
            return fetchResultAsJsonAndDecode(
                stringifyMethodCall(pythonValue.refExpr, "get_style_function_details")
            )
        }

        override fun evaluateValidateStyleFunctions(
            firstRow: Int,
            firstColumn: Int,
            numberOfRows: Int,
            numberOfColumns: Int,
            validationStrategy: ValidationStrategyType,
        ): List<StyleFunctionValidationProblem> {
            if (validationStrategy == ValidationStrategyType.DISABLED) return emptyList()
            return fetchResultAsJsonAndDecode(
                stringifyMethodCall(pythonValue.refExpr, "validate_style_functions") {
                    numberParam(firstRow)
                    numberParam(firstColumn)
                    numberParam(numberOfRows)
                    numberParam(numberOfColumns)
                    refParam("${pythonValue.evaluator.getFromPluginGlobalsExpr("ValidationStrategyType")}.${validationStrategy.name}")
                }
            )
        }

        private inline fun <reified T> fetchResultAsJsonAndDecode(methodCallExpr: String): T {
            return pythonValue.evaluator.evaluate("${pythonValue.refExpr}.to_json($methodCallExpr)").forcedValue.let {
                // IntelliJ marks the @OptIn as redundant but removing it results in a warning:
                // Warning: This declaration is experimental and its usage should be marked with '@kotlinx.serialization.ExperimentalSerializationApi' or '@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)'
                @OptIn(ExperimentalSerializationApi::class)
                json.decodeFromString(it)
            }
        }

        override fun evaluateSetSortCriteria(byColumnIndex: List<Int>?, ascending: List<Boolean>?) {
            pythonValue.evaluator.evaluate(
                stringifyMethodCall(pythonValue.refExpr, "set_sort_criteria") {
                    if (byColumnIndex.isNullOrEmpty()) noneParam() else refParam(byColumnIndex.toString())
                    if (ascending.isNullOrEmpty()) noneParam() else refParam(ascending.map { stringifyBool(it) }.toString())
                }
            )
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

        override fun evaluateComputeChunkHTMLPropsTable(
            firstRow: Int,
            firstColumn: Int,
            numberOfRows: Int,
            numberOfColumns: Int,
            excludeRowHeader: Boolean,
            excludeColumnHeader: Boolean
        ): HTMLPropsTable {
            return fetchResultAsJsonAndDecode(
                stringifyMethodCall(pythonValue.refExpr, "compute_chunk_html_props_table") {
                    numberParam(firstRow)
                    numberParam(firstColumn)
                    numberParam(numberOfRows)
                    numberParam(numberOfColumns)
                    boolParam(excludeRowHeader)
                    boolParam(excludeColumnHeader)
                }
            )
        }

        override fun evaluateComputeUnpatchedHTMLPropsTable(): HTMLPropsTable {
            return fetchResultAsJsonAndDecode(
                stringifyMethodCall(pythonValue.refExpr, "compute_unpatched_html_props_table")
            )
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