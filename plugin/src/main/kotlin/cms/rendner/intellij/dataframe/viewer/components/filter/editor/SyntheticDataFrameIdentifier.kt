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

import cms.rendner.intellij.dataframe.viewer.python.DataFrameLibrary
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyReferenceExpression

class SyntheticDataFrameIdentifier {
    companion object {
        const val NAME = "_df"
        private val SYNTHETIC_IDENTIFIER_TYPE: Key<DataFrameLibrary> = Key.create("cms.rendner.SYNTHETIC_IDENTIFIER_TYPE")

        fun markForResolution(psiFile: PsiFile, frameLibraryType: DataFrameLibrary) {
            SYNTHETIC_IDENTIFIER_TYPE.set(psiFile, frameLibraryType)
        }

        fun getFrameLibraryType(psiFile: PsiFile): DataFrameLibrary? {
            return SYNTHETIC_IDENTIFIER_TYPE.get(psiFile.containingFile, null)
        }

        fun isMarkedForResolution(psiFile: PsiFile): Boolean {
            return getFrameLibraryType(psiFile) != null
        }

        fun isIdentifierAndMarkedForResolution(element: PsiElement?): Boolean {
            if (element == null) return false
            if (element.elementType != PyTokenTypes.IDENTIFIER && element !is PyReferenceExpression) return false
            return isMarkedForResolution(element.containingFile) && element.text == NAME
        }
    }
}