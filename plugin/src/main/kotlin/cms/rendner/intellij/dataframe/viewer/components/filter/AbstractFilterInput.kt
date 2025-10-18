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

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI.Panels
import com.intellij.xdebugger.XSourcePosition
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JPanel



abstract class AbstractFilterInput(
    protected val myEditorsProvider: FilterInputEditorsProvider,
    ): KeyAdapter(), Disposable {
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

    fun containsSyntheticIdentifier(): Boolean {
        val editor = getEditor() ?: return false
        val project = editor.project ?: return false
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return false
        return MySearchForSyntheticIdentifierVisitor().also { psiFile.accept(it) }.syntheticIdentifierFound()
    }

    override fun dispose() {}
}


private class MySearchForSyntheticIdentifierVisitor: PsiRecursiveElementVisitor() {
    private var mySyntheticIdentifierFound = false

    override fun visitElement(element: PsiElement) {
        if (mySyntheticIdentifierFound) return

        if (element is LeafPsiElement) {
            mySyntheticIdentifierFound = SyntheticDataFrameIdentifier.isIdentifier(element)
        } else {
            super.visitElement(element)
        }
    }

    fun syntheticIdentifierFound(): Boolean {
        return mySyntheticIdentifierFound
    }
}
