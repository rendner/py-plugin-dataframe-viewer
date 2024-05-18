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

import cms.rendner.intellij.dataframe.viewer.python.DataFrameLibrary
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.intellij.ui.ErrorStripeEditorCustomization
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.jetbrains.python.PythonFileType

class DefaultEditorComponent(
    project: Project,
    syntheticIdentifierType: DataFrameLibrary,
    sourcePosition: XSourcePosition?,
) : AbstractEditorComponent(syntheticIdentifierType) {

    private val myEditor: EditorTextField

    init {
        myEditor = createEditor(project, sourcePosition)
        myEditorContainer.addToTop(myEditor)
    }

    override fun setEnabled(enabled: Boolean) {
        myEditor.editor?.contentComponent?.let {
            it.isEnabled = enabled
            it.isFocusable = enabled
        }
    }

    override fun getText(): String {
        return myEditor.text.trim()
    }

    override fun setSourcePosition(sourcePosition: XSourcePosition?) {
        myEditor.document =
            myEditorsProvider.createDocument(myEditor.project, myEditor.text, sourcePosition)
    }

    override fun getEditor(): Editor? {
        return myEditor.editor
    }

    private fun createEditor(project: Project, sourcePosition: XSourcePosition?): EditorTextField {
        val that = this
        return EditorTextField(
            myEditorsProvider.createDocument(project, "", sourcePosition),
            project,
            PythonFileType.INSTANCE,
            false,
        ).apply {
            setFontInheritedFromLAF(false)
            addSettingsProvider { editor ->
                editor.colorsScheme.editorFontName = EditorUtil.getEditorFont().fontName
                editor.settings.isShowIntentionBulb = false
                editor.isOneLineMode = true
                editor.isEmbeddedIntoDialogWrapper = true
                editor.setHorizontalScrollbarVisible(true)

                ErrorStripeEditorCustomization.ENABLED.customize(editor)
                SpellCheckingEditorCustomizationProvider.getInstance().disabledCustomization?.customize(editor)

                XDebuggerUtil.getInstance().disableValueLookup(editor)

                editor.contentComponent.addKeyListener(that)
            }
        }
    }
}