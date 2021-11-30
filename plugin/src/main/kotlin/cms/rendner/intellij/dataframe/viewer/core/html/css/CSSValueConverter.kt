package cms.rendner.intellij.dataframe.viewer.core.html.css

import cms.rendner.intellij.dataframe.viewer.core.component.models.TextAlign
import org.beryx.awt.color.ColorFactory
import java.awt.Color

/**
 * is thread-safe
 */
class CSSValueConverter {

    fun convertColorValue(value: String?): Color? {
        return if(value.isNullOrEmpty()) null else toColor(value)
    }

    fun convertTextAlign(value: String?): TextAlign? {
        return when (value) {
            "left" -> TextAlign.left
            "right" -> TextAlign.right
            "center" -> TextAlign.center
            else -> null
        }
    }

    private fun toColor(colorString: String): Color? {
        // https://dzone.com/articles/create-javaawtcolor-from-string-representation
        // https://github.com/beryx/awt-color-factory/blob/master/README.md
        return try {
            val color = ColorFactory.web(colorString, 1.0)
            return if (color.alpha == 0) null else color
        } catch (ignore: IllegalArgumentException) {
            null
        }
    }
}