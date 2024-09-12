/*
 * Copyright 2021-2024 cms.rendner (Daniel Schmidt)
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
package cms.rendner.intellij.dataframe.viewer.components.filter

import cms.rendner.intellij.dataframe.viewer.settings.ApplicationSettingsService
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XSourcePosition

class FilterInputFactory {

    companion object {
        private val logger = Logger.getInstance(FilterInputFactory::class.java)

        fun createComponent(
            project: Project,
            completionContributor: IFilterInputCompletionContributor,
            sourcePosition: XSourcePosition?,
            ): AbstractFilterInput {
            return if (!ApplicationSettingsService.instance.state.filterInputFromInternalApi) {
                DefaultFilterInput(project, completionContributor, sourcePosition)
            } else try {
                Class.forName("cms.rendner.intellij.dataframe.viewer.components.filter.InternalApiFilterInput")
                    .getConstructor(
                        Project::class.java,
                        IFilterInputCompletionContributor::class.java,
                        XSourcePosition::class.java,
                    )
                    .newInstance(
                        project,
                        completionContributor,
                        sourcePosition,
                    ) as AbstractFilterInput
            } catch (e: Exception) {
                logger.warn("Creating InternalApiFilterInput failed, using default component", e)
                DefaultFilterInput(project, completionContributor, sourcePosition)
            }
        }
    }
}