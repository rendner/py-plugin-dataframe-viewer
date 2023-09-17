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

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.xdebugger.XDebugSession
import java.util.concurrent.ConcurrentHashMap

class PandasAvailableInSessionProvider {
    companion object {
        // A user can start more than one debug process in parallel per project.
        // Therefore, a map is used to store the result.
        private val KEY: Key<MutableMap<String, Boolean?>> = Key.create("cms.rendner.pandasVersionInSession")

        fun init(project: Project) {
            KEY.set(project, ConcurrentHashMap())
        }

        fun cleanup(project: Project) {
            KEY.set(project, null)
        }

        fun remove(session: XDebugSession) {
            KEY.get(session.project)?.remove(createSessionFingerprint(session))
        }

        fun setIsAvailable(session: XDebugSession) {
            KEY.get(session.project)?.put(createSessionFingerprint(session), true)
        }

        fun isAvailable(session: XDebugSession): Boolean? {
            return KEY.get(session.project)?.get(createSessionFingerprint(session))
        }

        /**
         * Creates a fingerprint to identify a session without having to store a reference (to prevent memory leaks).
         * (expects the name and hash code of a session not to change)
         */
        private fun createSessionFingerprint(session: XDebugSession): String {
            return "${session.sessionName}_${session.hashCode()}"
        }
    }
}