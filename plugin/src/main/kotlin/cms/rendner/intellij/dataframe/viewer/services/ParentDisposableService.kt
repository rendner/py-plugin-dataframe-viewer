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
package cms.rendner.intellij.dataframe.viewer.services

import com.intellij.openapi.Disposable

/**
 * Even though Application and Project implement Disposable, they must NEVER be used as parent
 * disposables in plugin code. Disposables registered using those objects as parents will not
 * be disposed when the plugin is unloaded, leading to memory leaks.
 * Source: https://jetbrains.org/intellij/sdk/docs/basics/disposers.html#choosing-a-disposable-parent
 *
 * This class is registered as a project-service. Project-level services are disposed when
 * the project is closed, or the plugin is unloaded.
 * Source: https://jetbrains.org/intellij/sdk/docs/basics/disposers.html#automatically-disposed-objects
 */
// https://plugins.jetbrains.com/docs/intellij/disposers.html#diagnosing-disposer-leaks
class ParentDisposableService: Disposable {

    /**
     * Does nothing - but the method is required by the the [Disposable] interface.
     */
    override fun dispose() {
    }
}