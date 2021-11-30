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