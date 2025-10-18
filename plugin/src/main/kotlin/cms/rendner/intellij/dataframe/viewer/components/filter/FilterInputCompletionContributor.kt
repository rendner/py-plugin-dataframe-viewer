/*
 * Copyright 2021-2026 cms.rendner (Daniel Schmidt)
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

import cms.rendner.intellij.dataframe.viewer.COMPLETION_PROVIDER_KEY
import cms.rendner.intellij.dataframe.viewer.TABLE_SOURCE_CONTEXT_KEY
import cms.rendner.intellij.dataframe.viewer.python.DataFrameLibrary
import com.intellij.codeInsight.completion.*
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyNumericLiteralExpression
import com.jetbrains.python.psi.PySubscriptionExpression


/**
 * Adds additional contributions for a [AbstractFilterInput] if a [IFilterInputCompletionProvider] was installed.
 */
class FilterInputCompletionContributor : CompletionContributor() {

    override fun beforeCompletion(context: CompletionInitializationContext) {
        // fix for: https://youtrack.jetbrains.com/issue/PY-73241
        // Code completion can't complete integer literals inside the index [] operator of a dictionary

        // The dummy identifier was already changed - return here
        // Otherwise an exception is thrown if the "dummyIdentifier" is changed again.
        if (context.dummyIdentifier != CompletionInitializationContext.DUMMY_IDENTIFIER) return

        // The "dummyIdentifier" is changed for all registered contributors.
        // So we have to be as specific as possible to not break other contributors.
        if (context.completionType != CompletionType.BASIC) return

        val tableSourceCtx = TABLE_SOURCE_CONTEXT_KEY.get(context.file) ?: return
        if (tableSourceCtx.syntheticIdentifierType != DataFrameLibrary.PANDAS) return

        val element = context.file.findElementAt(context.caret.offset) ?: return
        val subscription = PsiTreeUtil.getParentOfType(element, PySubscriptionExpression::class.java) ?: return

        if ((subscription.indexExpression as? PyNumericLiteralExpression)?.isIntegerLiteral == true) {
            context.dummyIdentifier = ""
        }
    }

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (parameters.completionType != CompletionType.BASIC) return

        COMPLETION_PROVIDER_KEY.get(parameters.originalFile)?.fillCompletionVariants(parameters, result)
    }
}
