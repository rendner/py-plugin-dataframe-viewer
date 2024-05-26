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
package cms.rendner.intellij.dataframe.viewer.components.filter

import cms.rendner.intellij.dataframe.viewer.services.SyntheticDataFramePsiRefProvider
import com.intellij.openapi.components.service
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.resolve.PyReferenceResolveProvider
import com.jetbrains.python.psi.resolve.RatedResolveResult
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Resolves the reference of the synthetic DataFrame identifier [SyntheticDataFrameIdentifier.NAME].
 * Without this provider, the identifier is marked as unresolved in the filter-input (even it can be navigated to it).
 */
class SyntheticIdentifierReferenceResolveProvider : PyReferenceResolveProvider {

    override fun resolveName(element: PyQualifiedExpression, context: TypeEvalContext): List<RatedResolveResult> {
        IFilterInputCompletionContributor.COMPLETION_CONTRIBUTOR.get(element.containingFile)?.let { contributor ->
            if (contributor.isSyntheticIdentifierEnabled()) {
                if (SyntheticDataFrameIdentifier.isIdentifier(element)) {
                    element.project.service<SyntheticDataFramePsiRefProvider>().getPointer(contributor.getSyntheticIdentifierType())?.let {
                        return listOf(RatedResolveResult(RatedResolveResult.RATE_NORMAL, it.element))
                    }
                }
            }
        }
        return emptyList()
    }
}