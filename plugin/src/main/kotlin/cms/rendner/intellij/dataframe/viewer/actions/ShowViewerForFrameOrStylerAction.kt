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
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.ITableSourceCodeProvider
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.TableSourceCodeProviderRegistry
import com.intellij.openapi.actionSystem.AnActionEvent
import com.jetbrains.python.debugger.PyDebugValue


class ShowViewerForFrameOrStylerAction : BaseShowViewerAction() {
    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = getSelectedDebugValue(event)?.let {
            getApplicableCodeProviders(event, it).isNotEmpty()
        } ?: false
    }

    override fun getApplicableCodeProviders(event: AnActionEvent, dataSource: PyDebugValue): List<ITableSourceCodeProvider> {
        dataSource.qualifiedType?.let {
            if (it != PythonQualifiedTypes.DICT) {
                return TableSourceCodeProviderRegistry.getApplicableProviders(it)
            }
        }
        return emptyList()
    }
}