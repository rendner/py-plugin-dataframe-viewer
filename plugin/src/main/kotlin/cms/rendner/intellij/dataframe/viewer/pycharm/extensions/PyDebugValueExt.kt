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
package cms.rendner.intellij.dataframe.viewer.pycharm.extensions

import cms.rendner.intellij.dataframe.viewer.pycharm.PythonQualifiedTypes
import com.jetbrains.python.debugger.PyDebugValue

fun PyDebugValue.isDataFrame(): Boolean = this.qualifiedType == "pandas.core.frame.DataFrame"
fun PyDebugValue.isStyler(): Boolean = this.qualifiedType == "pandas.io.formats.style.Styler"
fun PyDebugValue.isNone(): Boolean = qualifiedType == PythonQualifiedTypes.None.value
fun PyDebugValue.varName(): String {
    return if (this.name.startsWith("'")) {
        // like: "'styler' (1234567890)"
        this.name.substring(1, this.name.indexOf("'", 1))
    }
    // like "0" (for example index 0 in a tuple)
    else this.name
}