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

import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.ValidationStrategyType
import com.intellij.openapi.ui.ComboBox
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

    private val myValidationStrategyComboBox = ComboBox(ValidationStrategyType.values())
    private val myFSUseFilterInputFromInternalApiCheckBox =
        JBCheckBox("Filter input: use editor from internal IntelliJ API")

    private val myPanel: JPanel

    var validationStrategyType: ValidationStrategyType
        get() = myValidationStrategyComboBox.item
        set(value) {
            myValidationStrategyComboBox.item = value
        }

    var fsUseFilterInputFromInternalApi: Boolean
        get() = myFSUseFilterInputFromInternalApiCheckBox.isSelected
        set(value) {
            myFSUseFilterInputFromInternalApiCheckBox.isSelected = value
        }


    init {
        val dataFetchingSettingsPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Validation strategy: ", myValidationStrategyComboBox)
            .addTooltip("Automatic validation of used styling functions")
            .panel
        dataFetchingSettingsPanel.border = createTitleBorder("Data fetching")

        val featureSwitchPanel = FormBuilder.createFormBuilder()
            .addComponent(myFSUseFilterInputFromInternalApiCheckBox)
            .addTooltip("Stores the filter history. May not be available due to internal API changes.")
            .panel
        featureSwitchPanel.border = createTitleBorder("Feature switches")

        myPanel = FormBuilder.createFormBuilder()
            .addComponent(
                JBLabel(
                    "Changed settings are only applied to newly opened dialogs.",
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
        return myValidationStrategyComboBox
    }
}