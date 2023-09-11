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

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.ui.JBColor
import com.intellij.util.PlatformIcons

class MyAdjustAutoCompletionFilter : CompletionContributor() {

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (SyntheticDataFrameIdentifier.isSyntheticIdentifierAllowed(parameters.originalFile)) {
      result.runRemainingContributors(parameters) { completionResult ->
        if (!SyntheticDataFrameIdentifier.isIdentifierExcludedFromAutoCompletion(completionResult.lookupElement.lookupString)) {
          if (completionResult.lookupElement.lookupString == SyntheticDataFrameIdentifier.NAME) {
            // render synthetic identifier grayed out in the completion list
            result.passResult(
              completionResult.withLookupElement(
              LookupElementBuilder
                .create(completionResult.lookupElement, completionResult.lookupElement.lookupString)
                .withItemTextForeground(JBColor.GRAY)
                .withTypeText("synthetic", true)
                .withIcon(PlatformIcons.VARIABLE_ICON)
              )
            )
          } else {
            result.passResult(completionResult)
          }
        }
      }
    } else {
      super.fillCompletionVariants(parameters, result)
    }
  }
}