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

import com.intellij.openapi.util.Key
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.psi.PyExpressionCodeFragment
import com.jetbrains.python.psi.PyQualifiedExpression
import com.jetbrains.python.psi.resolve.PyReferenceResolveProvider
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.resolve.RatedResolveResult
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Fix for unresolved references in chained code fragments.
 *
 * Currently, the resolve algorithm implemented in IntelliJ can't resolve chained code fragments.
 *
 * Example:
 * codeFragmentA.context -> elementFromB.containingFile -> codeFragmentB.context -> elementFromC -> sourceCodeFileC
 *
 * IntelliJ can resolve all elements from "codeFragmentA" and "codeFragmentB". But elements defined in
 * "sourceCodeFileC" result in unresolved references.
 *
 * The registered instance of this class is only called if no previous listed [PyReferenceResolveProvider] can provide
 * a reference.
 */
class PyCodeFragmentReferenceResolveProvider : PyReferenceResolveProvider {

    companion object {
        val RESOLVE_REFERENCES = Key.create<Boolean>("${Companion::class.java.name}.RESOLVE_REFERENCES")
    }

    override fun resolveName(element: PyQualifiedExpression, context: TypeEvalContext): List<RatedResolveResult> {
        val origin = context.origin
        if (
            origin is ScopeOwner &&
            element.containingFile is PyExpressionCodeFragment &&
            RESOLVE_REFERENCES.get(element.containingFile, false)
        ) {
            element.referencedName?.let { name ->
                return PyResolveUtil.resolveLocally(origin, name)
                    .map { RatedResolveResult(RatedResolveResult.RATE_NORMAL, it) }
            }
        }
        return emptyList()
    }
}