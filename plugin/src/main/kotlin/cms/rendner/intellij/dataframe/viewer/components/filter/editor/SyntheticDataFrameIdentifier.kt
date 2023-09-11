/*
 * Copyright 2023 cms.rendner (Daniel Schmidt)
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
import com.jetbrains.python.psi.PyExpressionCodeFragment

class SyntheticDataFrameIdentifier {
    companion object {
        const val NAME = "_df"
        private val FRAGMENT_ALLOWS_SYNTHETIC_IDENTIFIER: Key<Boolean> = Key.create("cms.rendner.FragmentAllowsSyntheticIdentifier")

        fun getFragmentCode(): String {
        /*
        The code is used to create a synthetic identifier of type DataFrame.
        IntelliJ provides automatically full code completion for the identifier.
        The identifier is injected by using an extra code fragment to hide the code from the user.

        The comment, included in the snippet, is a description for the user in case the user navigates
        to the definition of the injected identifier.

        The special alias for the pandas import is intended to reduce the risk to shadow an existing identifier
        used in the file specified by the source position of the debugger breakpoint.

        Example:
            - debugger breakpoint: file has import statement "import numpy as pd"
            - syntheticCode:
                    import pandas as pd
                    _df = pd.DataFrame()

            The auto-completion for "_df." works when [PyChainedCodeFragmentReferenceResolveProvider] is used.
            But if the user wants to auto-complete "pd." he gets the auto-completion for a pandas DataFrame instead
            for numpy.
        */
            return """
                |# plugin: "Styled DataFrame Viewer"
                |# helper for providing the synthetic identifier "$NAME"
                |import pandas as sdvf_plugin_pd
                |$NAME = sdvf_plugin_pd.DataFrame()
            """.trimMargin()
        }

        fun isIdentifierExcludedFromAutoCompletion(identifier: String): Boolean {
            return identifier == "sdvf_plugin_pd"
        }

        fun allowSyntheticIdentifier(fragment: PyExpressionCodeFragment) {
            FRAGMENT_ALLOWS_SYNTHETIC_IDENTIFIER.set(fragment, true)
        }

        fun isSyntheticIdentifierAllowed(psiFile: PsiFile): Boolean {
            return psiFile is PyExpressionCodeFragment && FRAGMENT_ALLOWS_SYNTHETIC_IDENTIFIER.get(psiFile, false)
        }

        fun isIdentifier(element: PsiElement?): Boolean {
            if (element == null) return false
            if (element.elementType != PyTokenTypes.IDENTIFIER) return false
            return isSyntheticIdentifierAllowed(element.containingFile) && element.text == NAME
        }
    }
}