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
package cms.rendner.intellij.dataframe.viewer.actions

import cms.rendner.intellij.dataframe.viewer.components.DataFrameTable
import cms.rendner.intellij.dataframe.viewer.components.filter.FilterInputFactory
import cms.rendner.intellij.dataframe.viewer.components.filter.IFilterEvalExprBuilder
import cms.rendner.intellij.dataframe.viewer.components.filter.editor.AbstractEditorComponent
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
import cms.rendner.intellij.dataframe.viewer.python.PandasTypes
import cms.rendner.intellij.dataframe.viewer.python.bridge.IPyPatchedStylerRef
import cms.rendner.intellij.dataframe.viewer.python.bridge.PythonPluginCodeInjector
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import cms.rendner.intellij.dataframe.viewer.python.pycharm.PyDebugValueEvalExpr
import cms.rendner.intellij.dataframe.viewer.python.pycharm.toPluginType
import cms.rendner.intellij.dataframe.viewer.python.pycharm.toValueEvalExpr
import cms.rendner.intellij.dataframe.viewer.services.ParentDisposableService
import cms.rendner.intellij.dataframe.viewer.settings.ApplicationSettingsService
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.FontColor
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.jetbrains.python.debugger.PyDebugValue
import java.awt.Dimension
import java.text.NumberFormat
import javax.swing.JComponent


/**
 * Action to open the "StyledDataFrameViewer" dialog from the PyCharm debugger.
 */
class ShowStyledDataFrameAction : AnAction(), DumbAware {

    companion object {
        private val logger = Logger.getInstance(ShowStyledDataFrameAction::class.java)

        // NotificationGroup is registered in plugin.xml
        // https://plugins.jetbrains.com/docs/intellij/notifications.html#notificationgroup-20203-and-later
        private val BALLOON: NotificationGroup = NotificationGroupManager
            .getInstance()
            .getNotificationGroup("cms.rendner.StyledDataFrameViewer")
    }

    override fun update(event: AnActionEvent) {
        super.update(event)
        event.presentation.isEnabledAndVisible = event.project != null && getFrameOrStyler(event) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        if (project.isDisposed) return
        getFrameOrStyler(event)?.let { MyDialog(project, it).show() }
    }

    private fun getFrameOrStyler(e: AnActionEvent): PyDebugValue? {
        XDebuggerUtil.getInstance().getValueContainer(e.dataContext)?.let {
            if (it is PyDebugValue && (PandasTypes.isStyler(it.qualifiedType) || PandasTypes.isDataFrame(it.qualifiedType))) {
                return it
            }
        }
        return null
    }

