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

import cms.rendner.intellij.dataframe.viewer.python.PythonQualifiedTypes
import cms.rendner.intellij.dataframe.viewer.python.bridge.DataSourceTransformHint
import cms.rendner.intellij.dataframe.viewer.python.bridge.PandasAvailableInSessionProvider
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.python.debugger.PyDebugValue

open class ShowViewerForDictAction: AbstractShowViewerAction() {

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null && couldCreateFrameFromSelectedValue(event)
    }

    private fun couldCreateFrameFromSelectedValue(event: AnActionEvent): Boolean {
        val value = getSelectedDebugValue(event) ?: return false
        if (value.qualifiedType == PythonQualifiedTypes.DICT) {
            val project = event.project ?: return false
            val session = XDebuggerManager.getInstance(project).currentSession ?: return false
            return PandasAvailableInSessionProvider.isAvailable(session) == true
        }
        return false
    }
}

class ShowViewerForDictWithKeysAsRowsAction: ShowViewerForDictAction() {
    override fun getDataSourceTransformHint(value: PyDebugValue) = DataSourceTransformHint.DictKeysAsRows
}