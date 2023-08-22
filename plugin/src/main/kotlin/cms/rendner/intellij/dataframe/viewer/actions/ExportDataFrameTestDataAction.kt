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

import cms.rendner.intellij.dataframe.viewer.SystemPropertyKey
import cms.rendner.intellij.dataframe.viewer.notifications.ErrorNotification
import cms.rendner.intellij.dataframe.viewer.python.PythonQualifiedTypes
import cms.rendner.intellij.dataframe.viewer.python.bridge.PandasVersionInSessionProvider
import cms.rendner.intellij.dataframe.viewer.python.bridge.PythonPluginCodeInjector
import cms.rendner.intellij.dataframe.viewer.python.exporter.ExportTask
import cms.rendner.intellij.dataframe.viewer.python.pycharm.toPluginType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.jetbrains.python.debugger.PyDebugValue
import java.nio.file.Paths


/**
 * Helper action, used to dump test data from the "html_from_styler" Python projects via the PyCharm debugger.
 */
class ExportDataFrameTestDataAction : AnAction(), DumbAware {

    private val rootExportDir = System.getProperty(SystemPropertyKey.EXPORT_TEST_DATA_DIR)?.let { Paths.get(it) }
    private var isEnabled = System.getProperty(SystemPropertyKey.ENABLE_TEST_DATA_EXPORT_ACTION, "false") == "true"

    override fun update(event: AnActionEvent) {
        super.update(event)
        event.presentation.isEnabled = isEnabled
        event.presentation.isVisible = rootExportDir != null && event.project != null && getExportDataValue(event) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        if (rootExportDir == null) return
        val project = event.project ?: return
        val exportDataValue = getExportDataValue(event) ?: return
        val debugSession = XDebuggerManager.getInstance(project).currentSession ?: return
        val pandasVersion = PandasVersionInSessionProvider.getVersion(debugSession)

        if (pandasVersion == null) {
            ErrorNotification(
                "No pandasVersion for debug session found",
                "Can't export test data because of missing pandasVersion.",
                null,
            ).notify(project)
            return
        }

        isEnabled = false
        val pluginValue = exportDataValue.toPluginType()
        ProgressManager.getInstance().run(
            object: Task.Backgroundable(project, "Exporting test data", true) {
                override fun run(indicator: ProgressIndicator) {
                    PythonPluginCodeInjector.injectIfRequired(pandasVersion, pluginValue.evaluator)
                    ExportTask(rootExportDir, pandasVersion, pluginValue).run()
                }

                override fun onFinished() {
                    println("export done")
                    isEnabled = true
                }

                override fun onThrowable(error: Throwable) {
                    println("export failed")
                    ErrorNotification("Exception during export", error.localizedMessage, error).notify(project)
                }

                override fun onCancel() {
                    println("export canceled")
                }
            }
        )
    }

    private fun getExportDataValue(e: AnActionEvent): PyDebugValue? {
        XDebuggerUtil.getInstance().getValueContainer(e.dataContext)?.let {
            if (it is PyDebugValue) {
                if (it.qualifiedType == PythonQualifiedTypes.List && it.name == "export_test_data") {
                    return it
                }
            }
        }
        return null
    }
}