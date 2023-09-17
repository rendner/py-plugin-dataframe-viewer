/*
 * Copyright 2023 cms.rendner (Daniel Schmidt)
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
package cms.rendner.intellij.dataframe.viewer.python.bridge

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Converts [data] into a [dataClassName] instance.
 * [data] has to be serializable to a json-object where keys and values are valid Python syntax.
 *
 * @param json the json instance to encode the data.
 * @param data the data to encode as a Python dataclass.
 * @param dataClassName the class name of the Python dataclass.
 */
inline fun <reified T> convertToDataClass(json: Json, data: T, dataClassName: String): String {
    @OptIn(ExperimentalSerializationApi::class)
    return "${PythonPluginCodeInjector.getFromPluginModuleExpr(dataClassName)}(**${json.encodeToString(data)})"
}