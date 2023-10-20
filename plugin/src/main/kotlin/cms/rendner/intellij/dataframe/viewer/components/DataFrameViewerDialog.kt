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
package cms.rendner.intellij.dataframe.viewer.components

import cms.rendner.intellij.dataframe.viewer.components.filter.FilterInputFactory
import cms.rendner.intellij.dataframe.viewer.components.filter.editor.AbstractEditorComponent
import cms.rendner.intellij.dataframe.viewer.components.filter.editor.FilterInputState
import cms.rendner.intellij.dataframe.viewer.components.filter.editor.IEditorChangedListener
import cms.rendner.intellij.dataframe.viewer.models.chunked.*
import cms.rendner.intellij.dataframe.viewer.models.chunked.evaluator.ChunkEvaluator
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.AsyncChunkDataLoader
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.IChunkDataLoader
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.IChunkDataLoaderErrorHandler
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.exceptions.ChunkDataLoaderException
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.*
import cms.rendner.intellij.dataframe.viewer.notifications.ChunkValidationProblemNotification
import cms.rendner.intellij.dataframe.viewer.notifications.ErrorNotification
import cms.rendner.intellij.dataframe.viewer.python.bridge.CreatePatchedStylerErrorKind
import cms.rendner.intellij.dataframe.viewer.python.bridge.CreatePatchedStylerFailure
import cms.rendner.intellij.dataframe.viewer.python.bridge.DataSourceToFrameHint
import cms.rendner.intellij.dataframe.viewer.python.bridge.IPyPatchedStylerRef
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import cms.rendner.intellij.dataframe.viewer.python.pycharm.PyDebugValueEvalExpr
import cms.rendner.intellij.dataframe.viewer.python.pycharm.toPluginType
import cms.rendner.intellij.dataframe.viewer.python.pycharm.toValueEvalExpr
import cms.rendner.intellij.dataframe.viewer.settings.ApplicationSettingsService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.FontColor
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.jetbrains.python.debugger.PyDebugValue
import java.awt.Dimension
import java.text.NumberFormat
import javax.swing.JComponent