    private class MyDialog(
        private val project: Project,
        frameOrStyler: PyDebugValue,
    ) :
        DialogWrapper(project, false),
        IChunkValidationProblemHandler,
        IChunkDataLoaderErrorHandler,
        XDebugSessionListener {

        private val myParentDisposable = project.service<ParentDisposableService>()
        private var myDebugValueEvalExpr: PyDebugValueEvalExpr
        private val myEvaluator: IPluginPyValueEvaluator
        private val myDebugSession: XDebugSession
        private var myFilterEvalExprBuilder: IFilterEvalExprBuilder
        private val myFilterInput: AbstractEditorComponent
        private val myDataFrameTable: DataFrameTable
        private val myDataFrameTableFooterLabel = JBLabel("", UIUtil.ComponentStyle.SMALL, FontColor.BRIGHTER)

        private var myLastDataModelDisposable: Disposable? = null
        private var myLastStartedModelDataFetcher: Disposable? = null

        init {
            Disposer.register(myParentDisposable, disposable)

            myDebugSession = XDebuggerManager.getInstance(project).currentSession!!
            myFilterInput = FilterInputFactory.createComponent(project, myDebugSession.currentPosition)
            myFilterEvalExprBuilder = myFilterInput.createFilterExprBuilder()

            myDebugValueEvalExpr = frameOrStyler.toValueEvalExpr()
            myEvaluator = frameOrStyler.toPluginType().evaluator

            isModal = false
            title = myDebugValueEvalExpr.reEvalExpr

            setOKButtonText("Apply Filter")
            setCancelButtonText("Close")
            setCrossClosesWindow(true)

            myDataFrameTable = DataFrameTable()
            myDataFrameTable.preferredSize = Dimension(700, 500)

            init()

            disableApplyFilterButton()

            BackgroundTaskUtil.executeOnPooledThread(disposable) {
                try {
                    PythonPluginCodeInjector.injectIfRequired(myEvaluator)
                } catch (ex: Throwable) {
                    ErrorNotification(
                        BALLOON.displayId,
                        "Initialize plugin code failed",
                        ex.localizedMessage ?: "",
                        ex
                    ).notify(project)
                }

                ApplicationManager.getApplication().invokeLater {
                    if (!isDisposed) {
                        if (myDebugSession.isStopped) {
                            close(CANCEL_EXIT_CODE)
                        } else {
                            myFilterInput.setChangedListener(object : IEditorChangedListener {
                                override fun editorInputChanged() {
                                    updateApplyFilterButtonState()
                                }
                            })
                            myDebugSession.addSessionListener(this)
                            fetchModelData(false)
                        }
                    }
                }
            }
        }

        override fun dispose() {
            myDebugSession.removeSessionListener(this)
            super.dispose()
        }

        private fun disposeLastStartedModelDataFetcherAndModel() {
            myLastStartedModelDataFetcher?.let {
                myLastStartedModelDataFetcher = null
                Disposer.dispose(it)
            }
            myLastDataModelDisposable?.let {
                myLastDataModelDisposable = null
                Disposer.dispose(it)
            }
        }

        override fun sessionStopped() {
            ApplicationManager.getApplication().invokeLater { close(CANCEL_EXIT_CODE) }
        }

        override fun beforeSessionResume() {
            ApplicationManager.getApplication().invokeLater {
                disableApplyFilterButton()
                disposeLastStartedModelDataFetcherAndModel()
                myFilterInput.setSourcePosition(null)
            }
        }

        private fun disableApplyFilterButton() {
            myOKAction.isEnabled = false
        }

        private fun updateApplyFilterButtonState() {
            myOKAction.isEnabled =
                myFilterEvalExprBuilder.build(null) != myFilterInput.getText()
        }

        override fun stackFrameChanged() {
            ApplicationManager.getApplication().invokeLater {
                if (!myDebugValueEvalExpr.canBeReEvaluated()) {
                    setErrorText("Can't re-evaluate '${myDebugValueEvalExpr.reEvalExpr}'")
                } else {
                    myDebugSession.currentPosition.let { myFilterInput.setSourcePosition(it) }
                    fetchModelData(myDebugValueEvalExpr.reEvalExpr != myDebugValueEvalExpr.currentFrameRefExpr)
                }
            }
        }

        override fun doOKAction() {
            myFilterEvalExprBuilder = myFilterInput.createFilterExprBuilder()
            fetchModelData(false)
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
            disposeLastStartedModelDataFetcherAndModel()

            myLastStartedModelDataFetcher = MyModelDataFetcher(myEvaluator).also {
                Disposer.register(disposable, it)
                val request = ModelDataFetcher.Request(
                    myDebugValueEvalExpr,
                    myFilterEvalExprBuilder,
                    reEvaluateDataSource,
                    myDataFrameTable.getDataFrameModel().getDataSourceFingerprint(),
                )
                BackgroundTaskUtil.executeOnPooledThread(it) { it.fetchModelData(request) }
            }
        }

        private fun updateFooterLabel(tableStructure: TableStructure) {
            myDataFrameTableFooterLabel.text = tableStructure.let {
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
                .addComponent(myDataFrameTable)
                .addComponent(myDataFrameTableFooterLabel)
                .addVerticalGap(10)
                .addComponent(myFilterInput.getMainComponent())
                .panel
        }

        override fun getDimensionServiceKey(): String {
            return "#cms.rendner.intellij.dataframe.viewer.python.actions.ShowStyledDataFrameAction"
        }

        override fun getPreferredFocusedComponent(): JComponent {
            return myDataFrameTable.getPreferredFocusedComponent()
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
                logger.warn("Possible incompatible styling function detected for $region.\n$problems")
                ChunkValidationProblemNotification(
                    BALLOON.displayId,
                    region,
                    validationStrategy,
                    problems,
                    details,
                ).notify(project)
            }
        }

        override fun handleChunkDataError(region: ChunkRegion, exception: ChunkDataLoaderException) {
            if (isShouldAbortDataFetchingSilentlyException(exception.cause)) return
            logger.error("Error during fetching/processing chunk for $region.", exception)
            ErrorNotification(
                BALLOON.displayId,
                "Error during fetching/processing chunk",
                "${exception.message ?: "Unknown error occurred"}\nfor $region",
                exception
            ).notify(project)
        }

        private inner class MyModelDataFetcher(evaluator: IPluginPyValueEvaluator) : ModelDataFetcher(evaluator),
            Disposable {

            override fun handleReEvaluateDataSourceException(request: Request, ex: Exception) {
                if (ex is ProcessCanceledException || isShouldAbortDataFetchingSilentlyException(ex)) return
                ApplicationManager.getApplication().invokeLater {
                    if (this == myLastStartedModelDataFetcher && !isDisposed) {
                        updateApplyFilterButtonState()
                        setErrorText(
                            ex.localizedMessage
                                ?: "Couldn't re-evaluate '${request.dataSourceExpr.reEvalExpr}' for current stack frame."
                        )
                    }
                }
            }

            override fun handleNonMatchingFingerprint(request: Request, fingerprint: String) {
                ApplicationManager.getApplication().invokeLater {
                    if (this == myLastStartedModelDataFetcher && !isDisposed) {
                        close(CANCEL_EXIT_CODE)
                    }
                }
            }

            override fun handleFilterFrameEvaluateException(request: Request, ex: Exception) {
                if (ex is ProcessCanceledException || isShouldAbortDataFetchingSilentlyException(ex)) return
                ApplicationManager.getApplication().invokeLater {
                    if (this == myLastStartedModelDataFetcher && !isDisposed) {
                        updateApplyFilterButtonState()
                        ex.localizedMessage?.let { myFilterInput.showErrorMessage(it) }
                    }
                }
            }

            override fun handleEvaluateModelDataException(request: Request, ex: Exception) {
                if (ex is ProcessCanceledException || isShouldAbortDataFetchingSilentlyException(ex)) return
                ApplicationManager.getApplication().invokeLater {
                    if (this == myLastStartedModelDataFetcher && !isDisposed) {
                        updateApplyFilterButtonState()
                        logger.error("Creating DataFrame model failed", ex)

                        ErrorNotification(
                            BALLOON.displayId,
                            "Creating DataFrame model failed",
                            ex.localizedMessage ?: "",
                            ex
                        ).notify(project)
                    }
                }
            }

            override fun handleEvaluateModelDataSuccess(request: Request, result: Result, fetcher: ModelDataFetcher) {
                ApplicationManager.getApplication().invokeLater {
                    if (this == myLastStartedModelDataFetcher && !isDisposed) {
                        updateApplyFilterButtonState()

                        myLastDataModelDisposable?.let {
                            myLastDataModelDisposable = null
                            Disposer.dispose(it)
                        }

                        val settings = ApplicationSettingsService.instance.state
                        // note: loader doesn't sync on settings, user has to re-open the dialog after settings are changed
                        val chunkLoader = createChunkLoader(result.dataSource, settings)
                        ChunkedDataFrameModel(
                            result.tableStructure,
                            result.frameColumnIndexList,
                            result.dataSourceFingerprint,
                            chunkLoader,
                            ChunkSize(30, 20),
                        ).let { model ->
                            myLastDataModelDisposable = Disposer.newDisposable(disposable, "modelDisposable").also {
                                Disposer.register(it, model)
                                Disposer.register(it, chunkLoader)
                            }
                            myDataFrameTable.setDataFrameModel(model)
                        }

                        if (request.reEvaluateDataSource) {
                            myDebugValueEvalExpr = result.updatedDataSourceExpr
                        }

                        request.filterExprBuilder.let {
                            val filterCompText = myFilterInput.getText()
                            if (filterCompText.isNotEmpty() && filterCompText == it.build(null)) {
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

            override fun dispose() {}
        }
    }
}