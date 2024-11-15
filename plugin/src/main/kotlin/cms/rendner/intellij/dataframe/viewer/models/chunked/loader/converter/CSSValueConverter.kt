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
package cms.rendner.intellij.dataframe.viewer.models.chunked.loader.converter

import cms.rendner.intellij.dataframe.viewer.models.TextAlign
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
        return if(value.isNullOrEmpty()) null else when (value) {
            "left", "start" -> TextAlign.left
            "right", "end" -> TextAlign.right
            "center" -> TextAlign.center
            else -> null
        }
    }

    private fun toColor(colorString: String): Color? {
        // https://dzone.com/articles/create-javaawtcolor-from-string-representation
        // https://github.com/beryx/awt-color-factory/blob/master/README.md
        return try {
             ColorFactory.web(colorString, 1.0).let { if (it.alpha == 0) null else it }
        } catch (ignore: IllegalArgumentException) {
            null
        }
    }
}