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
package cms.rendner.intellij.dataframe.viewer.notifications

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.text.StringUtil
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.JOptionPane

/**
 * Notification about an error.
 *
 * @param groupId the notification group id
 * @param title the title of the notification
 * @param content the content of the notification
 * @param throwable the throwable to be reported
 */
class ErrorNotification(
    groupId: String,
    title: String,
    content: String,
    throwable: Throwable,
) : Notification(
    groupId,
    title,
    content,
    NotificationType.ERROR,
) {

    init {
        addAction(ShowErrorAction(title, content, throwable))
        addAction(CopyToClipboardAction(content, throwable))
    }

    private class CopyToClipboardAction(
        private val content: String,
        private val throwable: Throwable,
    ) : AnAction("Copy To Clipboard"), DumbAware {
        override fun actionPerformed(p0: AnActionEvent) {
            val selection = StringSelection("$content\n\n${throwable.stackTraceToString()}")
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
        }
    }

    private class ShowErrorAction(
        private val title: String,
        private val content: String,
        private val throwable: Throwable,
    ) : AnAction("Show Error"), DumbAware {
        override fun actionPerformed(event: AnActionEvent) {
            showHtmlMessageDialog(
                event.project,
                "<html><h3>$content</h3><pre>${StringUtil.escapeXmlEntities(throwable.stackTraceToString())}</pre></html>",
                title,
                JOptionPane.ERROR_MESSAGE,
            ) { messageScrollPane ->
                messageScrollPane.preferredSize = Dimension(messageScrollPane.preferredSize.width, 250)
            }
        }
    }
}