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

import cms.rendner.intellij.dataframe.viewer.python.DataFrameLibrary
import cms.rendner.intellij.dataframe.viewer.python.PythonQualifiedTypes
import cms.rendner.intellij.dataframe.viewer.python.bridge.CompletionVariant
import cms.rendner.intellij.dataframe.viewer.python.bridge.ICompletionVariant
import cms.rendner.intellij.dataframe.viewer.python.bridge.IPyTableSourceRef
import cms.rendner.intellij.dataframe.viewer.python.bridge.NestedCompletionVariant
import cms.rendner.intellij.dataframe.viewer.services.SyntheticDataFramePsiRefProvider
import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.JBColor
import com.intellij.util.PlatformIcons
import com.jetbrains.python.console.completion.PydevConsoleElement
import com.jetbrains.python.console.pydev.IToken
import com.jetbrains.python.console.pydev.PyCodeCompletionImages
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyListLiteralExpression
import com.jetbrains.python.psi.PyNumericLiteralExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.PySubscriptionExpression

class MyFilterInputCompletionProvider(
    private val tableSourceRef: IPyTableSourceRef,
    private val withPythonConsoleCodeCompletion: Boolean,
    private val withPluginCodeCompletion: Boolean,
): IFilterInputCompletionProvider {

    @Volatile
    private var myTableSourceCompletionVariants: List<ICompletionVariant>? = null

    private data class ColNameCompletionInfo(
        val completableText: String?,
        val isInsideString: Boolean,
        private val result: CompletionResultSet,
        ) {
        private var addedVariants: Int = 0

        fun addVariants(variants: List<ICompletionVariant>) {
            for (variant in variants) {
                if (variant is NestedCompletionVariant) {
                    if (!isInsideString && variant.fqType == PythonQualifiedTypes.TUPLE) {
                        val parts = variant.children.map {
                            if (it.fqType == PythonQualifiedTypes.STR) "\"${it.value}\"" else it.value
                        }
                        addElement("(${parts.joinToString(", ")})")
                    }
                }
                if (variant is CompletionVariant) {
                    if (isInsideString && variant.fqType != PythonQualifiedTypes.STR) continue
                    if (!isInsideString && variant.fqType == PythonQualifiedTypes.STR && completableText != null) continue
                    addElement(
                        if (variant.fqType == PythonQualifiedTypes.STR && !isInsideString) "\"${variant.value}\""
                        else variant.value
                    )
                }
            }
        }

        private fun addElement(text: String) {
            result.addElement(
                PrioritizedLookupElement.withPriority(
                    LookupElementBuilder
                        .create(text)
                        .withTypeText("column name")
                        .withIcon(PlatformIcons.PROPERTY_ICON),
                    1001.0 + addedVariants,
                    )
            )
            addedVariants++
        }
    }

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        addSyntheticIdentifier(parameters, result)
        addExternalCompletionVariants(parameters, result)
    }

    private fun addSyntheticIdentifier(parameters: CompletionParameters, result: CompletionResultSet) {
        if (!tableSourceRef.context.frameAccessor.syntheticIdentifierCanBeUsed()) return
        val refExpr = parameters.position.parent as? PyReferenceExpression ?: return
        if (refExpr.isQualified) return
        if (!result.prefixMatcher.prefixMatches(SyntheticDataFrameIdentifier.NAME)) return

        parameters.position.project.service<SyntheticDataFramePsiRefProvider>()
            .getPointer(tableSourceRef.context.syntheticIdentifierType)?.let {
            result.addElement(
                PrioritizedLookupElement.withPriority(
                    LookupElementBuilder
                        .create(it, SyntheticDataFrameIdentifier.NAME)
                        .withItemTextForeground(JBColor.GRAY)
                        .withTypeText("<synthetic>", true)
                        .withIcon(PlatformIcons.VARIABLE_ICON),
                    1000.0,
                )
            )
        }
    }

    private fun addExternalCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        addPandasDataFrameColumnNames(parameters, result)
        addRuntimeCompletionVariantsForConsole(parameters, result)
    }

    private fun addPandasDataFrameColumnNames(parameters: CompletionParameters, result: CompletionResultSet) {
        if (!withPluginCodeCompletion) return
        if (tableSourceRef.context.syntheticIdentifierType != DataFrameLibrary.PANDAS) return

        val subscriptionExpr = PsiTreeUtil.getParentOfType(parameters.position, PySubscriptionExpression::class.java) ?: return
        val refExpr = subscriptionExpr.operand as? PyReferenceExpression ?: return
        // ignore something like "df.loc[<caret>]"
        if (refExpr.isQualified) return

        var indexExpr = subscriptionExpr.indexExpression ?: return

        if (indexExpr is PyListLiteralExpression) {
            // df[[<caret>]]
            if (indexExpr.isEmpty) return
            val elements =  indexExpr.elements
            if (elements.size > 1) return
            indexExpr = elements[0]
        }

        val completionInfo = getColNameCompletionInfo(indexExpr, parameters, result) ?: return
        val identifier = refExpr.text
        val isSyntheticIdentifier = tableSourceRef.context.frameAccessor.syntheticIdentifierCanBeUsed() && identifier == SyntheticDataFrameIdentifier.NAME

        if (isSyntheticIdentifier) {
            myTableSourceCompletionVariants?.let {
                completionInfo.addVariants(it)
                return
            }
        }

        ProgressManager.checkCanceled()
        val variants = tableSourceRef.getColumnNameCompletionVariants(identifier, isSyntheticIdentifier)
        if (isSyntheticIdentifier) {
            myTableSourceCompletionVariants = variants
        }
        ProgressManager.checkCanceled()

        completionInfo.addVariants(variants)
    }

    private fun getColNameCompletionInfo(
        indexExpr: PyExpression,
        parameters: CompletionParameters,
        result: CompletionResultSet,
        ): ColNameCompletionInfo? {
        if (indexExpr is PyReferenceExpression) {
            if (indexExpr.text == CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED) {
                return ColNameCompletionInfo(null, false, result)
            }
        }
        else if (indexExpr is PyStringLiteralExpression) {
            return ColNameCompletionInfo(
                indexExpr.text.replace(CompletionInitializationContext.DUMMY_IDENTIFIER, ""),
                true,
                result,
            )
        }
        else if (indexExpr is PyNumericLiteralExpression && indexExpr.isIntegerLiteral) {
            val prefixLength = parameters.offset - indexExpr.getTextRange().startOffset
            val prefix = indexExpr.text.substring(0, prefixLength)

            return ColNameCompletionInfo(
                indexExpr.text,
                false,
                result.withPrefixMatcher(prefix),
            )
        }
        return null
    }

    private fun addRuntimeCompletionVariantsForConsole(parameters: CompletionParameters, result: CompletionResultSet) {
        // The console based "frameAccessor" doesn't provide a sourcePosition which could be used for code completion.
        // Instead, the data is fetched from the underlying Python process.
        if (!withPythonConsoleCodeCompletion || tableSourceRef.context.frameAccessor.isDebugConsole()) return

        val refExpr = parameters.position.parent as? PyReferenceExpression ?: return
        val qName = refExpr.asQualifiedName() ?: return
        val actToken = qName.toString().replace(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED, "")

        // Exit here in case of our synthetic identifier.
        if (tableSourceRef.context.frameAccessor.syntheticIdentifierCanBeUsed() && actToken.startsWith("${SyntheticDataFrameIdentifier.NAME}.")) return

        val psiManager = parameters.position.manager

        ProgressManager.checkCanceled()
        val variants = tableSourceRef.context.frameAccessor.getConsoleCompletions(actToken, actToken)
        ProgressManager.checkCanceled()

        variants.forEachIndexed { index, it ->
            var builder = LookupElementBuilder
                .create(PydevConsoleElement(psiManager, it.name, it.description))
                .withIcon(PyCodeCompletionImages.getImageForType(it.type))

            if (it.args.isNotBlank()) {
                builder = builder.withTailText(it.args)
            }
            if (it.type == IToken.TYPE_FUNCTION || it.args.endsWith(")")) {
                builder = builder.withInsertHandler(ParenthesesInsertHandler.WITH_PARAMETERS)
            }

            result.addElement(PrioritizedLookupElement.withPriority(builder, 100.0 + index))
        }
    }
}
