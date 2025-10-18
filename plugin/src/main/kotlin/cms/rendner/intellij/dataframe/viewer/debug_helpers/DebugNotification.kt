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
package cms.rendner.intellij.dataframe.viewer.debug_helpers

import cms.rendner.intellij.dataframe.viewer.notifications.AbstractBalloonNotification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.text.StringUtil
import java.awt.datatransfer.StringSelection
import java.lang.Enum
import java.lang.StringBuilder
import java.lang.reflect.Modifier

class DebugNotification {
    companion object {
        fun stringify(text: String, info: Any? = null) {
            runInEdt {
                Notification(text, convertToString(info)).notify(null)
            }
        }

        fun typeDefinition(text: String, info: Any) {
            runInEdt {
                Notification(text, generateTypeDefinitionFromInstance(info)).notify(null)
            }
        }
    }

    private class Notification(text: String, info: String) : AbstractBalloonNotification(
    "[Debug] $text",
        StringUtil.escapeXmlEntities(info),
    NotificationType.INFORMATION,
    ) {init {
        addAction(CopyToClipboardAction(info))
    }

        private class CopyToClipboardAction(
            private val text: String,
        ) : AnAction("Copy To Clipboard"), DumbAware {

            override fun getActionUpdateThread() = ActionUpdateThread.EDT

            override fun actionPerformed(p0: AnActionEvent) {
                try {
                    CopyPasteManager.getInstance().setContents(StringSelection(text))
                } catch (_: Exception) {
                }
            }
        }
    }
}

private fun convertToString(info: Any?): String {
    return when (info) {
        null -> "<null>"
        is String -> info
        is Throwable -> {
            "${info.message}\n${info.stackTraceToString()}"
        }
        else -> info.toString()
    }
}

private fun generateTypeDefinitionFromInstance(instance: Any): String {
    val clazz: Class<*> = instance.javaClass
    val kClass = clazz.kotlin
    val sb = StringBuilder()

    // determine type keyword
    val typeKeyword = when {
        clazz.isInterface -> "interface"
        clazz.isEnum -> "enum class"
        clazz.isRecord -> "record"
        kClass.isData -> "data class"
        kClass.isValue -> "value class"
        Modifier.isAbstract(clazz.modifiers) -> "abstract class"
        else -> "class"
    }

    sb.append("$typeKeyword ${clazz.simpleName}")

    // extended superclass (if not Object or Enum for enums/records)
    val superClass = clazz.superclass
    if (superClass != null && superClass != Object::class.java &&
        (!clazz.isEnum || superClass != java.lang.Enum::class.java) &&
        (!clazz.isRecord || superClass != Record::class.java)) {
        sb.append(" : ${superClass.name}")
    }

    // implemented interfaces
    val interfaces = clazz.interfaces
    if (interfaces.isNotEmpty()) {
        if (superClass == null || superClass == Object::class.java ||
            (clazz.isEnum && superClass == Enum::class.java) ||
            (clazz.isRecord && superClass == Record::class.java)) {
            sb.append(" : ")
        } else {
            sb.append(", ")
        }
        sb.append(interfaces.joinToString(", ") { it.name })
    }

    sb.append(" {\n")

    // extract fields
    for (field in clazz.declaredFields) {
        val modifiers = Modifier.toString(field.modifiers)
        sb.append("\t$modifiers ${field.type.name} ${field.name}")

        try {
            field.isAccessible = true
            var value = if (Modifier.isStatic(field.modifiers)) {
                field.get(null)
            } else {
                field.get(instance)
            }

            val formattedValue = when {
                value is String -> "\"${StringUtil.escapeStringCharacters(value)}\""
                field.type.isPrimitive || value is Number || value is Boolean -> value.toString()
                else -> "<$value>"
            }

            sb.append(" = $formattedValue")
        } catch (e: Exception) {
            sb.append(" = <error: ${e.message}>")
        }
        sb.append(";\n")
    }

    // extract methods
    for (method in clazz.declaredMethods) {
        val modifiers = Modifier.toString(method.modifiers)
        val params = method.parameters.joinToString(", ") { "${it.type.name} ${it.name}" }
        sb.append("\t$modifiers ${method.returnType.name} ${method.name}($params);\n")
    }

    sb.append("}")
    return sb.toString()
}
