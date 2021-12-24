/*
 * Copyright 2021 cms.rendner (Daniel Schmidt)
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
package cms.rendner.intellij.dataframe.viewer.components.renderer.text.header

import cms.rendner.intellij.dataframe.viewer.models.IHeaderLabel
import cms.rendner.intellij.dataframe.viewer.models.LeveledHeaderLabel
import cms.rendner.intellij.dataframe.viewer.models.Value
import cms.rendner.intellij.dataframe.viewer.components.renderer.text.ILabelTextProvider
import javax.swing.JTable

enum class LeveledDisplayMode {
    LEADING_LEVELS_ONLY,
    LAST_LEVEL_ONLY,
    FULL
}

class DefaultHeaderTextProvider(
    private val displayMode: LeveledDisplayMode = LeveledDisplayMode.FULL
) : ILabelTextProvider {
    override fun getLabel(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): String? {

        return when (value) {
            is LeveledHeaderLabel -> {
                when (displayMode) {
                    LeveledDisplayMode.LAST_LEVEL_ONLY -> value.lastLevel
                    LeveledDisplayMode.LEADING_LEVELS_ONLY -> value.leadingLevels.joinToString(separator = "/")
                    else -> value.text()
                }
            }
            is IHeaderLabel -> value.text()
            is Value -> value.text()
            is String -> value
            else -> null
        }
    }
}