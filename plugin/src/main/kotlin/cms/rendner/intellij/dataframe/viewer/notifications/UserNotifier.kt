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

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Is safe to be called from any thread.
 */
class UserNotifier(private val project: Project?) {

    // NotificationGroup is registered in plugin.xml
    // https://plugins.jetbrains.com/docs/intellij/notifications.html#notificationgroup-20203-and-later
    companion object {
        val NOTIFICATION_GROUP: NotificationGroup = NotificationGroupManager
            .getInstance()
            .getNotificationGroup("cms.rendner.StyledDataFrameViewer")
    }

    fun error(title: String, reason: String, throwable: Throwable?) {
        NOTIFICATION_GROUP
            .createNotification(NotificationType.ERROR)
            .setTitle(title)
            .setContent(createContentFromError(reason, throwable))
            .notify(project)
    }

    private fun createContentFromError(reason: String, throwable: Throwable?): String {
        if (throwable == null) return reason
        return "$reason:${System.lineSeparator()}${stringifyExceptionWithFullStacktrace(throwable)}"
            .replace(System.lineSeparator(), "<br/>")
    }

    private fun stringifyExceptionWithFullStacktrace(throwable: Throwable): String {
        val target = StringWriter()
        throwable.printStackTrace(PrintWriter(target))
        return target.toString()
    }
}