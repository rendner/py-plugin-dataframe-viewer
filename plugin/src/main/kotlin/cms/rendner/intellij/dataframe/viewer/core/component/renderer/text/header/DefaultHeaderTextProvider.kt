package cms.rendner.intellij.dataframe.viewer.core.component.renderer.text.header

import cms.rendner.intellij.dataframe.viewer.core.component.models.IHeaderLabel
import cms.rendner.intellij.dataframe.viewer.core.component.models.LeveledHeaderLabel
import cms.rendner.intellij.dataframe.viewer.core.component.models.Value
import cms.rendner.intellij.dataframe.viewer.core.component.renderer.text.ILabelTextProvider
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