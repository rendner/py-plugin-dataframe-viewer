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

import cms.rendner.intellij.dataframe.viewer.python.DataFrameLibrary
import cms.rendner.intellij.dataframe.viewer.services.SyntheticDataFramePsiRefProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI.Panels
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.debugger.PyDebuggerEditorsProvider
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.resolve.PyResolveUtil
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JPanel



abstract class AbstractFilterInput(
    private val completionContributor: IFilterInputCompletionContributor
    ): KeyAdapter() {

    protected val myEditorsProvider = MyDebuggerEditorsProvider(
        completionContributor.getSyntheticIdentifierType(),
        ::configureCreatedDocumentFile,
    )

    private var changedListener: IFilterInputChangedListener? = null
    protected val myEditorContainer = Panels.simplePanel()
    private val myMainPanel: JPanel
    private val myErrorLabel: JBLabel = JBLabel()
    private val myExpressionComponentLabel = JBLabel("Filter:")

    init {
        myErrorLabel.icon = AllIcons.General.Error
        myErrorLabel.foreground = JBColor.RED
        myErrorLabel.isVisible = false
        // set an explicit min width to not increase the width when a long error message is displayed
        myErrorLabel.minimumSize = myErrorLabel.minimumSize.also { it.width = 100 }

        myMainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(myExpressionComponentLabel, myEditorContainer)
            .addTooltip("Expression that returns a DataFrame with the rows and columns to keep")
            .addLabeledComponent("", myErrorLabel)
            .panel
    }

    abstract fun setEnabled(enabled: Boolean)

    fun setChangedListener(listener: IFilterInputChangedListener?) {
        changedListener = listener
    }

    override fun keyReleased(e: KeyEvent?) {
        notifyInputChanged()
    }

    protected fun notifyInputChanged() {
        changedListener?.filterInputChanged()
    }

    abstract fun getText(): String

    abstract fun setSourcePosition(sourcePosition: XSourcePosition?)

    fun showErrorMessage(message: String) {
        myErrorLabel.isVisible = true
        myErrorLabel.text = message
        // error message will be truncated if dialog is too small
        // therefore also show the message as tooltip
        myErrorLabel.toolTipText = StringUtil.escapeXmlEntities(message)
    }

    fun hideErrorMessage() {
        myErrorLabel.isVisible = false
    }

    open fun saveInputInHistory() {
    }

    fun getMainComponent() = myMainPanel

    protected abstract fun getEditor(): Editor?

    fun getInputState(): FilterInputState {
        return getText().let{
            FilterInputState(it, if (it.isEmpty()) false else textContainsSyntheticFrameIdentifier())
        }
    }

    private fun configureCreatedDocumentFile(psiFile: PsiFile) {
        IgnoreAllIntentionsActionFilter.register(psiFile)
        IFilterInputCompletionContributor.COMPLETION_CONTRIBUTOR.set(psiFile, completionContributor)
    }

    private fun textContainsSyntheticFrameIdentifier(): Boolean {
        if (!completionContributor.isSyntheticIdentifierEnabled()) return false
        val editor = getEditor() ?: return false
        val project = editor.project ?: return false
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return false
        return MyCheckForSyntheticIdentifierVisitor().also { psiFile.accept(it) }.containsSyntheticFrameIdentifier()
    }

    private class MyCheckForSyntheticIdentifierVisitor: PsiRecursiveElementVisitor() {
        private var mySyntheticIdentifierUsed = false

        override fun visitElement(element: PsiElement) {
            if (mySyntheticIdentifierUsed) return

            if (element is LeafPsiElement) {
                mySyntheticIdentifierUsed = SyntheticDataFrameIdentifier.isIdentifier(element)
            } else {
                super.visitElement(element)
            }
        }

        fun containsSyntheticFrameIdentifier(): Boolean {
            return mySyntheticIdentifierUsed
        }
    }

    protected class MyDebuggerEditorsProvider(
        private val syntheticIdentifierType: DataFrameLibrary,
        private val createdDocumentFileProcessor: (psiFile: PsiFile) -> Unit,
    ) : PyDebuggerEditorsProvider() {
        fun createDocument(project: Project, expression: String, sourcePosition: XSourcePosition?): Document {
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
                project.service<SyntheticDataFramePsiRefProvider>()
                    .computeIfAbsent(syntheticIdentifierType) { computeSyntheticIdentifierPointer(project) }
                PsiDocumentManager.getInstance(project).getPsiFile(it)?.let { psiFile ->
                    createdDocumentFileProcessor(psiFile)
                }
            }
        }

        private fun computeSyntheticIdentifierPointer(project: Project): SmartPsiElementPointer<PyTargetExpression>? {
            val exprFragment = XDebuggerUtil.getInstance().createExpression(
                getSourceCodeToCreateIdentifier(),
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

        private fun getSourceCodeToCreateIdentifier(): String {
            val code = when (syntheticIdentifierType) {
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
}