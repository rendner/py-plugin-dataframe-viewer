/*
 * Copyright 2021-2025 cms.rendner (Daniel Schmidt)
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

import cms.rendner.intellij.dataframe.viewer.IGNORE_ALL_INTENTIONS_ACTION_FILTER_KEY
import cms.rendner.intellij.dataframe.viewer.python.DataFrameLibrary
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.debugger.PyDebuggerEditorsProvider
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.resolve.PyResolveUtil

class FilterInputEditorsProvider(
    private val project: Project,
    private val createdDocumentFileProcessor: ((psiFile: PsiFile) -> Unit)? = null,
) : PyDebuggerEditorsProvider() {

    fun createDocument(expression: String, sourcePosition: XSourcePosition? = null): Document {
        return createDocument(
            project,
            XDebuggerUtil.getInstance()
                .createExpression(expression, PythonLanguage.INSTANCE, null, EvaluationMode.EXPRESSION),
            sourcePosition,
            EvaluationMode.EXPRESSION,
        )
    }

    override fun createDocument(
        project: Project,
        expression: XExpression,
        sourcePosition: XSourcePosition?,
        mode: EvaluationMode
    ): Document {
        return super.createDocument(
            project,
            expression,
            sourcePosition,
            EvaluationMode.EXPRESSION,
        ).also {
            PsiDocumentManager.getInstance(project).getPsiFile(it)?.let { psiFile ->
                IGNORE_ALL_INTENTIONS_ACTION_FILTER_KEY.set(psiFile, true)
                createdDocumentFileProcessor?.invoke(psiFile)
            }
        }
    }

    fun createSyntheticIdentifierPointer(identifierType: DataFrameLibrary): SmartPsiElementPointer<PyTargetExpression>? {
        val exprFragment = XDebuggerUtil.getInstance().createExpression(
            getSourceCodeToCreateIdentifier(identifierType),
            PythonLanguage.INSTANCE,
            null,
            EvaluationMode.CODE_FRAGMENT
        )
        super.createDocument(project, exprFragment, null, EvaluationMode.CODE_FRAGMENT).apply {
            setReadOnly(true)
            val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(this) as? ScopeOwner ?: return null
            val resolved = PyResolveUtil.resolveLocally(psiFile, SyntheticDataFrameIdentifier.NAME)
            val identifier = resolved.firstOrNull() as? PyTargetExpression ?: return null
            return SmartPointerManager.getInstance(project).createSmartPsiElementPointer(identifier)
        }
    }

    private fun getSourceCodeToCreateIdentifier(identifierType: DataFrameLibrary): String {
        val code = when (identifierType) {
            DataFrameLibrary.PANDAS -> """
                |import pandas as pd
                |${SyntheticDataFrameIdentifier.NAME} = pd.DataFrame()
            """
            DataFrameLibrary.POLARS -> """
                |import polars as pl
                |${SyntheticDataFrameIdentifier.NAME} = pl.DataFrame()
            """
        }
        return """
                ${code.trim()}
                |""${'"'}
                |Synthetic identifier, provided by the 'Styled DataFrame Viewer'-plugin, to reference the displayed DataFrame.
                |""${'"'}
            """.trimMargin()
    }
}
