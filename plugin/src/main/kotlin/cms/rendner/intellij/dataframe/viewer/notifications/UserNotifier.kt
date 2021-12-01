/*
 * Copyright 2021 cms.rendner (Daniel Schmidt)
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

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/**
 * Is safe to be called from any thread. [Notification::notify] takes care that the message is dispatched in ed-thread.
 */
class UserNotifier(private val project: Project?) {

    fun error(message: String) {
        showNotification(message, NotificationType.ERROR)
    }

    fun warning(message: String) {
        showNotification(message, NotificationType.WARNING)
    }

    fun info(message: String) {
        showNotification(message, NotificationType.INFORMATION)
    }

    private fun showNotification(content: String, type: NotificationType) {
        // NotificationGroup is registered in plugin.xml
        // https://github.com/JetBrains/intellij-community/blob/ff6cdad938004e24be720f0a1bef1255f2a17b0c/platform/platform-api/src/com/intellij/notification/NotificationGroup.kt#L26
        // https://plugins.jetbrains.com/docs/intellij/notifications.html#notificationgroup-20203-and-later
        NotificationGroupManager.getInstance().getNotificationGroup("cms.rendner.StyledDataFrameViewer")
            .createNotification(content, type)
            .notify(project)
    }
}