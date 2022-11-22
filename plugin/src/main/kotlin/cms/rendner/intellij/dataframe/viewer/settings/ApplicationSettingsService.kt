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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.annotations.OptionTag


/**
 * Supports storing the plugin settings in a persistent way.
 * The [State] and [Storage] annotations define the name of the data and the file name where
 * these persistent application settings are stored.
 *
 * See [persisting-state-of-components](https://plugins.jetbrains.com/docs/intellij/persisting-state-of-components.html)
 */
@State(name = "cms.rendner.StyledDataFrameViewer", storages = [Storage("styledDataFrameViewer.xml")])
class ApplicationSettingsService : PersistentStateComponent<ApplicationSettingsService.MyState> {

    companion object {
        val instance: ApplicationSettingsService
            get() = ApplicationManager.getApplication().getService(ApplicationSettingsService::class.java)
    }

    private var myState = MyState()

    data class MyState (
        @OptionTag(converter = MyValidationStrategyTypeConverter::class)
        var validationStrategyType: ValidationStrategyType = ValidationStrategyType.DISABLED,
        var fsUseFilterInputFromInternalApi: Boolean = true,
    )

    override fun getState(): MyState {
        return myState
    }

    override fun loadState(state: MyState) {
        myState = state
    }

    /**
     * Use a converter to not fail in case the name of the enum values is changed after persistence.
     */
    class MyValidationStrategyTypeConverter: Converter<ValidationStrategyType>() {
        override fun toString(value: ValidationStrategyType) = value.name
        override fun fromString(value: String) = ValidationStrategyType.valueOfOrDisabled(value)
    }
}