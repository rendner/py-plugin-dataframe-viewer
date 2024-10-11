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
package cms.rendner.intellij.dataframe.viewer.components.filter.editor

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyReferenceExpression

class SyntheticDataFrameIdentifier {
    companion object {
        const val NAME = "_df"
        private val RESOLVE_SYNTHETIC_IDENTIFIER: Key<Boolean> = Key.create("cms.rendner.RESOLVE_SYNTHETIC_IDENTIFIER")

        fun getSourceCodeToCreateIdentifier(): String {
            /*
            The code is used to create a synthetic identifier of type DataFrame.
            IntelliJ provides automatically full code completion for the identifier.

            The comment, included in the snippet, is a description for the user in case the user navigates
            to the definition of the injected identifier.
            */
            return """
                |# plugin: "Styled DataFrame Viewer"
                |# helper for providing the synthetic identifier "$NAME"
                |import pandas as pd
                |$NAME = pd.DataFrame()
            """.trimMargin()
        }

        fun allowToResolveSyntheticIdentifier(psiFile: PsiFile) {
            RESOLVE_SYNTHETIC_IDENTIFIER.set(psiFile, true)
        }

        fun isSyntheticIdentifierAllowed(psiFile: PsiFile): Boolean {
            return RESOLVE_SYNTHETIC_IDENTIFIER.get(psiFile, false)
        }

        fun isAllowedIdentifier(element: PsiElement?): Boolean {
            if (element == null) return false
            if (element.elementType != PyTokenTypes.IDENTIFIER && element !is PyReferenceExpression) return false
            return isSyntheticIdentifierAllowed(element.containingFile) && element.text == NAME
        }
    }
}