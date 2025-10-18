/*
 * Copyright 2021-2026 cms.rendner (Daniel Schmidt)
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

import com.jetbrains.python.console.PydevConsoleCommunication
import com.jetbrains.python.debugger.IPyDebugProcess
import com.jetbrains.python.debugger.PyFrameAccessor

fun PyFrameAccessor.isConsole(): Boolean {
    return this is PydevConsoleCommunication
}

fun PyFrameAccessor.isDebugger(): Boolean {
    return this is IPyDebugProcess
}

fun PyFrameAccessor.simulateExec(): Boolean {
    // It seems exec isn't implemented for the JupyterVarsFrameAccessor and console based frameAccessor (in PyCharm).
    return this.isConsole()
}

fun PyFrameAccessor.isSupported(): Boolean {
    return this.isDebugger() || this.isConsole()
}
