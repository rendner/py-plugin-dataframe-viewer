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
package cms.rendner.intellij.dataframe.viewer.models.chunked

import cms.rendner.intellij.dataframe.viewer.components.filter.FilterInputState
import cms.rendner.intellij.dataframe.viewer.python.bridge.*
import cms.rendner.intellij.dataframe.viewer.python.bridge.exceptions.CreateTableSourceException
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.DataSourceInfo
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager

private var tempVarIdCounter: Int = 0

/**
 * Creates a table source on Python side, which is used to fetch data from an underlying data source.
 * A data source can be a Python dictionary or DataFrame instance.
 * The supported data sources depend on the used table source.
 *
 * @param evaluator the evaluator to create the table source.
 */
abstract class TableSourceCreator(val evaluator: IPluginPyValueEvaluator) {

    /**
     * The result.
     *
     * @param currentStackFrameRefExpr if [Request.reEvaluate] was true the new expression to refer to
     * the created table source in Python.
     * @param tableSourceRef the table source instance to interop with the instance created in Python.
     * @param tableStructure the table structure of the table source.
     */
    data class Result(
        val currentStackFrameRefExpr: String,
        val tableSourceRef: IPyTableSourceRef,
        val tableStructure: TableStructure,
    )

    /**
     * A request.
     *
     * @param info to access the data source variable in Python (a DataFrame or pandas Styler or a dict).
     * @param reEvaluate true if the data source, described by [info] has to be re-evaluated (e.g. after a stack frame change).
     * @param fingerprint if not null, value is compared against the fingerprint of the used data source.
     * The fingerprint is used to identify if the underlying data source is the same one or has changed between
     * re-evaluations.
     * @param filterInputState to filter the table source.
     * In case they don't match a [CreateTableSourceErrorKind.INVALID_FINGERPRINT] is reported.
     * @param transformHint in case the date source for the table source is a Python dict and has to be converted.
     */
    data class Request(
        val info: DataSourceInfo,
        val reEvaluate: Boolean,
        val fingerprint: String? = null,
        val filterInputState: FilterInputState? = null,
        val transformHint: DataSourceTransformHint? = null,
    )

    /**
     * Called in case of an unexpected result.
     *
     * @param request the initial request.
     * @param failure a short description of the failure.
     */
    protected abstract fun handleFailure(request: Request, failure: CreateTableSourceFailure)

    /**
     * Called in case the table source was successfully created on Python side.
     *
     * @param request the initial request.
     * @param result the result to interact with the created table source.
     */
    protected abstract fun handleSuccess(request: Request, result: Result)

    private fun handleFailure(request: Request, ex: EvaluateException, info: String) {
        if (ex.isCausedByProcessIsRunningException() || ex.isCausedByDisconnectException()) return
        handleFailure(request, CreateTableSourceErrorKind.EVAL_EXCEPTION, info)
    }

    private fun handleFailure(request: Request, errorKind: CreateTableSourceErrorKind, info: String) {
        handleFailure(request, CreateTableSourceFailure(errorKind, info))
    }

    /**
     * Starts the process to create the requested table source on Python side.
     *
     * @param request describes which table source to create.
     * @throws [ProcessCanceledException] in case the process was canceled.
     */
    @Throws(ProcessCanceledException::class)
    fun create(request: Request) {
        ProgressManager.checkCanceled()

        var dataSourceRefExpr = request.info.source.currentStackFrameRefExpr
        if (request.reEvaluate) {

            val reEvaluatedDataSource = try {
                evaluator.evaluate(request.info.source.reEvalExpr)
            } catch (ex: EvaluateException) {
                handleFailure(request, ex, "${ex.localizedMessage} => caused by: '${request.info.source.reEvalExpr}'")
                return
            }

            if (request.info.source.qualifiedType != reEvaluatedDataSource.qualifiedType) {
                handleFailure(
                    request,
                    CreateTableSourceErrorKind.RE_EVAL_DATA_SOURCE_OF_WRONG_TYPE,
                    "Re-evaluated data source is of another type: ${reEvaluatedDataSource.qualifiedType} (was: ${request.info.source.reEvalExpr})"
                )
                return
            }

            dataSourceRefExpr = reEvaluatedDataSource.refExpr
        }

        ProgressManager.checkCanceled()

        val tableSourceRef = try {
            TableSourceFactory.create(
                evaluator,
                request.info.tableSourceFactoryImport,
                dataSourceRefExpr,
                CreateTableSourceConfig(
                    dataSourceTransformHint = request.transformHint,
                    previousFingerprint = request.fingerprint,
                    filterEvalExpr = request.filterInputState?.text,
                    filterEvalExprProvideFrame = request.filterInputState?.containsSyntheticFrameIdentifier == true,
                    tempVarSlotId = if (evaluator.isConsole()) "_temp_slot_id_${tempVarIdCounter++}" else null,
                ),
            )
        } catch (ex: CreateTableSourceException) {
            handleFailure(request, ex.failure)
            return
        } catch (ex: EvaluateException) {
            handleFailure(request, ex, "${ex.localizedMessage} => caused by: 'TableSourceFactory.create(...)' using '$dataSourceRefExpr'")
            return
        }

        ProgressManager.checkCanceled()
        val tableStructure = tableSourceRef.evaluateTableStructure()
        ProgressManager.checkCanceled()

        handleSuccess(
            request,
            Result(dataSourceRefExpr, tableSourceRef, tableStructure),
        )
    }
}