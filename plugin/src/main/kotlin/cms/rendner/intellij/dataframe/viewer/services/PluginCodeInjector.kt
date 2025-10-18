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
package cms.rendner.intellij.dataframe.viewer.services

import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.ITableSourceCodeProvider
import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyFrameAccessor
import cms.rendner.intellij.dataframe.viewer.python.utils.stringifyImportWithObjectRef
import com.intellij.openapi.components.Service
import com.jetbrains.rd.util.AtomicReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean


class InjectionState {
    val moduleImporterAvailable = AtomicBoolean(false)
    private val myRegisteredModules = AtomicReference<List<String>>(emptyList())

    fun isRegistered(moduleId: String) = myRegisteredModules.get().contains(moduleId)
    fun addRegisteredModule(moduleId: String) {
        myRegisteredModules.getAndUpdate { listOf(moduleId) + it }
    }
}

/**
 * Injects the Python-specific plugin code into the Python process.
 * The injected code is available until the Python process is terminated.
 */
@Service(Service.Level.PROJECT)
class PluginCodeInjector {

    private val myInjectionStateByFrameAccessor = ConcurrentHashMap<String, InjectionState>()

    fun getInjectionState(frameAccessor: IPluginPyFrameAccessor): InjectionState? {
        return myInjectionStateByFrameAccessor[frameAccessor.getUniqueId()]
    }

    @Synchronized
    fun injectIfRequired(
        frameAccessor: IPluginPyFrameAccessor,
        codeProvider: ITableSourceCodeProvider,
    ) {
        val injectionState = myInjectionStateByFrameAccessor.getOrPut(frameAccessor.getUniqueId()) { InjectionState() }

        if (!injectionState.moduleImporterAvailable.get()) {
            val importer =
                PluginCodeInjector::class.java.getResource("/generated_dumps/sdfv_base/plugin_modules_importer")!!.readText()
            frameAccessor.execute(importer)

            val base =
                PluginCodeInjector::class.java.getResource("/generated_dumps/sdfv_base/plugin_modules_dump.json")!!.readText()
            registerModulesDump(frameAccessor, "base", base)

            injectionState.moduleImporterAvailable.set(true)
        }

        if (!injectionState.isRegistered(codeProvider.getDataFrameLibrary().moduleName)) {
            val dump = codeProvider.getModulesDump(frameAccessor)
            val dumpId = codeProvider.getDataFrameLibrary().moduleName
            registerModulesDump(frameAccessor, dumpId, dump)

            injectionState.addRegisteredModule(dumpId)
        }
    }

    private fun registerModulesDump(frameAccessor: IPluginPyFrameAccessor, dumpId: String, dump: String) {
        val methodRef = stringifyImportWithObjectRef("cms_rendner_sdfv.package_registry", "register_package_dump")
        // don't wrap json string with extra "" - it is a valid Python dict out of the box
        frameAccessor.execute("$methodRef('$dumpId', $dump)")
    }
}