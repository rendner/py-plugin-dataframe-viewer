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
package cms.rendner.intellij.dataframe.viewer.settings

import com.intellij.ui.border.IdeaTitledBorder
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import java.awt.Insets
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Provides the settings view.
 */
class SettingsComponent {

    private val myPandasStyledFuncValidationEnabledCheckBox =
        JBCheckBox("Validate pandas style functions")
    private val myFilterInputFromInternalApiCheckBox =
        JBCheckBox("Use editor from internal IntelliJ API")
    private val myFilterInputWithAdditionCodeCompletion =
        JBCheckBox("Code completion provided by plugin")
    private val myFilterInputWithRuntimeCodeCompletionInPythonConsole =
        JBCheckBox("Runtime code completion (Python Console)")

    private val myPanel: JPanel

    var pandasStyledFuncValidationEnabled: Boolean
        get() = myPandasStyledFuncValidationEnabledCheckBox.isSelected
        set(value) {
            myPandasStyledFuncValidationEnabledCheckBox.isSelected = value
        }

    var filterInputFromInternalApi: Boolean
        get() = myFilterInputFromInternalApiCheckBox.isSelected
        set(value) {
            myFilterInputFromInternalApiCheckBox.isSelected = value
        }

    var filterInputWithAdditionCodeCompletion: Boolean
        get() = myFilterInputWithAdditionCodeCompletion.isSelected
        set(value) {
            myFilterInputWithAdditionCodeCompletion.isSelected = value
        }

    var filterInputWithRuntimeCodeCompletionInPythonConsole: Boolean
        get() = myFilterInputWithRuntimeCodeCompletionInPythonConsole.isSelected
        set(value) {
            myFilterInputWithRuntimeCodeCompletionInPythonConsole.isSelected = value
        }


    init {
        val dataFetchingSettingsPanel = FormBuilder.createFormBuilder()
            .addComponent(myPandasStyledFuncValidationEnabledCheckBox)
            .addTooltip("Validates that styling functions return stable results for chunked results. Only used for pandas.Styler.")
            .panel
        dataFetchingSettingsPanel.border = createTitleBorder("Data fetching")

        val filterInputSettingsPanel = FormBuilder.createFormBuilder()
            .addComponent(myFilterInputFromInternalApiCheckBox)
            .addTooltip("Stores a filter history. May not be available due to internal API changes.")
            .addComponent(myFilterInputWithAdditionCodeCompletion)
            .addTooltip("Adds completion for pandas DataFrame column names.")
            .addComponent(myFilterInputWithRuntimeCodeCompletionInPythonConsole)
            .addTooltip("Adds code completion when plugin is used with Python Console.")
            .panel
        filterInputSettingsPanel.border = createTitleBorder("Filter input")

        val featureSwitchPanel = FormBuilder.createFormBuilder()
            .panel
        featureSwitchPanel.border = createTitleBorder("Feature switches")

        myPanel = FormBuilder.createFormBuilder()
            .addComponent(
                JBLabel(
                    "Changes are only applied to newly opened dialogs.",
                    UIUtil.ComponentStyle.SMALL,
                    UIUtil.FontColor.BRIGHTER,
                )
            )
            .addVerticalGap(10)
            .addComponent(dataFetchingSettingsPanel)
            .addComponent(filterInputSettingsPanel)
            .addComponent(featureSwitchPanel)
            .addComponentFillVertically(Box.createVerticalGlue() as JComponent, 0)
            .panel
    }

    private fun createTitleBorder(title: String): IdeaTitledBorder {
        return IdeaTitledBorder(title, 20, Insets(0, 0, 20, 0))
    }

    fun getPanel(): JComponent {
        return myPanel
    }

    fun getPreferredFocusedComponent(): JComponent {
        return myPanel
    }
}