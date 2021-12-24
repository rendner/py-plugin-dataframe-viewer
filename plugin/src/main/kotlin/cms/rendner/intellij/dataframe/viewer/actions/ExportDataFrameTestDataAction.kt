/*
 * Copyright 2021 cms.rendner (Daniel Schmidt)
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

import cms.rendner.intellij.dataframe.viewer.SystemPropertyEnum
import cms.rendner.intellij.dataframe.viewer.python.PythonQualifiedTypes
import cms.rendner.intellij.dataframe.viewer.python.exporter.ExportTask
import cms.rendner.intellij.dataframe.viewer.python.pycharm.toPluginType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase
import com.jetbrains.python.debugger.PyDebugValue
import java.nio.file.Paths

class ExportDataFrameTestDataAction : AnAction(), DumbAware {

    private val exportDir = System.getProperty(SystemPropertyEnum.EXPORT_TEST_DATA_DIR.key)?.let { Paths.get(it) }
    private var isEnabled = System.getProperty(SystemPropertyEnum.ENABLE_TEST_DATA_EXPORT_ACTION.key, "false") == "true"

    override fun update(event: AnActionEvent) {
        super.update(event)
        event.presentation.isEnabled = isEnabled
        event.presentation.isVisible = exportDir != null && event.project != null && getExportDataValue(event) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        if (exportDir == null) return
        val exportDataValue = getExportDataValue(event) ?: return
        val project: Project = XDebuggerTree.getTree(event.dataContext)!!.project
        isEnabled = false
        ProgressManager.getInstance().run(
            MyExportTask(
                project,
                ExportTask(exportDir, exportDataValue.toPluginType()),
            ) {
                ApplicationManager.getApplication().invokeLater {
                    isEnabled = true
                }
            })
    }

    private fun getExportDataValue(e: AnActionEvent): PyDebugValue? {
        val nodes = XDebuggerTreeActionBase.getSelectedNodes(e.dataContext)
        if (nodes.size == 1) {
            val container = nodes.first().valueContainer
            if (container is PyDebugValue) {
                if (container.qualifiedType == PythonQualifiedTypes.Dict.value && container.name == "export_test_data") {
                    return container
                }
            }
        }
        return null
    }

    private class MyExportTask(
        project: Project,
        private val exportTask: ExportTask,
        private val onFinally: () -> Unit
    ) : Task.Backgroundable(project, "Exporting test cases", true) {
        override fun run(indicator: ProgressIndicator) {
            var failed = false
            try {
                exportTask.run()
            } catch (ex: Exception) {
                failed = true
                if (ex is InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            } finally {
                println("export ${if (failed) "failed" else "done"}")
                onFinally()
            }
        }

    }
}