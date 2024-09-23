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

import cms.rendner.intellij.dataframe.viewer.MyPlugin
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType

// NotificationGroup is registered in plugin.xml
// https://plugins.jetbrains.com/docs/intellij/notifications.html#notificationgroup-20203-and-later
private val BALLOON: NotificationGroup = NotificationGroupManager
    .getInstance()
    .getNotificationGroup(MyPlugin.NOTIFICATION_GROUP)

abstract class AbstractBalloonNotification(
    title: String,
    content: String,
    type: NotificationType,
): Notification(BALLOON.displayId, title, content, type)