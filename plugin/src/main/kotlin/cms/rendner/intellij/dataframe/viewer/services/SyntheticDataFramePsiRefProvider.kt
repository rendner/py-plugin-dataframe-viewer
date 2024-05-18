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
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.psi.SmartPsiElementPointer
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.rd.util.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class SyntheticDataFramePsiRefProvider: Disposable {
    private val myPointers = ConcurrentHashMap<DataFrameLibrary, SmartPsiElementPointer<PyTargetExpression>?>()

    fun computeIfAbsent(type: DataFrameLibrary, computer: () -> SmartPsiElementPointer<PyTargetExpression>?) {
        myPointers.computeIfAbsent(type) { computer() }
    }

    fun getPointer(type: DataFrameLibrary): SmartPsiElementPointer<PyTargetExpression>? {
        return myPointers[type]
    }

    override fun dispose() {
        myPointers.clear()
    }
}