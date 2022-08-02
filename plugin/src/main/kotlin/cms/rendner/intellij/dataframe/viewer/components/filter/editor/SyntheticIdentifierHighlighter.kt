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
package cms.rendner.intellij.dataframe.viewer.components.filter.editor

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyExpressionCodeFragment

/**
 * Highlights synthetic identifier inside a [PyExpressionCodeFragment].
 *
 * To highlight synthetic identifier the code fragment has to provide the names
 * of the identifier to highlight via the key [HIGHLIGHT].
 */
class SyntheticIdentifierHighlighter : Annotator {
    companion object {
        val HIGHLIGHT = Key.create<Set<String>>("${Companion::class.java.name}.HIGHLIGHT")
        private val EMPTY_LIST = emptySet<String>()
    }

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.containingFile is PyExpressionCodeFragment &&
            element.elementType == PyTokenTypes.IDENTIFIER &&
            HIGHLIGHT.get(element.containingFile, EMPTY_LIST).contains(element.text)
        ) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element)
                .textAttributes(DefaultLanguageHighlighterColors.LINE_COMMENT).create()
        }
    }
}