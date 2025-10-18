/*
 * Copyright 2021-2026 cms.rendner (Daniel Schmidt)
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
import cms.rendner.intellij.dataframe.viewer.python.bridge.*
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.ITableSourceCodeProvider
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyFrameAccessor
import cms.rendner.intellij.dataframe.viewer.python.debugger.PluginPyValue
import cms.rendner.intellij.dataframe.viewer.python.pycharm.PyDebugValueEvalExpr
import cms.rendner.intellij.dataframe.viewer.python.pycharm.isSupported
import cms.rendner.intellij.dataframe.viewer.python.pycharm.toPluginType
import cms.rendner.intellij.dataframe.viewer.python.pycharm.toValueEvalExpr
import cms.rendner.intellij.dataframe.viewer.services.FrameAccessorManager
import cms.rendner.intellij.dataframe.viewer.services.PluginCodeInjector
import cms.rendner.intellij.dataframe.viewer.services.ProjectScopedPluginDisposableService
import cms.rendner.intellij.dataframe.viewer.services.SharedExecutorService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.xdebugger.XDebuggerUtil
import com.jetbrains.python.debugger.*

private class MySelectCodeProviderAction(
    val provider: ITableSourceCodeProvider,
    val transformHint: DataSourceTransformHint?,
) : AbstractShowViewerAction() {
    override fun getApplicableCodeProvider(debugValue: SelectedDebugValue) = provider
    override fun getDataSourceTransformHint() = transformHint
    override fun update(e: AnActionEvent) {
        e.presentation.text = provider.getDataFrameLibrary().moduleName
    }
}

/**
 * Opens a popup in case more than one matching [ITableSourceCodeProvider] are available to transform a data source
 * into a viewable data frame. The user can select the preferred code provider from the popup.
 */
abstract class BaseShowViewerAction : AbstractShowViewerAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val debugValue = getSelectedDebugValue(event) ?: return

        val providers = getApplicableCodeProviders(debugValue)
        if (providers.size > 1) {
            runInEdt { showSelectCodeProviderPopup(event, providers) }
            return
        }

        super.actionPerformed(event)
    }

    protected abstract fun getApplicableCodeProviders(debugValue: SelectedDebugValue): List<ITableSourceCodeProvider>

    final override fun getApplicableCodeProvider(debugValue: SelectedDebugValue): ITableSourceCodeProvider? {
        return getApplicableCodeProviders(debugValue).firstOrNull()
    }

    private fun showSelectCodeProviderPopup(event: AnActionEvent, providers: List<ITableSourceCodeProvider>) {
        val transformHint = getDataSourceTransformHint()
        JBPopupFactory.getInstance().createActionGroupPopup(
            "Select DataFrame Library",
            DefaultActionGroup(providers.map { MySelectCodeProviderAction(it, transformHint) }),
            event.dataContext,
            JBPopupFactory.ActionSelectionAid.NUMBERING,
            false,
            null,
        ).also {
            event.project?.let { p -> it.showCenteredInCurrentWindow(p) } ?: it.showInBestPositionFor(event.dataContext)
        }
    }
}

class SelectedDebugValue(private val pyDebugValue: PyDebugValue) {
    val value: PluginPyValue = pyDebugValue.toPluginType()
    val frameAccessor: IPluginPyFrameAccessor = pyDebugValue.frameAccessor.let {
        it.project!!.service<FrameAccessorManager>().resolve(it)
    }
    val project = pyDebugValue.frameAccessor.project
    fun getValueEvalExpr(): PyDebugValueEvalExpr = pyDebugValue.toValueEvalExpr()
}

abstract class AbstractShowViewerAction : AnAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    protected abstract fun getApplicableCodeProvider(debugValue: SelectedDebugValue): ITableSourceCodeProvider?

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        if (project.isDisposed) return
        val debugValue = getSelectedDebugValue(event) ?: return

        val codeProvider = getApplicableCodeProvider(debugValue)
            ?: throw IllegalStateException("No codeProvider found for ${debugValue.value.qualifiedType}")

        val dataSourceTransformHint = getDataSourceTransformHint()
        val projectScopedDisposable = project.service<ProjectScopedPluginDisposableService>()

        project.service<SharedExecutorService>().execute {
            val dataSourceInfo = try {
                project.service<PluginCodeInjector>().injectIfRequired(debugValue.frameAccessor, codeProvider)
                codeProvider.createSourceInfo(debugValue.getValueEvalExpr())
            } catch (ex: Throwable) {
                runInEdt {
                    if (!projectScopedDisposable.isDisposed) {
                        ErrorNotification(
                            "Initialize plugin code failed",
                            ex.localizedMessage ?: "",
                            ex
                        ).notify(project)
                    }
                }
                return@execute
            }

            val tableSourceRef = try {
                TableSourceFactory.create(
                    debugValue.frameAccessor,
                    dataSourceInfo,
                    CreateTableSourceConfig(dataSourceTransformHint = dataSourceTransformHint),
                )
            } catch (ex: Throwable) {
                runInEdt {
                    if (!projectScopedDisposable.isDisposed) {
                    ErrorNotification(
                        "Create TableSource failed",
                        ex.localizedMessage ?: "",
                        ex,
                        ).notify(project)
                    }
                }
                return@execute
            }

            runInEdt {
                if (!debugValue.frameAccessor.isAlive() ||
                    projectScopedDisposable.isDisposed ||
                    project.isDisposed) {
                    return@runInEdt
                }

                DataFrameViewerDialog(project, tableSourceRef).apply {
                    // Close dialog when plugin is unloaded or project is closed.
                    // Using "disposable" here as child would not lead to a closed dialog.
                    Disposer.register(projectScopedDisposable, ::closeWithAutoDispose)

                    // Use the dialog "disposable" only as parentDisposable!
                    Disposer.register(disposable) {
                        BackgroundTaskUtil.executeOnPooledThread(projectScopedDisposable) {
                            tableSourceRef.disposeTableSource()
                        }
                    }

                    show()
                }
            }
        }
    }

    protected open fun getDataSourceTransformHint(): DataSourceTransformHint? = null

    protected fun getSelectedDebugValue(event: AnActionEvent): SelectedDebugValue? {
        return XDebuggerUtil.getInstance().getValueContainer(event.dataContext)?.let {
            if (it is PyDebugValue && it.frameAccessor.isSupported()) {
                SelectedDebugValue(it)
            } else null
        }
    }
}
