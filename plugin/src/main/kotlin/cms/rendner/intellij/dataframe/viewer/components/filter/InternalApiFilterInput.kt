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

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.ErrorStripeEditorCustomization
import com.intellij.ui.PopupMenuListenerAdapter
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionComboBox
import javax.swing.event.PopupMenuEvent

@Suppress("unused")
class InternalApiFilterInput(
    project: Project,
    completionContributor: IFilterInputCompletionContributor,
    sourcePosition: XSourcePosition?,
) : AbstractFilterInput(completionContributor) {

    private val myEditor: XDebuggerExpressionComboBox

    init {
        myEditor = createEditor(project, sourcePosition)
        myEditorContainer.addToTop(myEditor.component)
    }

    override fun setEnabled(enabled: Boolean) {
        myEditor.setEnabled(enabled)
    }

    override fun getText(): String {
        return myEditor.expression.expression.trim()
    }

    override fun getEditor(): Editor? {
        return myEditor.editor
    }

    override fun saveInputInHistory() {
        myEditor.saveTextInHistory()
    }

    override fun setSourcePosition(sourcePosition: XSourcePosition?) {
        myEditor.setSourcePosition(sourcePosition)
    }

    private fun createEditor(project: Project, sourcePosition: XSourcePosition?): XDebuggerExpressionComboBox {
        val that = this
        return object : XDebuggerExpressionComboBox(
            project,
            myEditorsProvider,
            "cms.rendner.StyledDataFrameViewer",
            sourcePosition,
            false,
            false,
        ) {
            private var myComboBoxPopUpIsOpen = false
            private var shouldRequestFocus = false

            init {
                comboBox.addPopupMenuListener(object : PopupMenuListenerAdapter() {
                    override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {
                        myComboBoxPopUpIsOpen = true
                    }

                    override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent?) {
                        myComboBoxPopUpIsOpen = false
                        shouldRequestFocus = true
                    }
                })
            }

            override fun prepareEditor(editor: EditorEx) {
                editor.isEmbeddedIntoDialogWrapper = true
                editor.setHorizontalScrollbarVisible(true)
                ErrorStripeEditorCustomization.ENABLED.customize(editor)
                editor.settings.isShowIntentionBulb = false
                editor.colorsScheme.editorFontName = EditorUtil.getEditorFont().fontName
                editor.colorsScheme.editorFontSize = comboBox.font.size

                editor.contentComponent.addKeyListener(that)

                XDebuggerUtil.getInstance().disableValueLookup(editor)
            }

            override fun doSetText(text: XExpression?) {
                super.doSetText(text)
                if (!myComboBoxPopUpIsOpen) {
                    notifyInputChanged()
                    /*
                    Requesting the focus back solves two problems:

                    1) User can immediately adjust the selected text after selecting from the dropdown.

                    2) Fixes the following usability problem:
                        If the "Apply Filter"-btn of the dialog is disabled, the next focusable component
                        get the focus. In that case the "close"-button. Hitting Enter closes the dialog.
                     */
                    if (shouldRequestFocus) {
                        shouldRequestFocus = false
                        requestFocusInEditor()
                    }
                }
            }
        }
    }
}