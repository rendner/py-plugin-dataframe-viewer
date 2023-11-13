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

import cms.rendner.intellij.dataframe.viewer.python.PandasTypes
import com.intellij.openapi.actionSystem.AnActionEvent


class ShowViewerForFrameOrStylerAction : AbstractShowViewerAction() {

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null && selectedItemIsFrameOrStyler(event)
    }

    private fun selectedItemIsFrameOrStyler(event: AnActionEvent): Boolean {
        return getSelectedDebugValue(event)?.let {
            return PandasTypes.isStyler(it.qualifiedType) || PandasTypes.isDataFrame(it.qualifiedType)
        } ?: false
    }
}