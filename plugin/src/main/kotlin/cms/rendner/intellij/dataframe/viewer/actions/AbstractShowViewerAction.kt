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
import cms.rendner.intellij.dataframe.viewer.python.bridge.DataSourceToFrameHint
import cms.rendner.intellij.dataframe.viewer.python.bridge.PythonPluginCodeInjector
import cms.rendner.intellij.dataframe.viewer.python.pycharm.toPluginType
import cms.rendner.intellij.dataframe.viewer.services.ParentDisposableService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.jetbrains.python.debugger.PyDebugValue

/**
 * Action to open the "DataFrameViewerDialog" from the PyCharm debugger.
 */
abstract class AbstractShowViewerAction: AnAction(), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        if (project.isDisposed) return
        val dataSource = getSelectedDebugValue(event) ?: return
        val debugSession = XDebuggerManager.getInstance(project).currentSession ?: return
        val parentDisposable = project.service<ParentDisposableService>()

        BackgroundTaskUtil.executeOnPooledThread(parentDisposable) {
            try {
                PythonPluginCodeInjector.injectIfRequired(dataSource.toPluginType().evaluator)
            } catch (ex: Throwable) {
                ErrorNotification(
                    "Initialize plugin code failed",
                    ex.localizedMessage ?: "",
                    ex
                ).notify(project)
                return@executeOnPooledThread
            }

            runInEdt {
                if (debugSession.isStopped || parentDisposable.isDisposed || project.isDisposed) return@runInEdt
                DataFrameViewerDialog(debugSession, dataSource, getDataSourceToFrameHint(dataSource)).apply {
                    if (!debugSession.isStopped) {
                        Disposer.register(parentDisposable, disposable)
                        startListeningAndFetchInitialData()
                        show()
                    } else {
                        close(DialogWrapper.CANCEL_EXIT_CODE)
                    }
                }
            }
        }
    }

    protected open fun getDataSourceToFrameHint(value: PyDebugValue): DataSourceToFrameHint? = null

    protected fun getSelectedDebugValue(event: AnActionEvent): PyDebugValue? {
        return XDebuggerUtil.getInstance().getValueContainer(event.dataContext)?.let {
            if (it is PyDebugValue) it else null
        }
    }
}