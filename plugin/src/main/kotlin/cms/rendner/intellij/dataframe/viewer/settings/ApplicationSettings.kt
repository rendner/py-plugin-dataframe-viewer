/*
 * Copyright 2022 cms.rendner (Daniel Schmidt)
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
package cms.rendner.intellij.dataframe.viewer.settings

import com.intellij.openapi.options.Configurable
import javax.swing.JComponent


/**
 * Allows the user to modify some plugin settings via the IntelliJ settings.
 */
class ApplicationSettings : Configurable {

    override fun getDisplayName() = "Styled DataFrame Viewer"

    /**
     * The view to modify the settings.
     *
     * Note:
     * The IntelliJ Platform may instantiate a [Configurable] implementation on a background thread,
     * so creating Swing components in a constructor can degrade UI responsiveness.
     */
    private var mySettingsComponent: SettingsComponent? = null

    override fun createComponent():JComponent  {
        return SettingsComponent().also { mySettingsComponent = it }.getPanel()
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        return mySettingsComponent?.getPreferredFocusedComponent()
    }

    override fun reset() {
        mySettingsComponent?.let {
            val settings = ApplicationSettingsService.instance.state
            it.validationStrategyType = settings.validationStrategyType
            it.fsUseFilterInputFromInternalApi = settings.fsUseFilterInputFromInternalApi
        }
    }

    override fun isModified(): Boolean {
        return mySettingsComponent?.let {
            val settings = ApplicationSettingsService.instance.state
            it.validationStrategyType !== settings.validationStrategyType ||
            it.fsUseFilterInputFromInternalApi != settings.fsUseFilterInputFromInternalApi
        } ?: false
    }

    override fun apply() {
        mySettingsComponent?.let {
            val settings = ApplicationSettingsService.instance.state
            settings.validationStrategyType = it.validationStrategyType
            settings.fsUseFilterInputFromInternalApi = it.fsUseFilterInputFromInternalApi
        }
    }

    override fun disposeUIResources() {
        mySettingsComponent = null
    }
}