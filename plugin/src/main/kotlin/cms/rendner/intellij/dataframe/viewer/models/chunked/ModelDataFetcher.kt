/*
 * Copyright 2023 cms.rendner (Daniel Schmidt)
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
package cms.rendner.intellij.dataframe.viewer.models.chunked

import cms.rendner.intellij.dataframe.viewer.components.filter.editor.FilterInputState
import cms.rendner.intellij.dataframe.viewer.python.bridge.*
import cms.rendner.intellij.dataframe.viewer.python.bridge.exceptions.CreatePatchedStylerException
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import cms.rendner.intellij.dataframe.viewer.python.pycharm.PyDebugValueEvalExpr
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager

/**
 * Fetches the required model data from Python.
 *
 * @param evaluator the evaluator to fetch the data.
 */
abstract class ModelDataFetcher(val evaluator: IPluginPyValueEvaluator) {

    /**
     * The result.
     *
     * @param updatedDataSourceExpr if [Request.reEvaluateDataSource] was true the new expression to refer to
     * the variable in Python, else the [Request.dataSourceExpr]
     * @param patchedStyler a patched styler instance to interop with the PatchedStyler created in Python by the plugin
     * @param tableStructure the table structure of the dataSource (pandas DataFrame)
     * @param frameColumnIndexList indices of the visible columns in the unfiltered dataSource (pandas DataFrame)
     *
     */
    data class Result(
        val updatedDataSourceExpr: PyDebugValueEvalExpr,
        val patchedStyler: IPyPatchedStylerRef,
        val tableStructure: TableStructure,
        val frameColumnIndexList: List<Int>,
    )

    /**
     * A request.
     *
     * @param dataSourceExpr expression to refer to the data source variable in Python (a pandas DataFrame or Styler or a dict)
     * @param filterInputState to filter the dataSource
     * @param reEvaluateDataSource true if the dataSource has to be re-evaluated (e.g. after a stack frame change)
     * @param oldDataSourceFingerprint if not null, value is compared against actual fingerprint
     * @param dataSourceToFrameHint hint to create a pandas DataFrame from the data source in case it is not a pandas DataFrame or Styler
     */
    data class Request(
        val dataSourceExpr: PyDebugValueEvalExpr,
        val filterInputState: FilterInputState,
        val reEvaluateDataSource: Boolean,
        val oldDataSourceFingerprint: String? = null,
        val dataSourceToFrameHint: DataSourceToFrameHint? = null,
    )

    /**
     * Called in case of an unexpected evaluation result.
     *
     * @param request the initial request.
     * @param failure a short description of the failure.
     */
    protected abstract fun handleFetchFailure(request: Request, failure: CreatePatchedStylerFailure)

    /**
     * Called after all required data is successfully fetched from the data-source.
     *
     * @param request the initial request.
     * @param result the result to create a data model.
     */
    protected abstract fun handleFetchSuccess(request: Request, result: Result)

    private fun handleFetchFailure(request: Request, ex: EvaluateException, info: String) {
        if (ex.isCausedByProcessIsRunningException() || ex.isCausedByDisconnectException()) return
        handleFetchFailure(request, CreatePatchedStylerErrorKind.EVAL_EXCEPTION, info)
    }

    private fun handleFetchFailure(request: Request, errorKind: CreatePatchedStylerErrorKind, info: String) {
        handleFetchFailure(request, CreatePatchedStylerFailure(errorKind, info))
    }

    /**
     * Starts the data fetching.
     *
     * @param request describes where to fetch the data from.
     * @throws [ProcessCanceledException] in case the evaluation process was canceled.
     */
    @Throws(ProcessCanceledException::class)
    fun fetchModelData(request: Request) {
        ProgressManager.checkCanceled()

        var updatedDataSourceEvalExpr = request.dataSourceExpr
        if (request.reEvaluateDataSource) {

            val reEvaluatedDataSource = try {
                evaluator.evaluate(request.dataSourceExpr.reEvalExpr)
            } catch (ex: EvaluateException) {
                handleFetchFailure(request, ex, "${ex.localizedMessage} => caused by: '${request.dataSourceExpr.reEvalExpr}'")
                return
            }

            if (request.dataSourceExpr.qualifiedType != reEvaluatedDataSource.qualifiedType) {
                handleFetchFailure(
                    request,
                    CreatePatchedStylerErrorKind.RE_EVAL_DATA_SOURCE_OF_WRONG_TYPE,
                    "Re-evaluated data source is of another type: ${reEvaluatedDataSource.qualifiedType} (was: ${request.dataSourceExpr.qualifiedType})"
                )
                return
            }

            updatedDataSourceEvalExpr = request.dataSourceExpr.withUpdatedRefExpr(reEvaluatedDataSource.refExpr)
        }

        ProgressManager.checkCanceled()

        val patchedStyler = try {
            PythonCodeBridge.createPatchedStyler(
                evaluator,
                updatedDataSourceEvalExpr.currentFrameRefExpr,
                CreatePatchedStylerConfig(
                    dataSourceToFrameHint = request.dataSourceToFrameHint,
                    previousFingerprint = request.oldDataSourceFingerprint,
                    filterEvalExpr = request.filterInputState.text,
                    filterEvalExprProvideFrame = request.filterInputState.containsSyntheticFrameIdentifier,
                ),
            )
        } catch (ex: CreatePatchedStylerException) {
            handleFetchFailure(request, ex.failure)
            return
        } catch (ex: EvaluateException) {
            handleFetchFailure(request, ex, "${ex.localizedMessage} => caused by: 'createPatchedStyler(${updatedDataSourceEvalExpr.currentFrameRefExpr})'")
            return
        }

        ProgressManager.checkCanceled()

        val tableStructure = patchedStyler.evaluateTableStructure()
        val frameColumnIndexList = mutableListOf<Int>()
        if (tableStructure.columnsCount > 0) {
            var entryOffset = 0
            val maxEntries = 1000
            while (entryOffset < tableStructure.columnsCount) {
                ProgressManager.checkCanceled()
                try {
                    patchedStyler.evaluateGetOrgIndicesOfVisibleColumns(entryOffset, maxEntries).also {
                        frameColumnIndexList.addAll(it)
                    }
                } catch (ex: EvaluateException) {
                    handleFetchFailure(request, ex, "${ex.localizedMessage} => caused by: 'evaluateGetOrgIndicesOfVisibleColumns($entryOffset)'")
                    return
                }
                entryOffset += maxEntries
            }
        }

        handleFetchSuccess(
            request,
            Result(
                updatedDataSourceEvalExpr,
                patchedStyler,
                tableStructure,
                frameColumnIndexList,
            ),
        )
    }
}