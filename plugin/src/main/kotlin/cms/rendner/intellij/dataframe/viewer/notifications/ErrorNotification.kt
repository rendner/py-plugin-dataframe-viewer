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
package cms.rendner.intellij.dataframe.viewer.notifications

import cms.rendner.intellij.dataframe.viewer.DataFrameViewerIcons
import cms.rendner.intellij.dataframe.viewer.MyPlugin
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.text.StringUtil
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import java.lang.Integer.min
import javax.swing.JOptionPane

/**
 * Notification about an error.
 *
 * @param title the title of the notification
 * @param content the content of the notification
 * @param throwable the throwable to be reported
 */
class ErrorNotification(
    title: String,
    content: String,
    throwable: Throwable?,
) : AbstractBalloonNotification(
    title,
    content,
    NotificationType.ERROR,
) {

    init {
        icon = DataFrameViewerIcons.LOGO_16
        if (throwable != null) {
            addAction(ShowErrorAction(title, content, throwable))
            addAction(CopyToClipboardAction(content, throwable))
        }
    }

    private class CopyToClipboardAction(
        private val content: String,
        private val throwable: Throwable,
    ) : AnAction("Copy To Clipboard"), DumbAware {

        override fun getActionUpdateThread() = ActionUpdateThread.EDT

        private fun appendPluginAndIdeInfo(sb: StringBuilder) {
            try {
                sb.appendLine("pluginVersion: ${PluginManagerCore.getPlugin(PluginId.getId(MyPlugin.ID))?.version ?: "unknown"}")
                sb.appendLine("IDE-buildNumber: ${ApplicationInfo.getInstance().build.asString()}")
            } catch (ignore: Exception) {}
        }

        override fun actionPerformed(p0: AnActionEvent) {
            val message =
                StringBuilder().apply {
                    appendPluginAndIdeInfo(this)
                    appendLine()
                    append(content)
                    appendLine()
                    appendLine()
                    append(throwable.stackTraceToString())
                }.toString()
            try {
                CopyPasteManager.getInstance().setContents(StringSelection(message))
            } catch (ignore: Exception) { }
        }
    }

    private class ShowErrorAction(
        private val title: String,
        private val content: String,
        private val throwable: Throwable,
    ) : AnAction("Show Error"), DumbAware {

        override fun getActionUpdateThread() = ActionUpdateThread.EDT

        override fun actionPerformed(event: AnActionEvent) {
            val message = StringUtil.escapeXmlEntities(throwable.stackTraceToString())
            showHtmlMessageDialog(
                event.project,
                "<html><h3>$content</h3><pre style='font-size: 11px'>$message</pre> </html>",
                title,
                JOptionPane.ERROR_MESSAGE,
            ) { messageScrollPane ->
                messageScrollPane.preferredSize = Dimension(
                    min(800, messageScrollPane.preferredSize.width),
                    250,
                )
            }
        }
    }
}