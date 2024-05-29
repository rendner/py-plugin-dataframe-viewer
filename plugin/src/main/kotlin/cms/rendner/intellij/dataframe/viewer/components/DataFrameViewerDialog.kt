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
package cms.rendner.intellij.dataframe.viewer.components

import cms.rendner.intellij.dataframe.viewer.components.filter.FilterInputFactory
import cms.rendner.intellij.dataframe.viewer.components.filter.AbstractFilterInput
import cms.rendner.intellij.dataframe.viewer.components.filter.FilterInputState
import cms.rendner.intellij.dataframe.viewer.components.filter.IFilterInputChangedListener
import cms.rendner.intellij.dataframe.viewer.components.filter.IFilterInputCompletionContributor
import cms.rendner.intellij.dataframe.viewer.models.chunked.*
import cms.rendner.intellij.dataframe.viewer.models.chunked.evaluator.ChunkEvaluator
import cms.rendner.intellij.dataframe.viewer.models.chunked.evaluator.IChunkValidationProblemHandler
import cms.rendner.intellij.dataframe.viewer.models.chunked.evaluator.ValidatedChunkEvaluator
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.AsyncChunkDataLoader
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.IChunkDataLoader
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.IChunkDataLoaderErrorHandler
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.exceptions.ChunkDataLoaderException
import cms.rendner.intellij.dataframe.viewer.notifications.ChunkValidationProblemNotification
import cms.rendner.intellij.dataframe.viewer.notifications.ErrorNotification
import cms.rendner.intellij.dataframe.viewer.python.bridge.*
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.DataSourceInfo
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginEdtAwareDebugSessionListener
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import cms.rendner.intellij.dataframe.viewer.settings.ApplicationSettingsService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.FontColor
import com.intellij.xdebugger.XSourcePosition
import java.awt.Dimension
import java.text.NumberFormat
import javax.swing.Action
import javax.swing.JComponent

