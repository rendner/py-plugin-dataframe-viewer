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
import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkSize
import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkedDataFrameModel
import cms.rendner.intellij.dataframe.viewer.models.chunked.evaluator.ChunkEvaluator
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.AsyncChunkDataLoader
import cms.rendner.intellij.dataframe.viewer.notifications.UserNotifier
import cms.rendner.intellij.dataframe.viewer.python.bridge.IPyPatchedStylerRef
import cms.rendner.intellij.dataframe.viewer.python.bridge.PythonCodeBridge
import cms.rendner.intellij.dataframe.viewer.python.pycharm.isDataFrame
import cms.rendner.intellij.dataframe.viewer.python.pycharm.isStyler
import cms.rendner.intellij.dataframe.viewer.python.pycharm.toPluginType
import cms.rendner.intellij.dataframe.viewer.services.ParentDisposableService
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
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase
import com.jetbrains.python.debugger.PyDebugValue
import java.awt.Component
import java.awt.Dimension
import javax.swing.*

class ShowStyledDataFrameAction : AnAction(), DumbAware {

    companion object {
        private val logger = Logger.getInstance(ShowStyledDataFrameAction::class.java)
    }

    override fun update(event: AnActionEvent) {
        super.update(event)
        event.presentation.isEnabledAndVisible = event.project != null && getFrameOrStyler(event) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val frameOrStyler = getFrameOrStyler(event)
        if (frameOrStyler !== null) {
            val project: Project = XDebuggerTree.getTree(event.dataContext)!!.project
            val dialog = MyDialog(project)
            dialog.createModelFrom(frameOrStyler)
            dialog.title = "Styled DataFrame"
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

    private class MyDialog(project: Project) :
        DialogWrapper(project, false) {
        private val myDataFrameTable: DataFrameTable
        private val myStatusLabel = JLabel()
        private val myParentDisposable = project.service<ParentDisposableService>()
        private val myUserNotifier = UserNotifier(project)

        init {
            Disposer.register(myParentDisposable, disposable)

            isModal = false

            setOKButtonText("Close")
            // "Alt" + "c" triggers OK action (esc also closes the window)
            setOKButtonMnemonic('C'.toInt())

            setCrossClosesWindow(true)

            myDataFrameTable = DataFrameTable()
            myDataFrameTable.preferredSize = Dimension(700, 500)

            init()
        }

        fun createModelFrom(frameOrStyler: PyDebugValue) {
            BackgroundTaskUtil.executeOnPooledThread(myParentDisposable) {
                var patchedStyler: IPyPatchedStylerRef? = null
                var model: IDataFrameModel? = null
                try {
                    val pythonBridge = PythonCodeBridge()
                    patchedStyler = pythonBridge.createPatchedStyler(
                        frameOrStyler.toPluginType()
                    )

                    model = createChunkedModel(patchedStyler, myUserNotifier)

                    ApplicationManager.getApplication().invokeLater {
                        if (!Disposer.isDisposed(disposable)) {
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
                    myUserNotifier.error("Can't display content from Styler", "Creating model failed", ex)

                    patchedStyler?.dispose()
                    model?.dispose()
                }
            }
        }

        override fun createCenterPanel(): JComponent {
            val result = JPanel()
            result.layout = BoxLayout(result, BoxLayout.Y_AXIS)

            myStatusLabel.alignmentX = Component.LEFT_ALIGNMENT
            myDataFrameTable.alignmentX = Component.LEFT_ALIGNMENT

            result.add(myStatusLabel)
            result.add(myDataFrameTable)

            return result
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

        /**
         * The chunked model evaluates the underlying dataframe in slices.
         */
        fun createChunkedModel(patchedStyler: IPyPatchedStylerRef, notifier: UserNotifier? = null): IDataFrameModel {
            return ChunkedDataFrameModel(
                patchedStyler.evaluateTableStructure(),
                AsyncChunkDataLoader(
                    ChunkEvaluator(
                        patchedStyler,
                        ChunkSize(60, 20)
                    ),
                    8,
                    notifier
                )
            )
        }
    }
}