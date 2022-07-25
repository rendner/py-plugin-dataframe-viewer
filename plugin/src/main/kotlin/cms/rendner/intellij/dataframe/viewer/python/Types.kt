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
package cms.rendner.intellij.dataframe.viewer.python

object PythonQualifiedTypes {
    const val None = "builtins.NoneType"
    const val List = "builtins.list"
    const val Tuple = "builtins.tuple"
    const val Dict = "builtins.dict"
    const val Function = "builtins.function"
    const val Int = "builtins.int"
    const val Float = "builtins.float"
    const val Float64 = "numpy.float64"
    const val Index = "pandas.core.indexes.base.Index"
    const val Module = "builtins.module"
}