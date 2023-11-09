/*
 * Copyright 2021-2023 cms.rendner (Daniel Schmidt)
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
package cms.rendner.intellij.dataframe.viewer.python.bridge.providers.pandas

import cms.rendner.intellij.dataframe.viewer.python.bridge.PandasVersion
import cms.rendner.intellij.dataframe.viewer.python.bridge.exceptions.InjectException
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.ITableSourceCodeProvider
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException

abstract class BaseCodeProvider: ITableSourceCodeProvider {
    final override fun getModulesDumpId() = "pandas"

    final override fun getModulesDump(evaluator: IPluginPyValueEvaluator): String {
        val pandasVersion = try {
            PandasVersion.fromString(evaluator.evaluate("__import__('pandas').__version__").forcedValue)
        } catch (ex: EvaluateException) {
            throw InjectException("Failed to identify version of pandas.", ex)
        }

        val resourcePath = getPluginCodeResourcePath(pandasVersion)
        return PatchedStylerCodeProvider::class.java.getResource(resourcePath)!!.readText()
    }

    private fun getPluginCodeResourcePath(version: PandasVersion): String {
        if (version.major == 1) {
            if (version.minor in 1..5) {
                return "/pandas_1.${version.minor}/plugin_modules_dump.json"
            }
        } else if (version.major == 2) {
            if (version.minor in 0..1) {
                return "/pandas_2.${version.minor}/plugin_modules_dump.json"
            }
        }
        throw InjectException("Unsupported $version.")
    }
}