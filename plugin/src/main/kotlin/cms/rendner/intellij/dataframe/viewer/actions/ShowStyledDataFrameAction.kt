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
import cms.rendner.intellij.dataframe.viewer.models.IDataFrameModel
import cms.rendner.intellij.dataframe.viewer.models.chunked.*
import cms.rendner.intellij.dataframe.viewer.models.chunked.evaluator.ChunkEvaluator
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.AsyncChunkDataLoader
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.IChunkDataLoaderErrorHandler
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.*
import cms.rendner.intellij.dataframe.viewer.notifications.ChunkValidationProblemNotification
import cms.rendner.intellij.dataframe.viewer.notifications.ErrorNotification
import cms.rendner.intellij.dataframe.viewer.python.bridge.IPyPatchedStylerRef
import cms.rendner.intellij.dataframe.viewer.python.bridge.PythonCodeBridge
import cms.rendner.intellij.dataframe.viewer.python.pycharm.isDataFrame
import cms.rendner.intellij.dataframe.viewer.python.pycharm.isStyler
import cms.rendner.intellij.dataframe.viewer.python.pycharm.toPluginType
import cms.rendner.intellij.dataframe.viewer.services.ParentDisposableService
import cms.rendner.intellij.dataframe.viewer.settings.ApplicationSettingsService
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase
import com.jetbrains.python.debugger.PyDebugValue
import java.awt.Component
import java.awt.Dimension
import javax.swing.*

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
        val frameOrStyler = getFrameOrStyler(event)
        if (frameOrStyler !== null) {
            val settings = ApplicationSettingsService.instance.state
            val dialog = MyDialog(project)
            // note: dialog doesn't sync on settings user has to re-open the dialog after the settings were changed
            dialog.createModelFrom(frameOrStyler, settings.validationStrategyType, settings.fsLoadNewDataStructure)
            dialog.show()
        }
    }

    private fun getFrameOrStyler(e: AnActionEvent): PyDebugValue? {
        val nodes = XDebuggerTreeActionBase.getSelectedNodes(e.dataContext)
        if (nodes.size == 1) {
            val container = nodes.first().valueContainer
            if (container is PyDebugValue) {
                if (container.isDataFrame() || container.isStyler()) {
                    return container
                }
            }
        }
        return null
    }

    private class MyDialog(private val project: Project) :
        DialogWrapper(project, false),
        IChunkValidationProblemHandler,
        IChunkDataLoaderErrorHandler {

        private val myDataFrameTable: DataFrameTable
        private val myParentDisposable = project.service<ParentDisposableService>()

        init {
            Disposer.register(myParentDisposable, disposable)

            isModal = false
            title = "Styled DataFrame"

            setOKButtonText("Close")
            // "Alt" + "c" triggers OK action (esc also closes the window)
            setOKButtonMnemonic('C'.toInt())

            setCrossClosesWindow(true)

            myDataFrameTable = DataFrameTable()
            myDataFrameTable.preferredSize = Dimension(700, 500)

            init()
        }

        fun createModelFrom(
            frameOrStyler: PyDebugValue,
            validationStrategyType: ValidationStrategyType,
            loadNewDataStructure: Boolean,
        ) {
            BackgroundTaskUtil.executeOnPooledThread(myParentDisposable) {
                var patchedStyler: IPyPatchedStylerRef? = null
                var model: IDataFrameModel? = null
                try {
                    val pythonBridge = PythonCodeBridge()
                    patchedStyler = pythonBridge.createPatchedStyler(
                        frameOrStyler.toPluginType()
                    )

                    model = createChunkedModel(patchedStyler, validationStrategyType, loadNewDataStructure)

                    ApplicationManager.getApplication().invokeLater {
                        if (!isDisposed) {
                            Disposer.register(disposable, model)
                            Disposer.register(disposable, patchedStyler)
                            myDataFrameTable.setDataFrameModel(model)
                        } else {
                            patchedStyler.dispose()
                            model.dispose()
                        }
                    }
                } catch (ex: Exception) {
                    logger.error("Creating DataFrame model failed", ex)

                    ErrorNotification(
                        BALLOON.displayId,
                        "Creating DataFrame model failed",
                        ex.localizedMessage,
                        ex
                    ).notify(project)

                    patchedStyler?.dispose()
                    model?.dispose()
                }
            }
        }

        override fun createCenterPanel(): JComponent {
            return JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                myDataFrameTable.alignmentX = Component.LEFT_ALIGNMENT
                add(myDataFrameTable)
            }
        }

        override fun createActions(): Array<Action> {
            return arrayOf(okAction)
        }

        override fun getDimensionServiceKey(): String {
            return "#cms.rendner.intellij.dataframe.viewer.python.actions.ShowStyledDataFrameAction"
        }

        override fun getPreferredFocusedComponent(): JComponent {
            return myDataFrameTable
        }

        private fun createChunkedModel(
            patchedStyler: IPyPatchedStylerRef,
            validationStrategyType: ValidationStrategyType,
            loadNewDataStructure: Boolean,
        ): IDataFrameModel {
            val loader = AsyncChunkDataLoader(
                ChunkEvaluator(patchedStyler),
                loadNewDataStructure,
                createChunkValidator(patchedStyler, validationStrategyType),
                this,
            )
            loader.setMaxWaitingRequests(8)
            return ChunkedDataFrameModel(
                patchedStyler.evaluateTableStructure(),
                loader,
                ChunkSize(30, 20),
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

        override fun handleChunkDataError(region: ChunkRegion, throwable: Throwable) {
            logger.error("Error during fetching/processing chunk for $region.", throwable)
            ErrorNotification(
                BALLOON.displayId,
                "Error during fetching/processing chunk",
                "${throwable.message ?: "Unknown error occurred"}\nfor $region",
                throwable
            ).notify(project)
        }
    }
}