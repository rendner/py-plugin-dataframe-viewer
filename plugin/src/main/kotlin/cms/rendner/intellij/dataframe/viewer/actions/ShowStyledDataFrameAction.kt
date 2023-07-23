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
package cms.rendner.intellij.dataframe.viewer.actions

import cms.rendner.intellij.dataframe.viewer.components.DataFrameViewerDialog
import cms.rendner.intellij.dataframe.viewer.notifications.ErrorNotification
import cms.rendner.intellij.dataframe.viewer.python.PandasTypes
import cms.rendner.intellij.dataframe.viewer.python.bridge.PythonPluginCodeInjector
import cms.rendner.intellij.dataframe.viewer.python.pycharm.toPluginType
import cms.rendner.intellij.dataframe.viewer.services.ParentDisposableService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.jetbrains.python.debugger.PyDebugValue


/**
 * Action to open the "DataFrameViewerDialog" from the PyCharm debugger.
 */
class ShowStyledDataFrameAction : AnAction(), DumbAware {

    override fun update(event: AnActionEvent) {
        super.update(event)
        event.presentation.isEnabledAndVisible = event.project != null && getFrameOrStyler(event) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        if (project.isDisposed) return
        getFrameOrStyler(event)?.let { pyDebugValue ->

            val parentDisposable = project.service<ParentDisposableService>()
            val debugSession = XDebuggerManager.getInstance(project).currentSession!!
            BackgroundTaskUtil.executeOnPooledThread(parentDisposable) {
                try {
                    PythonPluginCodeInjector.injectIfRequired(pyDebugValue.toPluginType().evaluator)
                } catch (ex: Throwable) {
                    ErrorNotification(
                        "Initialize plugin code failed",
                        ex.localizedMessage ?: "",
                        ex
                    ).notify(project)
                    return@executeOnPooledThread
                }

                ApplicationManager.getApplication().invokeLater {
                    if (debugSession.isStopped || parentDisposable.isDisposed || project.isDisposed) return@invokeLater
                    DataFrameViewerDialog(parentDisposable, debugSession, pyDebugValue).apply {
                        if (!debugSession.isStopped) {
                            startListeningAndFetchInitialData()
                            show()
                        } else {
                            close(DialogWrapper.CANCEL_EXIT_CODE)
                        }
                    }
                }
            }
        }
    }

    private fun getFrameOrStyler(e: AnActionEvent): PyDebugValue? {
        XDebuggerUtil.getInstance().getValueContainer(e.dataContext)?.let {
            if (it is PyDebugValue && (PandasTypes.isStyler(it.qualifiedType) || PandasTypes.isDataFrame(it.qualifiedType))) {
                return it
            }
        }
        return null
    }
}