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
package cms.rendner.intellij.dataframe.viewer.python.bridge

import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.ITableSourceCodeProvider
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyValueEvaluator
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import cms.rendner.intellij.dataframe.viewer.python.utils.parsePythonList
import cms.rendner.intellij.dataframe.viewer.python.utils.parsePythonString
import cms.rendner.intellij.dataframe.viewer.python.utils.stringifyImportWithObjectRef

/**
 * Injects the Python-specific plugin code into the Python process.
 * The injected code is available until the Python process is terminated.
 */
class PythonPluginCodeInjector {

    private data class RegisteredModulesInfo(
        val moduleImporterAvailable: Boolean,
        private val registeredModules: List<String>
    ) {
        fun isRegistered(moduleId: String) = registeredModules.contains(moduleId)
    }

    companion object {

        @Synchronized
        fun injectIfRequired(
            evaluator: IPluginPyValueEvaluator,
            codeProvider: ITableSourceCodeProvider,
        ) {
            val registeredModulesInfo = getRegisteredModulesInfo(evaluator)

            if (!registeredModulesInfo.moduleImporterAvailable) {
                val importer =
                    PythonPluginCodeInjector::class.java.getResource("/sdfv_base/plugin_modules_importer")!!.readText()
                evaluator.execute(importer)

                val base =
                    PythonPluginCodeInjector::class.java.getResource("/sdfv_base/plugin_modules_dump.json")!!.readText()
                registerModulesDump(evaluator, "base", base)
            }

            if (!registeredModulesInfo.isRegistered(codeProvider.getDataFrameLibrary().moduleName)) {
                val dump = codeProvider.getModulesDump(evaluator)
                val dumpId = codeProvider.getDataFrameLibrary().moduleName
                registerModulesDump(evaluator, dumpId, dump)
            }
        }

        private fun getRegisteredModulesInfo(evaluator: IPluginPyValueEvaluator): RegisteredModulesInfo {
            val methodRef = stringifyImportWithObjectRef("cms_rendner_sdfv.package_registry", "get_registered_dump_ids")
            return try {
                RegisteredModulesInfo(
                    true,
                    parsePythonList(evaluator.evaluate("$methodRef()").forcedValue).map(::parsePythonString),
                )
            } catch (ex: EvaluateException) {
                RegisteredModulesInfo(false, emptyList())
            } catch (ex: NullPointerException) {
                RegisteredModulesInfo(false, emptyList())
            }
        }

        private fun registerModulesDump(evaluator: IPluginPyValueEvaluator, dumpId: String, dump: String) {
            val methodRef = stringifyImportWithObjectRef("cms_rendner_sdfv.package_registry", "register_package_dump")
            // don't wrap json string with extra "" - it is a valid Python dict out of the box
            evaluator.execute("$methodRef('$dumpId', $dump)")
        }
    }
}