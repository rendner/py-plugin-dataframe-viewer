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

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.resolve.PyReferenceResolveProvider
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.resolve.RatedResolveResult
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Fix for unresolved references in reference expressions for chained code fragments.
 *
 * The filter input editor instance uses a [com.jetbrains.python.psi.PyExpressionCodeFragment] where the user can
 * enter a code snippet (single line). This code fragment has as context the source position of the debugger breakpoint.
 * In case the synthetic DataFrame identifier is injected, the code fragment of the editor has as context the code
 * fragment which defines the synthetic identifier. And the code fragment with the synthetic identifier has as context
 * the source position of the debugger breakpoint.
 *
 * Having two chained code fragments has the side effect, that the auto-completion is broken for all identifier
 * in the chained code fragments if used inside a reference expression.
 *
 * Example:
 * The source position of the debugger breakpoint defines an identifier X which is a pandas DataFrame.
 * The auto-completion popup lists X. Calling auto-completion on "X.", to get the DataFrame specific properties/methods,
 * doesn't work.
 */
class PyChainedCodeFragmentReferenceResolveProvider : PyReferenceResolveProvider {

    override fun resolveName(element: PyQualifiedExpression, context: TypeEvalContext): List<RatedResolveResult> {
        if (isSyntheticIdentifierAllowed(element.containingFile?.originalFile) || isSyntheticIdentifierAllowed(context.origin?.originalFile)) {
            val refName = element.referencedName ?: return emptyList()
            var current: PsiElement? = element.context?.containingFile?.originalFile

            // unsure if needed but there to prevent endless loops
            val checked = mutableSetOf<ScopeOwner>()

            // iterate over the chained elements until we can resolve the element
            while (current != null) {
                if (current is ScopeOwner && !checked.contains(current)) {
                    checked.add(current)
                    val resolved = PyResolveUtil.resolveLocally(current, refName)
                    if (resolved.isNotEmpty()) {
                        return resolved.map { RatedResolveResult(RatedResolveResult.RATE_NORMAL, it) }
                    }
                }
                current = current.context?.containingFile?.originalFile
            }
        }
        return emptyList()
    }

    private fun isSyntheticIdentifierAllowed(psiFile: PsiFile?): Boolean {
        return psiFile?.let { SyntheticDataFrameIdentifier.isSyntheticIdentifierAllowed(it) } == true
    }
}