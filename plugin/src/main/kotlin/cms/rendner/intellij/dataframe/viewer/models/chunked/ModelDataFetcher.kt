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
package cms.rendner.intellij.dataframe.viewer.models.chunked

import cms.rendner.intellij.dataframe.viewer.components.filter.IFilterEvalExprBuilder
import cms.rendner.intellij.dataframe.viewer.python.PandasTypes
import cms.rendner.intellij.dataframe.viewer.python.bridge.IPyPatchedStylerRef
import cms.rendner.intellij.dataframe.viewer.python.bridge.PythonCodeBridge
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import cms.rendner.intellij.dataframe.viewer.python.debugger.PluginPyValue
import cms.rendner.intellij.dataframe.viewer.python.pycharm.PyDebugValueEvalExpr
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
     * @param dataSource a patched styler instance to interop with the PatchedStyler created in Python by the plugin
     * @param tableStructure the table structure of the dataSource (pandas DataFrame)
     * @param frameColumnIndexList indices of the visible columns in the unfiltered dataSource (pandas DataFrame)
     * @param dataSourceFingerprint fingerprint of the data source.
     *
     */
    data class Result(
        val updatedDataSourceExpr: PyDebugValueEvalExpr,
        val dataSource: IPyPatchedStylerRef,
        val tableStructure: TableStructure,
        val frameColumnIndexList: List<Int>,
        val dataSourceFingerprint: String,
    )

    /**
     * A request.
     *
     * @param dataSourceExpr expression to refer to the variable in Python (the pandas DataFrame or Styler)
     * @param filterExprBuilder to create a filter expression to filter the dataSource
     * @param reEvaluateDataSource true if the dataSource has to be re-evaluated (e.g. after a stack frame change)
     * @param oldDataSourceFingerprint if not null, value is compared against actual fingerprint.
     * In case they don't match the request is aborted and [handleNonMatchingFingerprint] is called.
     */
    data class Request(
        val dataSourceExpr: PyDebugValueEvalExpr,
        val filterExprBuilder: IFilterEvalExprBuilder,
        val reEvaluateDataSource: Boolean,
        val oldDataSourceFingerprint: String? = null,
    )

    /**
     * Called if the underlying DataFrame or Styler can't be reached anymore.
     *
     * @param request the initial request.
     * @param ex the exception.
     */
    protected abstract fun handleReEvaluateDataSourceException(request: Request, ex: Exception)

    /**
     * Called if the underlying DataFrame or Styler has another fingerprint as expected.
     *
     * @param request the initial request.
     * @param fingerprint the new non-matching fingerprint.
     */
    protected abstract fun handleNonMatchingFingerprint(request: Request, fingerprint: String)

    /**
     * Called if the filter expression can't be evaluated.
     *
     * @param request the initial request.
     * @param ex the exception.
     */
    protected abstract fun handleFilterFrameEvaluateException(request: Request, ex: Exception)

    /**
     * Called if the initial data, to build the data model, can't be fetched from the DataFrame/Styler.
     *
     * @param request the initial request.
     * @param ex the exception.
     */
    protected abstract fun handleEvaluateModelDataException(request: Request, ex: Exception)

    /**
     * Called after all required data is successfully fetched from the DataFrame/Styler.
     *
     * @param request the initial request.
     * @param result the result to create a data model.
     * @param fetcher the used fetcher instance to check if the fetched result is outdated or not.
     */
    protected abstract fun handleEvaluateModelDataSuccess(request: Request, result: Result, fetcher: ModelDataFetcher)

    /**
     * Starts the data fetching.
     *
     * @param request describes where to fetch the data from.
     */
    fun fetchModelData(request: Request) {
        ProgressManager.checkCanceled()
        var updatedDataSourceEvalExpr = request.dataSourceExpr
        if (request.reEvaluateDataSource) {
            try {
                updatedDataSourceEvalExpr = evaluator.evaluate(request.dataSourceExpr.reEvalExpr).let {
                    if (request.dataSourceExpr.qualifiedType != it.qualifiedType) {
                        throw IllegalStateException(
                            "Re-evaluated data source is of another type: ${it.qualifiedType} (was: ${request.dataSourceExpr.qualifiedType})",
                        )
                    }
                    request.dataSourceExpr.withUpdatedRefExpr(it.refExpr)
                }
            } catch (ex: Exception) {
                handleReEvaluateDataSourceException(request, ex)
                return
            }
            ProgressManager.checkCanceled()
        }

        val dataSourceFingerprint = try {
            PythonCodeBridge.createFingerprint(evaluator, updatedDataSourceEvalExpr.currentFrameRefExpr).let {
                if (request.oldDataSourceFingerprint != null && it != request.oldDataSourceFingerprint) {
                    handleNonMatchingFingerprint(request, it)
                    return
                }
                it
            }
        } catch (ex: Exception) {
            handleReEvaluateDataSourceException(request, ex)
            return
        }

        ProgressManager.checkCanceled()
        var filterFrame: PluginPyValue? = null
        try {
            val dataFrameRefExpr = updatedDataSourceEvalExpr.currentFrameRefExpr.let {
                if (PandasTypes.isStyler(updatedDataSourceEvalExpr.qualifiedType)) "${it}.data" else it
            }
            request.filterExprBuilder.build(dataFrameRefExpr).let { filterExpr ->
                if (filterExpr.isNotEmpty()) {
                    evaluator.evaluate(filterExpr, true).let {
                        if (!PandasTypes.isDataFrame(it.qualifiedType)) {
                            throw IllegalStateException("Filter result is of wrong type: ${it.qualifiedType}")
                        } else {
                            filterFrame = it
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            handleFilterFrameEvaluateException(request, ex)
            return
        }

        ProgressManager.checkCanceled()
        try {
            val dataSource = PythonCodeBridge.createPatchedStyler(
                evaluator,
                updatedDataSourceEvalExpr.currentFrameRefExpr,
                filterFrame?.refExpr,
            )

            ProgressManager.checkCanceled()
            val tableStructure = dataSource.evaluateTableStructure()
            // in case of same unfiltered dataSource the data is the same as fetched for the previous model
            // could be restructured to not fetch the same data
            val frameColumnIndexList = mutableListOf<Int>()
            if (tableStructure.columnsCount > 0) {
                var entryOffset = 0
                val maxEntries = 1000
                while (entryOffset < tableStructure.columnsCount) {
                    ProgressManager.checkCanceled()
                    frameColumnIndexList.addAll(
                        dataSource.evaluateGetOrgIndicesOfVisibleColumns(entryOffset, maxEntries),
                    )
                    entryOffset += maxEntries
                }
            }

            handleEvaluateModelDataSuccess(
                request,
                Result(
                    updatedDataSourceEvalExpr,
                    dataSource,
                    tableStructure,
                    frameColumnIndexList,
                    dataSourceFingerprint,
                ),
                this
            )
        } catch (ex: Exception) {
            handleEvaluateModelDataException(request, ex)
        }
    }
}