class DataFrameViewerDialog(
    private val project: Project,
    private val myEvaluator: IPluginPyValueEvaluator,
    dataSourceInfo: DataSourceInfo,
    filterCompletionContributor: IFilterInputCompletionContributor,
    private val dataSourceTransformHint: DataSourceTransformHint?,
    ) :
        DialogWrapper(project, false),
        IChunkValidationProblemHandler,
        IChunkDataLoaderErrorHandler,
        IPluginEdtAwareDebugSessionListener {

    private var myDataSourceInfo: DataSourceInfo
    private var myLastFilterInputState: FilterInputState? = null
    private val myFilterInput: AbstractFilterInput?
    private val myTable: DataFrameTable
    private val myTableFooterLabel = JBLabel("", UIUtil.ComponentStyle.SMALL, FontColor.BRIGHTER)

    private var myLastDataModelDisposable: Disposable? = null
    private var myRunningCreatorProcess: RunningCreatorProcess? = null

    init {
        myDataSourceInfo = dataSourceInfo

        myFilterInput = if (myDataSourceInfo.filterable) {
            FilterInputFactory.createComponent(project, filterCompletionContributor, null).also {
                it.setChangedListener(object : IFilterInputChangedListener {
                    override fun filterInputChanged() {
                        updateApplyFilterButtonState()
                    }
                })
                myLastFilterInputState = it.getInputState()
            }
        } else null

        isModal = false
        title = myDataSourceInfo.source.reEvalExpr

        setOKButtonText("Apply Filter")
        setCancelButtonText("Close")
        setCrossClosesWindow(true)
        disableApplyFilterButton()

        myTable = DataFrameTable()
        myTable.preferredSize = Dimension(635, 404)

        init()
    }

    fun createTableModel() {
        createTableSource(false)
    }

    override fun dispose() {
        cancelRunningCreatorAndDisposeModel()
        super.dispose()
    }

    private fun cancelRunningCreatorAndDisposeModel() {
        cleanupRunningCreatorProcess()
        myLastDataModelDisposable?.let {
            myLastDataModelDisposable = null
            Disposer.dispose(it)
        }
    }

    override fun sessionStopped() {
        close(CANCEL_EXIT_CODE)
    }

    override fun beforeSessionResume() {
        disableApplyFilterButton()
        cancelRunningCreatorAndDisposeModel()
        myFilterInput?.setSourcePosition(null)
    }

    private fun disableApplyFilterButton() {
        myOKAction.isEnabled = false
    }

    private fun updateApplyFilterButtonState() {
        if (myDataSourceInfo.filterable) {
            myOKAction.isEnabled = myLastFilterInputState!!.text != myFilterInput!!.getText()
        }
    }

    override fun stackFrameChanged() {
        if (!myDataSourceInfo.source.canBeReEvaluated()) {
            setErrorText("Can't re-evaluate '${myDataSourceInfo.source.reEvalExpr}'")
        } else {
            createTableSource(myDataSourceInfo.source.reEvalExpr != myDataSourceInfo.source.currentStackFrameRefExpr)
        }
    }

    fun setFilterSourcePosition(sourcePosition: XSourcePosition?) {
        if (myDataSourceInfo.filterable) {
            myFilterInput?.setSourcePosition(sourcePosition)
        }
    }

    override fun doOKAction() {
        myLastFilterInputState = myFilterInput?.getInputState()
        createTableSource(false)
    }

    override fun setErrorText(text: String?, component: JComponent?) {
        super.setErrorText(if (text == null) null else StringUtil.escapeXmlEntities(text), component)
    }

    override fun createActions(): Array<Action> {
        val actions = super.createActions()
        if (!myDataSourceInfo.filterable) {
            return actions.filter { it != myOKAction }.toTypedArray()
        }
        return actions
    }

    private fun isShouldAbortDataFetchingSilentlyException(throwable: Throwable?): Boolean {
        // It is OK to abort in case of a "Process is running"-exception, since the data fetched so far
        // could be outdated when the next breakpoint is reached. A new evaluation is started
        // as soon the next breakpoint is reached.
        return throwable is EvaluateException && throwable.isCausedByProcessIsRunningException()
    }

    private fun createTableSource(reEvaluateDataSource:Boolean) {
        disableApplyFilterButton()
        myFilterInput?.hideErrorMessage()
        cancelRunningCreatorAndDisposeModel()

        val request = TableSourceCreator.Request(
            myDataSourceInfo,
            reEvaluateDataSource,
            myTable.getDataFrameModel().getFingerprint(),
            myLastFilterInputState,
            dataSourceTransformHint,
        )
        val creator = MyTableSourceCreator(myEvaluator)
        myRunningCreatorProcess = RunningCreatorProcess(
            creator,
            BackgroundTaskUtil.executeOnPooledThread(disposable) { creator.create(request) }
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
            .apply {
                if (myDataSourceInfo.filterable) {
                    this.addComponent(myFilterInput!!.getMainComponent())
                }
            }
            .panel
    }

    override fun getDimensionServiceKey(): String {
        return "#cms.rendner.intellij.dataframe.viewer.python.actions.ShowStyledDataFrameAction"
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return myTable.getPreferredFocusedComponent()
    }

    private fun createChunkLoader(
        tableSourceRef: IPyTableSourceRef,
        settings: ApplicationSettingsService.MyState,
    ): IChunkDataLoader {
        return AsyncChunkDataLoader(
            if (tableSourceRef is IPyPatchedStylerRef && settings.pandasStyledFuncValidationEnabled)
                ValidatedChunkEvaluator(tableSourceRef, this)
            else ChunkEvaluator(tableSourceRef),
            this,
        )
    }

    override fun handleValidationProblems(problems: List<StyleFunctionValidationProblem>) {
        ChunkValidationProblemNotification(problems).notify(project)
    }

    override fun handleChunkDataError(region: ChunkRegion, exception: ChunkDataLoaderException) {
        if (isShouldAbortDataFetchingSilentlyException(exception.cause)) return
        ErrorNotification(
            "Error during fetching/processing chunk",
            "${exception.message ?: "Unknown error occurred"}\nfor $region",
            exception
        ).notify(project)
    }

    private fun shouldHandleCreatorResult(creator: TableSourceCreator): Boolean {
        return creator == myRunningCreatorProcess?.creator && !isDisposed
    }

    private fun cleanupRunningCreatorProcess() {
        myRunningCreatorProcess?.let {
            myRunningCreatorProcess = null
            it.progressIndicator.cancel()
        }
    }

    private class RunningCreatorProcess(var creator: TableSourceCreator, var progressIndicator: ProgressIndicator)

    private inner class MyTableSourceCreator(evaluator: IPluginPyValueEvaluator) : TableSourceCreator(evaluator) {

        override fun handleFailure(request: Request, failure: CreateTableSourceFailure) = runInEdt {
            if (shouldHandleCreatorResult(this)) {
                cleanupRunningCreatorProcess()
                updateApplyFilterButtonState()
                when (failure.errorKind) {
                    CreateTableSourceErrorKind.RE_EVAL_DATA_SOURCE_OF_WRONG_TYPE,
                    CreateTableSourceErrorKind.INVALID_FINGERPRINT -> close(CANCEL_EXIT_CODE)

                    CreateTableSourceErrorKind.FILTER_FRAME_EVAL_FAILED ->
                        myFilterInput!!.showErrorMessage("Failed to evaluate filter from expression: ${failure.info}")

                    CreateTableSourceErrorKind.FILTER_FRAME_OF_WRONG_TYPE ->
                        myFilterInput!!.showErrorMessage("Expression returned invalid filter of type: ${failure.info}")

                    CreateTableSourceErrorKind.EVAL_EXCEPTION,
                    CreateTableSourceErrorKind.UNSUPPORTED_DATA_SOURCE_TYPE -> setErrorText(failure.info)
                }
            }
        }

        override fun handleSuccess(request: Request, result: Result) = runInEdt {
            if (shouldHandleCreatorResult(this)) {
                cleanupRunningCreatorProcess()
                updateApplyFilterButtonState()

                myLastDataModelDisposable?.let {
                    myLastDataModelDisposable = null
                    Disposer.dispose(it)
                }

                val settings = ApplicationSettingsService.instance.state
                // note: loader doesn't sync on settings, user has to re-open the dialog after settings are changed
                val chunkLoader = createChunkLoader(result.tableSourceRef, settings)
                ChunkedDataFrameModel(
                    result.tableStructure,
                    result.columnIndexTranslator,
                    chunkLoader,
                    ChunkSize(30, 20),
                    request.info.sortable,
                    request.info.hasIndexLabels,
                ).let { model ->
                    myLastDataModelDisposable = Disposer.newDisposable(disposable, "modelDisposable").also {
                        Disposer.register(it, model)
                        Disposer.register(it, chunkLoader)
                        Disposer.register(it, result.tableSourceRef)
                    }
                    myTable.setDataFrameModel(model)
                }

                if (request.reEvaluate) {
                    val updatedSource = myDataSourceInfo.source.copy(
                        currentStackFrameRefExpr = result.currentStackFrameRefExpr,
                    )
                    myDataSourceInfo = myDataSourceInfo.copy(source = updatedSource)
                }

                if (myDataSourceInfo.filterable) {
                    if (request.filterInputState!!.text.isNotEmpty() && request.filterInputState.text == myFilterInput!!.getText()) {
                        myFilterInput.saveInputInHistory()
                    }
                }

                updateFooterLabel(result.tableStructure)

                // assign focus to the DataFrame table
                // -> allows immediately to use key bindings of the table like sort/scroll/etc.
                IdeFocusManager.getInstance(project).requestFocus(preferredFocusedComponent, true)
            }
        }
    }
}