class DataFrameViewerDialog(
    private val myDebugSession: XDebugSession,
    dataSource: PyDebugValue,
    private val dataSourceToFrameHint: DataSourceToFrameHint?,
    ) :
        DialogWrapper(myDebugSession.project, false),
        IChunkValidationProblemHandler,
        IChunkDataLoaderErrorHandler,
        XDebugSessionListener {

    private var myDataSourceEvalExpr: PyDebugValueEvalExpr
    private val myEvaluator: IPluginPyValueEvaluator
    private var myLastFilterInputState: FilterInputState
    private val myFilterInput: AbstractEditorComponent
    private val myTable: DataFrameTable
    private val myTableFooterLabel = JBLabel("", UIUtil.ComponentStyle.SMALL, FontColor.BRIGHTER)

    private var myLastDataModelDisposable: Disposable? = null
    private var myLastStartedDataFetcher: ActiveDataFetcher? = null

    init {
        myDataSourceEvalExpr = dataSource.toValueEvalExpr()
        myEvaluator = dataSource.toPluginType().evaluator

        myFilterInput = FilterInputFactory.createComponent(myDebugSession.project, myDebugSession.currentPosition)
        myFilterInput.setChangedListener(object : IEditorChangedListener {
            override fun editorInputChanged() {
                updateApplyFilterButtonState()
            }
        })
        myLastFilterInputState = myFilterInput.getInputState()

        isModal = false
        title = myDataSourceEvalExpr.reEvalExpr

        setOKButtonText("Apply Filter")
        setCancelButtonText("Close")
        setCrossClosesWindow(true)
        disableApplyFilterButton()

        myTable = DataFrameTable()
        myTable.preferredSize = Dimension(635, 404)

        init()
    }

    fun startListeningAndFetchInitialData() {
        myDebugSession.addSessionListener(this)
        fetchModelData(false)
    }

    override fun dispose() {
        myDebugSession.removeSessionListener(this)
        cancelLastStartedDataFetcherAndDisposeModel()
        super.dispose()
    }

    private fun cancelLastStartedDataFetcherAndDisposeModel() {
        myLastStartedDataFetcher?.let {
            myLastStartedDataFetcher = null
            it.progressIndicator.cancel()
        }
        myLastDataModelDisposable?.let {
            myLastDataModelDisposable = null
            Disposer.dispose(it)
        }
    }

    override fun sessionStopped() = runInEdt {
        close(CANCEL_EXIT_CODE)
    }

    override fun beforeSessionResume() = runInEdt {
        disableApplyFilterButton()
        cancelLastStartedDataFetcherAndDisposeModel()
        myFilterInput.setSourcePosition(null)
    }

    private fun disableApplyFilterButton() {
        myOKAction.isEnabled = false
    }

    private fun updateApplyFilterButtonState() {
        myOKAction.isEnabled = myLastFilterInputState.text != myFilterInput.getText()
    }

    override fun stackFrameChanged() = runInEdt {
        if (!myDataSourceEvalExpr.canBeReEvaluated()) {
            setErrorText("Can't re-evaluate '${myDataSourceEvalExpr.reEvalExpr}'")
        } else {
            myDebugSession.currentPosition.let { myFilterInput.setSourcePosition(it) }
            fetchModelData(myDataSourceEvalExpr.reEvalExpr != myDataSourceEvalExpr.currentFrameRefExpr)
        }
    }

    override fun doOKAction() {
        myLastFilterInputState = myFilterInput.getInputState()
        fetchModelData(false)
    }

    override fun setErrorText(text: String?, component: JComponent?) {
        super.setErrorText(if (text == null) null else StringUtil.escapeXmlEntities(text), component)
    }

    private fun isShouldAbortDataFetchingSilentlyException(throwable: Throwable?): Boolean {
        // It is OK to abort in case of a "Process is running"-exception, since the data fetched so far
        // could be outdated when the next breakpoint is reached. A new evaluation is started
        // as soon the next breakpoint is reached.
        return throwable is EvaluateException && throwable.isCausedByProcessIsRunningException()
    }

    private fun fetchModelData(reEvaluateDataSource:Boolean) {
        disableApplyFilterButton()
        myFilterInput.hideErrorMessage()
        cancelLastStartedDataFetcherAndDisposeModel()

        val request = ModelDataFetcher.Request(
            myDataSourceEvalExpr,
            myLastFilterInputState,
            reEvaluateDataSource,
            myTable.getDataFrameModel().getDataSourceFingerprint(),
            dataSourceToFrameHint,
        )
        val fetcher = MyModelDataFetcher(myEvaluator)
        myLastStartedDataFetcher = ActiveDataFetcher(
            fetcher,
            BackgroundTaskUtil.executeOnPooledThread(disposable) { fetcher.fetchModelData(request) }
        )
    }

    private fun updateFooterLabel(tableStructure: TableStructure) {
        myTableFooterLabel.text = tableStructure.let {
            NumberFormat.getInstance().let { nf ->
                val rowsCounter =
                    if (it.rowsCount != it.orgRowsCount) {
                        "${nf.format(it.rowsCount)} of ${nf.format(it.orgRowsCount)}"
                    } else nf.format(it.rowsCount)
                val colsCounter =
                    if (it.columnsCount != it.orgColumnsCount) {
                        "${nf.format(it.columnsCount)} of ${nf.format(it.orgColumnsCount)}"
                    } else nf.format(it.columnsCount)
                "$rowsCounter rows x $colsCounter columns"
            }
        }
    }

    override fun createCenterPanel(): JComponent {
        return FormBuilder.createFormBuilder()
            .addComponent(myTable)
            .addComponent(myTableFooterLabel)
            .addVerticalGap(10)
            .addComponent(myFilterInput.getMainComponent())
            .panel
    }

    override fun getDimensionServiceKey(): String {
        return "#cms.rendner.intellij.dataframe.viewer.python.actions.ShowStyledDataFrameAction"
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return myTable.getPreferredFocusedComponent()
    }

    private fun createChunkLoader(
        patchedStyler: IPyPatchedStylerRef,
        settings: ApplicationSettingsService.MyState,
    ): IChunkDataLoader {
        return AsyncChunkDataLoader(
            ChunkEvaluator(patchedStyler),
            createChunkValidator(patchedStyler, settings.validationStrategyType),
            this,
        )
    }

    private fun createChunkValidator(
        patchedStyler: IPyPatchedStylerRef,
        validationStrategyType: ValidationStrategyType
    ): ChunkValidator? {
        return if (validationStrategyType != ValidationStrategyType.DISABLED) {
            ChunkValidator(patchedStyler, validationStrategyType, this)
        } else null
    }

    override fun handleValidationProblems(
        region: ChunkRegion,
        validationStrategy: ValidationStrategyType,
        problems: List<StyleFunctionValidationProblem>,
        details: List<StyleFunctionDetails>,
    ) {
        if (problems.isNotEmpty()) {
            ChunkValidationProblemNotification(
                region,
                validationStrategy,
                problems,
                details,
            ).notify(myDebugSession.project)
        }
    }

    override fun handleChunkDataError(region: ChunkRegion, exception: ChunkDataLoaderException) {
        if (isShouldAbortDataFetchingSilentlyException(exception.cause)) return
        ErrorNotification(
            "Error during fetching/processing chunk",
            "${exception.message ?: "Unknown error occurred"}\nfor $region",
            exception
        ).notify(myDebugSession.project)
    }

    private fun shouldHandleFetcherAction(fetcher: ModelDataFetcher): Boolean {
        return fetcher == myLastStartedDataFetcher?.fetcher && !isDisposed
    }

    private class ActiveDataFetcher(var fetcher: ModelDataFetcher, var progressIndicator: ProgressIndicator)

    private inner class MyModelDataFetcher(evaluator: IPluginPyValueEvaluator) : ModelDataFetcher(evaluator) {

        override fun handleFetchFailure(request: Request, failure: CreatePatchedStylerFailure) = runInEdt {
            if (shouldHandleFetcherAction(this)) {
                updateApplyFilterButtonState()
                when (failure.errorKind) {
                    CreatePatchedStylerErrorKind.RE_EVAL_DATA_SOURCE_OF_WRONG_TYPE,
                    CreatePatchedStylerErrorKind.INVALID_FINGERPRINT -> close(CANCEL_EXIT_CODE)

                    CreatePatchedStylerErrorKind.FILTER_FRAME_EVAL_FAILED ->
                        myFilterInput.showErrorMessage("Failed to evaluate filter from expression: ${failure.info}")

                    CreatePatchedStylerErrorKind.FILTER_FRAME_OF_WRONG_TYPE ->
                        myFilterInput.showErrorMessage("Expression returned invalid filter of type: ${failure.info}")

                    CreatePatchedStylerErrorKind.EVAL_EXCEPTION,
                    CreatePatchedStylerErrorKind.UNSUPPORTED_DATA_SOURCE_TYPE -> setErrorText(failure.info)
                }
            }
        }

        override fun handleFetchSuccess(request: Request, result: Result) = runInEdt {
            if (shouldHandleFetcherAction(this)) {
                updateApplyFilterButtonState()

                myLastDataModelDisposable?.let {
                    myLastDataModelDisposable = null
                    Disposer.dispose(it)
                }

                val settings = ApplicationSettingsService.instance.state
                // note: loader doesn't sync on settings, user has to re-open the dialog after settings are changed
                val chunkLoader = createChunkLoader(result.patchedStyler, settings)
                ChunkedDataFrameModel(
                    result.tableStructure,
                    result.frameColumnIndexList,
                    chunkLoader,
                    ChunkSize(30, 20),
                ).let { model ->
                    myLastDataModelDisposable = Disposer.newDisposable(disposable, "modelDisposable").also {
                        Disposer.register(it, model)
                        Disposer.register(it, chunkLoader)
                    }
                    myTable.setDataFrameModel(model)
                }

                if (request.reEvaluateDataSource) {
                    myDataSourceEvalExpr = result.updatedDataSourceExpr
                }

                if (request.filterInputState.text.isNotEmpty()  && request.filterInputState.text == myFilterInput.getText()) {
                    myFilterInput.saveInputInHistory()
                }

                updateFooterLabel(result.tableStructure)

                // assign focus to the DataFrame table
                // -> allows immediately to use key bindings of the table like sort/scroll/etc.
                IdeFocusManager.getInstance(myDebugSession.project).requestFocus(preferredFocusedComponent, true)
            }
        }
    }
}