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

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
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
import com.jetbrains.python.psi.PyExpressionCodeFragment
import com.jetbrains.python.psi.resolve.PyResolveUtil
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JPanel

interface IEditorChangedListener {
    fun editorInputChanged()
}

data class FilterInputState(val text: String, val containsSyntheticFrameIdentifier: Boolean = false)

abstract class AbstractEditorComponent : KeyAdapter() {
    protected val myEditorsProvider = MyDebuggerEditorsProvider(::processDocumentFile)

    private var changedListener: IEditorChangedListener? = null
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
            .addTooltip("Specify a DataFrame that contains the rows and columns to keep")
            .addLabeledComponent("", myErrorLabel)
            .panel
    }

    abstract fun setEnabled(enabled: Boolean)

    fun setChangedListener(listener: IEditorChangedListener?) {
        changedListener = listener
    }

    override fun keyReleased(e: KeyEvent?) {
        notifyInputChanged()
    }

    protected fun notifyInputChanged() {
        changedListener?.editorInputChanged()
    }

    abstract fun getText(): String

    abstract fun setSourcePosition(sourcePosition: XSourcePosition?)

    fun showErrorMessage(message: String) {
        myErrorLabel.isVisible = true
        myErrorLabel.text = message
        // error message will be truncated if dialog is too small
        // therefore also show the message as tooltip
        myErrorLabel.toolTipText = message
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

    private fun processDocumentFile(psiFile: PsiFile, withSyntheticIdentifier: Boolean) {
        IgnoreAllIntentionsActionFilter.register(psiFile)

        if (withSyntheticIdentifier && psiFile is PyExpressionCodeFragment) {
            SyntheticDataFrameIdentifier.allowSyntheticIdentifier(psiFile)
        }

        myExpressionComponentLabel.toolTipText = if (withSyntheticIdentifier) {
            "Hint: You can use the synthetic identifier '${SyntheticDataFrameIdentifier.NAME}' in the expression to refer to the used DataFrame."
        } else null
    }

    private fun textContainsSyntheticFrameIdentifier(): Boolean {
        val editor = getEditor() ?: return false
        val project = editor.project ?: return false
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return false
        if (SyntheticDataFrameIdentifier.isSyntheticIdentifierAllowed(psiFile)) {
            return MyCheckForSyntheticIdentifierVisitor().also { psiFile.accept(it) }.containsSyntheticFrameIdentifier()
        }
        return false
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
        private val documentFileProcessor: (psiFile: PsiFile, withSyntheticIdentifier: Boolean) -> Unit,
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
            val injectSyntheticIdentifier = !syntheticIdentifierNameExistsInContext(project, sourcePosition)
            return super.createDocument(
                project,
                expression,
                if (injectSyntheticIdentifier) createSyntheticSourcePosition(project, sourcePosition) else sourcePosition,
                EvaluationMode.EXPRESSION,
            ).also {
                PsiDocumentManager.getInstance(project).getPsiFile(it)?.let { psiFile ->
                    documentFileProcessor(psiFile, injectSyntheticIdentifier)
                }
            }
        }

        private fun syntheticIdentifierNameExistsInContext(project: Project, sourcePosition: XSourcePosition?): Boolean {
            getContextElement(project, sourcePosition)?.containingFile?.let {
                if (it is ScopeOwner) {
                    return PyResolveUtil.resolveLocally(it, SyntheticDataFrameIdentifier.NAME).isNotEmpty()
                }
            }
            return false
        }

        private fun createSyntheticSourcePosition(project: Project, sourcePosition: XSourcePosition?): XSourcePosition? {
            val debuggerUtil = XDebuggerUtil.getInstance()
            val documentManager = PsiDocumentManager.getInstance(project)

            val exprFragment = debuggerUtil.createExpression(
                SyntheticDataFrameIdentifier.getFragmentCode(),
                PythonLanguage.INSTANCE,
                null,
                EvaluationMode.CODE_FRAGMENT
            )

            val document = super.createDocument(project, exprFragment, sourcePosition, EvaluationMode.CODE_FRAGMENT)
            val psiFile = documentManager.getPsiFile(document)!!
            return debuggerUtil.createPositionByElement(psiFile.lastChild)
        }
    }
}