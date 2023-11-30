/*
 * Copyright 2021-2023 cms.rendner (Daniel Schmidt)
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
import cms.rendner.intellij.dataframe.viewer.python.IEvalAvailableDataFrameLibraries
import cms.rendner.intellij.dataframe.viewer.python.PythonQualifiedTypes
import cms.rendner.intellij.dataframe.viewer.python.bridge.DataSourceTransformHint
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.ITableSourceCodeProvider
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.TableSourceCodeProviderRegistry
import cms.rendner.intellij.dataframe.viewer.services.AvailableDataFrameLibrariesProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XValue
import com.jetbrains.python.debugger.PyDebugValue
import java.util.concurrent.CountDownLatch

open class ShowViewerForDictAction : BaseShowViewerAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = couldCreateFrameFromSelectedValue(event)
    }

    override fun getApplicableCodeProviders(event: AnActionEvent, dataSource: PyDebugValue): List<ITableSourceCodeProvider> {
        dataSource.qualifiedType?.let { qType ->
            if (qType == PythonQualifiedTypes.DICT) {
                val codeProviders = TableSourceCodeProviderRegistry.getApplicableProviders(qType)
                if (codeProviders.isNotEmpty()) {
                    val project = event.project ?: return emptyList()
                    val session = XDebuggerManager.getInstance(project).currentSession ?: return emptyList()
                    project.service<AvailableDataFrameLibrariesProvider>().getLibraries(session)?.let { libs ->
                        return codeProviders.filter { libs.contains(it.getDataFrameLibrary()) }
                    }
                }
            }
        }
        return emptyList()
    }

    private fun couldCreateFrameFromSelectedValue(event: AnActionEvent): Boolean {
        val debugValue = getSelectedDebugValue(event) ?: return false
        return debugValue.qualifiedType.let { qType ->
            if (qType == PythonQualifiedTypes.DICT) {
                val project = event.project ?: return false
                val session = XDebuggerManager.getInstance(project).currentSession ?: return false
                if (!project.service<AvailableDataFrameLibrariesProvider>().hasResult(session)) {
                    MyDataFrameLibrariesEvaluator().evaluate(session)
                }
                getApplicableCodeProviders(event, debugValue).isNotEmpty()
            } else false
        }
    }

    private class MyDataFrameLibrariesEvaluator : IEvalAvailableDataFrameLibraries {
        fun evaluate(session: XDebugSession) {
            if (!session.isPaused) return
            val evaluator = session.debugProcess.evaluator ?: return

            val evalLatch = CountDownLatch(1)

            evaluator.evaluate(
                getEvalExpression(),
                object : XDebuggerEvaluator.XEvaluationCallback {
                    override fun errorOccurred(errorMessage: String) {
                        try {
                            // Only a paused session can evaluate expressions.
                            // If not paused, ignore error.
                            if (session.isPaused) {
                                session.project.service<AvailableDataFrameLibrariesProvider>()
                                    .setLibraries(session, emptyList())
                            }
                        } finally {
                            evalLatch.countDown()
                        }
                    }

                    override fun evaluated(result: XValue) {
                        try {
                            session.project.service<AvailableDataFrameLibrariesProvider>().setLibraries(
                                session,
                                if (result is PyDebugValue) convertResult(result.value!!) else emptyList()
                            )
                        } finally {
                            evalLatch.countDown()
                        }
                    }
                },
                null,
            )

            evalLatch.await()
        }
    }
}

class ShowViewerForDictWithKeysAsRowsAction : ShowViewerForDictAction() {
    override fun getApplicableCodeProviders(
        event: AnActionEvent,
        dataSource: PyDebugValue
    ): List<ITableSourceCodeProvider> {
        return super.getApplicableCodeProviders(event, dataSource).filter {
            it.getDataFrameLibrary() == DataFrameLibrary.PANDAS
        }
    }

    override fun getDataSourceTransformHint() = DataSourceTransformHint.DictKeysAsRows
}