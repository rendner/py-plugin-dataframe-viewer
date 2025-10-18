/*
 * Copyright 2021-2025 cms.rendner (Daniel Schmidt)
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

import cms.rendner.intellij.dataframe.viewer.python.DataFrameLibrary
import cms.rendner.intellij.dataframe.viewer.python.InstalledDataFrameLibrariesExpr
import cms.rendner.intellij.dataframe.viewer.python.PythonQualifiedTypes
import cms.rendner.intellij.dataframe.viewer.python.bridge.DataSourceTransformHint
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.ITableSourceCodeProvider
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.TableSourceCodeProviderRegistry
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyFrameAccessor
import cms.rendner.intellij.dataframe.viewer.services.InstalledDataFrameLibrariesProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

open class ShowViewerForDictAction : BaseShowViewerAction() {
    // Has to be EDT. Otherwise, triggering this action via the mapped key binding,
    // for the console based Python evaluator, inside the "update" method
    // freezes the whole IDE. (bug is reported to Jetbrains)
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = couldCreateFrameFromSelectedValue(event)
    }

    override fun getApplicableCodeProviders(debugValue: SelectedDebugValue): List<ITableSourceCodeProvider> {
        debugValue.value.qualifiedType.let { qType ->
            if (qType == PythonQualifiedTypes.DICT) {
                val codeProviders = TableSourceCodeProviderRegistry.getApplicableProviders(qType)
                if (codeProviders.isNotEmpty()) {
                    val project = debugValue.project ?: return emptyList()
                    project.service<InstalledDataFrameLibrariesProvider>().getDetectedLibraries(debugValue.frameAccessor)?.let { libs ->
                        return codeProviders.filter { libs.contains(it.getDataFrameLibrary()) }
                    }
                }
            }
        }
        return emptyList()
    }

    private fun couldCreateFrameFromSelectedValue(event: AnActionEvent): Boolean {
        val debugValue = getSelectedDebugValue(event) ?: return false
        return debugValue.value.qualifiedType.let { qType ->
            if (qType == PythonQualifiedTypes.DICT) {
                val project = debugValue.project ?: return false
                checkInstalledDataFrameLibraries(project, debugValue.frameAccessor)
                getApplicableCodeProviders(debugValue).isNotEmpty()
            } else false
        }
    }

    private fun checkInstalledDataFrameLibraries(project: Project, frameAccessor: IPluginPyFrameAccessor): List<DataFrameLibrary> {
        val libraries = try {
            val result = frameAccessor.evaluate(InstalledDataFrameLibrariesExpr.getExpression())
            InstalledDataFrameLibrariesExpr.convertResult(result.value!!)
        } catch (_: Throwable) {
            emptyList()
        }

        return libraries.also {
            project.service<InstalledDataFrameLibrariesProvider>().setDetectedLibraries(frameAccessor, it)
        }
    }
}

class ShowViewerForDictWithKeysAsRowsAction : ShowViewerForDictAction() {
    override fun getApplicableCodeProviders(debugValue: SelectedDebugValue): List<ITableSourceCodeProvider> {
        return super.getApplicableCodeProviders(debugValue).filter {
            it.getDataFrameLibrary() == DataFrameLibrary.PANDAS
        }
    }

    override fun getDataSourceTransformHint() = DataSourceTransformHint.DictKeysAsRows
}
