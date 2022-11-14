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

import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkRegion
import cms.rendner.intellij.dataframe.viewer.models.chunked.SortCriteria
import cms.rendner.intellij.dataframe.viewer.models.chunked.TableStructure
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.StyleFunctionDetails
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.StyleFunctionValidationProblem
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.ValidationStrategyType
import cms.rendner.intellij.dataframe.viewer.python.bridge.PythonCodeBridge.PyPatchedStylerRef
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import cms.rendner.intellij.dataframe.viewer.python.debugger.PluginPyValue
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import cms.rendner.intellij.dataframe.viewer.python.utils.stringifyBool
import cms.rendner.intellij.dataframe.viewer.python.utils.stringifyMethodCall
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Creates [PyPatchedStylerRef] instances to enable "communication" with pandas objects.
 *
 * This class calls methods of the injected "StyledDataFrameViewerBridge" Python class.
 * Therefore, the method signatures of the Python class "StyledDataFrameViewerBridge"
 * have to match with the ones called by this class.
 */
class PythonCodeBridge {

    companion object {
        private fun getBridgeExpr(): String {
            return PythonPluginCodeInjector.getFromPluginModuleExpr("StyledDataFrameViewerBridge")
        }

        fun createFingerprint(evaluator: IPluginPyValueEvaluator, frameOrStylerRefExpr: String): String {
            return evaluator.evaluate(
                stringifyMethodCall(getBridgeExpr(), "create_fingerprint") {
                    refParam(frameOrStylerRefExpr)
                }
            ).value!!
        }

        fun createPatchedStyler(
            evaluator: IPluginPyValueEvaluator,
            frameOrStylerRefExpr: String,
            filterFrameRefExpr: String? = null
        ): IPyPatchedStylerRef {
            val patchedStyler = evaluator.evaluate(
                stringifyMethodCall(getBridgeExpr(), "create_patched_styler") {
                    refParam(frameOrStylerRefExpr)
                    if (filterFrameRefExpr == null) noneParam() else refParam(filterFrameRefExpr)
                }
            )
            return PyPatchedStylerRef(patchedStyler)
        }
    }

    /**
     * Kotlin implementation of the "PatchedStyler" Python class.
     * All calls are forwarded to the referenced Python instance. Therefore, the method signatures of
     * the Python class "PatchedStyler" have to match with the ones used by this class.
     *
     * @param pythonValue the wrapped "PatchedStyler" Python instance.
     */
    private class PyPatchedStylerRef(val pythonValue: PluginPyValue) : IPyPatchedStylerRef {

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
            chunk: ChunkRegion,
            validationStrategy: ValidationStrategyType,
        ): List<StyleFunctionValidationProblem> {
            if (validationStrategy == ValidationStrategyType.DISABLED) return emptyList()
            return fetchResultAsJsonAndDecode(
                stringifyMethodCall(pythonValue.refExpr, "validate_style_functions") {
                    numberParam(chunk.firstRow)
                    numberParam(chunk.firstColumn)
                    numberParam(chunk.numberOfRows)
                    numberParam(chunk.numberOfColumns)
                    refParam("${PythonPluginCodeInjector.getFromPluginModuleExpr("ValidationStrategyType")}.${validationStrategy.name}")
                }
            )
        }

        private inline fun <reified T> fetchResultAsJsonAndDecode(methodCallExpr: String): T {
            return pythonValue.evaluator.evaluate("${pythonValue.refExpr}.to_json($methodCallExpr)").forcedValue.let {
                // IntelliJ marks the @OptIn as redundant but removing it results in a warning:
                // Warning: This declaration is experimental and its usage should be marked with '@kotlinx.serialization.ExperimentalSerializationApi' or '@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)'
                @OptIn(ExperimentalSerializationApi::class)
                json.decodeFromString(it.removeSurrounding("\""))
            }
        }

        override fun evaluateSetSortCriteria(sortCriteria: SortCriteria?) {
            pythonValue.evaluator.evaluate(
                stringifyMethodCall(pythonValue.refExpr, "set_sort_criteria") {
                    sortCriteria?.byIndex.let {
                        if (it.isNullOrEmpty()) noneParam() else refParam(it.toString())
                    }
                    sortCriteria?.ascending.let {
                        if (it.isNullOrEmpty()) noneParam() else refParam(it.map { e -> stringifyBool(e) }.toString())
                    }
                }
            )
        }

        override fun evaluateComputeChunkHTMLPropsTable(
            chunk: ChunkRegion,
            excludeRowHeader: Boolean,
            excludeColumnHeader: Boolean
        ): HTMLPropsTable {
            return fetchResultAsJsonAndDecode(
                stringifyMethodCall(pythonValue.refExpr, "compute_chunk_html_props_table") {
                    numberParam(chunk.firstRow)
                    numberParam(chunk.firstColumn)
                    numberParam(chunk.numberOfRows)
                    numberParam(chunk.numberOfColumns)
                    boolParam(excludeRowHeader)
                    boolParam(excludeColumnHeader)
                }
            )
        }

        override fun evaluateComputeUnpatchedHTMLPropsTable(): HTMLPropsTable {
            return fetchResultAsJsonAndDecode(
                stringifyMethodCall(pythonValue.refExpr, "internal_compute_unpatched_html_props_table")
            )
        }

        override fun evaluateGetOrgIndicesOfVisibleColumns(partStart: Int, maxColumns: Int): List<Int> {
            return fetchResultAsJsonAndDecode(
                stringifyMethodCall(pythonValue.refExpr, "get_org_indices_of_visible_columns") {
                    numberParam(partStart)
                    numberParam(maxColumns)
                }
            )
        }
    }
}