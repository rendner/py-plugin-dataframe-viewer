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
    private val myFSUseFilterInputFromInternalApiCheckBox =
        JBCheckBox("Filter input: use editor from internal IntelliJ API")

    private val myPanel: JPanel

    var pandasStyledFuncValidationEnabled: Boolean
        get() = myPandasStyledFuncValidationEnabledCheckBox.isSelected
        set(value) {
            myPandasStyledFuncValidationEnabledCheckBox.isSelected = value
        }

    var fsUseFilterInputFromInternalApi: Boolean
        get() = myFSUseFilterInputFromInternalApiCheckBox.isSelected
        set(value) {
            myFSUseFilterInputFromInternalApiCheckBox.isSelected = value
        }


    init {
        val dataFetchingSettingsPanel = FormBuilder.createFormBuilder()
            .addComponent(myPandasStyledFuncValidationEnabledCheckBox)
            .addTooltip("Validates that styling functions return stable results for chunks of different sizes.")
            .panel
        dataFetchingSettingsPanel.border = createTitleBorder("Data fetching")

        val featureSwitchPanel = FormBuilder.createFormBuilder()
            .addComponent(myFSUseFilterInputFromInternalApiCheckBox)
            .addTooltip("Stores the filter history. May not be available due to internal API changes.")
            .addTooltip("(For pandas DataFrames)")
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