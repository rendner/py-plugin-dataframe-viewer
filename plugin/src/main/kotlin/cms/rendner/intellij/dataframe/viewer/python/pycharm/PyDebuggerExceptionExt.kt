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
package cms.rendner.intellij.dataframe.viewer.python.pycharm

import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.PluginPyDebuggerException
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.python.debugger.PyDebuggerException

/**
 * Converts the PyCharm [PyDebuggerException] into a plugin internal exception.
 */
fun PyDebuggerException.toPluginType(): PluginPyDebuggerException {
    val truncatedMessage = message?.let {
        // https://docs.python.org/3/library/traceback.html#traceback-examples
        val lastTracebackIndex = it.lastIndexOf("Traceback (most recent call last):")
        if (lastTracebackIndex != -1) {
            // truncate evaluated code (could be too many lines which are not required to have in the error)
            val blankLineIndex = it.indexOf("\n\n", lastTracebackIndex)
            val maxLength = if (blankLineIndex > -1) blankLineIndex else lastTracebackIndex + 200
            StringUtil.first(it, maxLength, true)
        } else {
            it
        }
    } ?: "Unknown debugger exception occurred."
    return PluginPyDebuggerException(
        truncatedMessage,
        if (this === cause) null else cause,
    )
}