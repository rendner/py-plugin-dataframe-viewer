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
package cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions

import com.jetbrains.python.PyBundle

private val ANSI_ESCAPE_CODE_REGEX = Regex("\u001B\\[[0-9;]+m")

class PluginPyDebuggerException(
    message: String,
    cause: Throwable? = null,
    val isDisconnected: Boolean = false,
    ) : Exception(message, cause) {

    companion object {
        fun fromPyCharmDebuggerException(exception: Exception, isDisconnected: Boolean = false): PluginPyDebuggerException {
            // https://docs.python.org/3/library/traceback.html#traceback-examples
            val truncatedMessage = exception.message?.let {
                val lastTracebackIndex = it.lastIndexOf("Traceback (most recent call last):")
                val extracted = if (lastTracebackIndex != -1) {
                    it.substring(lastTracebackIndex)
                } else {
                    it
                }
                // Strip ANSI escape codes for better readability
                extracted.replace(ANSI_ESCAPE_CODE_REGEX, "")
            } ?: "Unknown debugger exception occurred."

            return PluginPyDebuggerException(
                truncatedMessage,
                if (exception === exception.cause) null else exception.cause,
                isDisconnected,
            )
        }
    }

    fun isProcessIsRunningException(): Boolean {
        // In PyCharm a "PyDebuggerException" with the message "Process is running"
        // is thrown when XDebugSession::getCurrentStackFrame returns null in PyDebugProcess::currentFrame.
        // This happens when the session is resumed (not paused).
        return message == "Process is running" || message == PyBundle.message("debugger.debug.process.running")
    }
}
