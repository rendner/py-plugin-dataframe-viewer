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
import com.intellij.util.ui.FormBuilder
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Provides the settings view.
 */
class SettingsComponent {

    private val myValidationStrategyComboBox = MyValidationStrategyComboBox(ValidationStrategyType.DISABLED)

    private val myPanel: JPanel

    var validationStrategyType: ValidationStrategyType
        get() = myValidationStrategyComboBox.item
        set(value) { myValidationStrategyComboBox.item = value }


    init {
        myPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Validation strategy: ", myValidationStrategyComboBox)
            .addComponentFillVertically(Box.createVerticalGlue() as JComponent, 0)
            .panel
    }

    fun getPanel(): JComponent {
        return myPanel
    }

    fun getPreferredFocusedComponent(): JComponent {
        return myValidationStrategyComboBox
    }

    private class MyValidationStrategyComboBox(selectedValue: ValidationStrategyType) :
        ComboBox<ValidationStrategyType>() {
        init {
            ValidationStrategyType.values().forEach { addItem(it) }
            item = selectedValue
        }
    }
}