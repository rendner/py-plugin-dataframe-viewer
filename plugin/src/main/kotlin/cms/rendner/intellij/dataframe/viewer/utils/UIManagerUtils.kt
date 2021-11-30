package cms.rendner.intellij.dataframe.viewer.utils

import java.awt.Color
import javax.swing.UIManager

fun colorFromUI(key: String, fallback: Color): Color {
    return try {
        UIManager.getColor(key) ?: fallback
    } catch (e: NullPointerException) {
        fallback
    }
}