/*
 * Copyright 2021-2024 cms.rendner (Daniel Schmidt)
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

import cms.rendner.intellij.dataframe.viewer.python.DataFrameLibrary
import com.intellij.openapi.components.Service
import com.jetbrains.python.debugger.PyFrameAccessor
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class AvailableDataFrameLibrariesProvider {
    private val cache: MutableMap<String, List<DataFrameLibrary>?> = ConcurrentHashMap()

    fun setLibraries(frameAccessor: PyFrameAccessor, libraries: List<DataFrameLibrary>) {
        cache[createFingerprint(frameAccessor)] = libraries
    }

    fun getLibraries(frameAccessor: PyFrameAccessor): List<DataFrameLibrary>? {
        return cache[createFingerprint(frameAccessor)]
    }

    fun hasResult(frameAccessor: PyFrameAccessor): Boolean {
        return cache[createFingerprint(frameAccessor)] != null
    }

    /**
     * Creates a fingerprint to identify a [frameAccessor] without having to store a reference (to prevent memory leaks).
     * (expects "toString()" returns a stable result like "com.jetbrains.python.debugger.PyDebugProcess@18237333")
     */
    private fun createFingerprint(frameAccessor: PyFrameAccessor): String {
        return frameAccessor.toString()
    }
}