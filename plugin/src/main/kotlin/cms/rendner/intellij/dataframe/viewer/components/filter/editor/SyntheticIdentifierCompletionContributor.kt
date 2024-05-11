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

import cms.rendner.intellij.dataframe.viewer.services.SyntheticDataFramePsiRefProvider
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.ui.JBColor
import com.intellij.util.PlatformIcons

/**
 * Adds a navigateable reference to the synthetic DataFrame identifier [SyntheticDataFrameIdentifier.NAME]
 * to the completion popup.
 */
class SyntheticIdentifierCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (!SyntheticDataFrameIdentifier.isSyntheticIdentifierAllowed(parameters.originalFile)) return
        // "df." has a prevSibling, "df[" not
        if (parameters.position.prevSibling != null) return
        if (!result.prefixMatcher.prefixMatches(SyntheticDataFrameIdentifier.NAME)) return

        parameters.position.project.service<SyntheticDataFramePsiRefProvider>().pointer?.let {
            result.addElement(
                PrioritizedLookupElement.withPriority(
                    LookupElementBuilder
                        .create(it, SyntheticDataFrameIdentifier.NAME)
                        .withItemTextForeground(JBColor.GRAY)
                        .withTypeText("<synthetic>", true)
                        .withIcon(PlatformIcons.VARIABLE_ICON),
                    100.0,
                )
            )
        }
    }
}