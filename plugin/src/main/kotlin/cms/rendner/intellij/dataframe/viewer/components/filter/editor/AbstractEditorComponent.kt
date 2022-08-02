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

import cms.rendner.intellij.dataframe.viewer.components.filter.IFilterEvalExprBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
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
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.debugger.PyDebuggerEditorsProvider
import com.jetbrains.python.psi.resolve.PyResolveUtil
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JPanel

private const val SYNTHETIC_DATAFRAME_IDENTIFIER = "_df"

interface IEditorChangedListener {
    fun editorInputChanged()
}

abstract class AbstractEditorComponent : KeyAdapter() {
    protected val myEditorsProvider = MyDebuggerEditorsProvider()

    private var changedListener: IEditorChangedListener? = null
    protected val myEditorContainer = Panels.simplePanel()
    private val myMainPanel: JPanel
    private val myErrorLabel: JBLabel = JBLabel()

    init {
        myErrorLabel.icon = AllIcons.General.Error
        myErrorLabel.foreground = JBColor.RED
        myErrorLabel.isVisible = false
        // set an explicit min width to not increase the width when a long error message is displayed
        myErrorLabel.minimumSize = myErrorLabel.minimumSize.also { it.width = 100 }

        val expressionComponentLabel = JBLabel("Filter:").apply {
            toolTipText = "Hint: You can use the identifier '$SYNTHETIC_DATAFRAME_IDENTIFIER' to refer to the displayed DataFrame."
        }

        myMainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(expressionComponentLabel, myEditorContainer)
            .addTooltip("Specify a DataFrame that contains the rows and columns to keep")
            .addLabeledComponent("", myErrorLabel)
            .panel
    }

    abstract fun setEnabled(enabled: Boolean)

    fun setChangedListener(listener: IEditorChangedListener?) {
        changedListener = listener
    }


    override fun keyReleased(e: KeyEvent?) {
        notifyChange()
    }

    protected fun notifyChange() {
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

    fun createFilterExprBuilder(): IFilterEvalExprBuilder {
        return getEditor()?.let { editor ->
            val psiFile = PsiDocumentManager.getInstance(editor.project!!).getPsiFile(editor.document)!!
            if (MyDebuggerEditorsProvider.SYNTHETIC_IDENTIFIER_INJECTED.get(psiFile, false)) {
                MyFilterExpressionVisitor().also { psiFile.accept(it) }
            } else MySimpleFilterEvalExprBuilder(psiFile.text)
        } ?: MySimpleFilterEvalExprBuilder("")
    }

    private class MySimpleFilterEvalExprBuilder(private val result: String) : IFilterEvalExprBuilder {
        override fun build(dataFrameRefExpr: String?) = result
    }

    private class MyFilterExpressionVisitor: IFilterEvalExprBuilder, PsiRecursiveElementVisitor() {
        private val myStringBuilder = StringBuilder()
        private val myDataFrameIndices = mutableListOf<Int>()

        override fun visitElement(element: PsiElement) {
            if (element is LeafPsiElement) {
                element.text.let {
                    if (element.elementType == PyTokenTypes.IDENTIFIER && it == SYNTHETIC_DATAFRAME_IDENTIFIER) {
                        myDataFrameIndices.add(myStringBuilder.length)
                    } else {
                        myStringBuilder.append(it)
                    }
                }
            } else {
                super.visitElement(element)
            }
        }

        override fun build(dataFrameRefExpr: String?): String {
            return if (myDataFrameIndices.isEmpty()) {
                myStringBuilder.toString()
            } else {
                var last = 0
                val result = StringBuilder()
                myDataFrameIndices.forEach {
                    result.append(myStringBuilder.substring(last, it))
                    result.append(dataFrameRefExpr ?: SYNTHETIC_DATAFRAME_IDENTIFIER)
                    last = it
                }
                if (last < myStringBuilder.length) {
                    result.append(myStringBuilder.substring(last))
                }
                result.toString()
            }
        }
    }

    protected class MyDebuggerEditorsProvider : PyDebuggerEditorsProvider() {

        companion object {
            val SYNTHETIC_IDENTIFIER_INJECTED =
                Key.create<Boolean>("${Companion::class.java.name}.SYNTHETIC_IDENTIFIER_INJECTED")
        }

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
            val debuggerUtil = XDebuggerUtil.getInstance()
            val documentManager = PsiDocumentManager.getInstance(project)

            /*
            The synthetic code is used to inject an identifier of type DataFrame.
            IntelliJ provides automatically full code completion for the injected identifier.
            The identifier is injected by using an extra fragment to hide it from the user.
            Otherwise, the code would be visible in the displayed editor.

            The comment in the snippet is a description for the user in case the user navigates
            to the definition of injected identifier.
             */
            val syntheticCode = """
                |# plugin: "Styled DataFrame Viewer"
                |# helper for providing the synthetic identifier "$SYNTHETIC_DATAFRAME_IDENTIFIER"
                |import pandas as pd
                |$SYNTHETIC_DATAFRAME_IDENTIFIER = pd.DataFrame()
                |breakpoint()
            """.trimMargin()
            val syntheticCodeExpr = debuggerUtil.createExpression(
                syntheticCode,
                PythonLanguage.INSTANCE,
                null,
                EvaluationMode.CODE_FRAGMENT
            )
            val syntheticDocument =
                super.createDocument(project, syntheticCodeExpr, sourcePosition, EvaluationMode.CODE_FRAGMENT)
            val syntheticPsiFile = documentManager.getPsiFile(syntheticDocument)!!

            /*
            It doesn't make sense to shadow a user defined variable with the same name us the synthetic identifier.
            Because, the code editor shows a popup when the user hovers over an identifier.
            The popup displays the evaluated fields and values for the identifier.
            It seems that the content of the popup is taken from the current source position of the debugger and
            not resolved from the hovered element.

            The user-code defined variable could point to another DataFrame or be of another type.
            The displayed information in the popup would be irritating for the user. Therefore, check if the identifier
            is already used.
             */
            val canUseSyntheticCodeFragment = !shadowsIdentifierInContextFile(syntheticPsiFile)


            val documentSourcePosition = if (canUseSyntheticCodeFragment) {
                debuggerUtil.createPositionByElement(syntheticPsiFile.lastChild)
            } else sourcePosition

            return super.createDocument(project, expression, documentSourcePosition, EvaluationMode.EXPRESSION).apply {
                documentManager.getPsiFile(this)?.let {
                    IgnoreAllIntentionsActionFilter.IGNORE.set(it, true)
                    if (canUseSyntheticCodeFragment) {
                        SYNTHETIC_IDENTIFIER_INJECTED.set(it, true)
                        PyCodeFragmentReferenceResolveProvider.RESOLVE_REFERENCES.set(it, true)
                        SyntheticIdentifierHighlighter.HIGHLIGHT.set(it, setOf(SYNTHETIC_DATAFRAME_IDENTIFIER))
                    }
                }
            }
        }

        private fun shadowsIdentifierInContextFile(element: PsiElement): Boolean {
            element.context?.containingFile?.let {
                if (it is ScopeOwner) {
                    return PyResolveUtil.resolveLocally(it, SYNTHETIC_DATAFRAME_IDENTIFIER).isNotEmpty()
                }
            }
            return false
        }
    }
}