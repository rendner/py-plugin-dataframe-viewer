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
package cms.rendner.intellij.dataframe.viewer.python.bridge

class PythonCodeProviderFactory {

    companion object {
        fun createProviderFor(version: PandasVersion): PythonCodeProvider? {
            if (version.major == 1) {
                if (version.minor == 1) {
                    return PythonCodeProvider(PandasVersion(1, 1), "/pandas_1.1/plugin_code")
                }
                if (version.minor == 2) {
                    return PythonCodeProvider(PandasVersion(1, 2), "/pandas_1.2/plugin_code")
                }
                if (version.minor == 3) {
                    return PythonCodeProvider(PandasVersion(1, 3), "/pandas_1.3/plugin_code")
                }
                if (version.minor == 4) {
                    return PythonCodeProvider(PandasVersion(1, 4), "/pandas_1.4/plugin_code")
                }
            }

            return null
        }
    }
}