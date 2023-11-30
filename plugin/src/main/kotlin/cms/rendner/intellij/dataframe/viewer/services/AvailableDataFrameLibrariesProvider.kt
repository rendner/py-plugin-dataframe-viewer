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
package cms.rendner.intellij.dataframe.viewer.services

import cms.rendner.intellij.dataframe.viewer.python.DataFrameLibrary
import com.intellij.openapi.components.Service
import com.intellij.xdebugger.XDebugSession
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class AvailableDataFrameLibrariesProvider {
    private val cache: MutableMap<String, List<DataFrameLibrary>?> = ConcurrentHashMap()

    fun setLibraries(session: XDebugSession, libraries: List<DataFrameLibrary>) {
        cache[createSessionFingerprint(session)] = libraries
    }

    fun getLibraries(session: XDebugSession): List<DataFrameLibrary>? {
        return cache[createSessionFingerprint(session)]
    }

    fun hasResult(session: XDebugSession): Boolean {
        return cache[createSessionFingerprint(session)] != null
    }

    /**
     * Creates a fingerprint to identify a session without having to store a reference (to prevent memory leaks).
     * (expects the name and hash code of a session not to change)
     */
    private fun createSessionFingerprint(session: XDebugSession): String {
        return "${session.sessionName}_${session.hashCode()}"
    }
}