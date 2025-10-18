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

import cms.rendner.intellij.dataframe.viewer.python.debugger.IPluginPyFrameAccessor
import cms.rendner.intellij.dataframe.viewer.python.pycharm.PluginPyFrameAccessor
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.xdebugger.XDebugSessionListener
import com.jetbrains.python.debugger.IPyDebugProcess
import com.jetbrains.python.debugger.PyFrameAccessor
import com.jetbrains.python.debugger.PyFrameListener
import java.lang.IllegalStateException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.WeakHashMap
import kotlin.collections.forEach

/**
 * Listener interface for debug session events.
 * Implement this interface to receive notifications about debug session state changes.
 *
 * Sequence of calls between 2 breakpoints or a step operation in the debugger:
 * - beforeSessionResume
 * - sessionResumed
 * - sessionPaused
 * - stackFrameChanged
 */
interface IFrameAccessorListener {
    /**
     * Called when the debug session is paused (e.g., when a breakpoint is hit, after a step operation,
     * or when the program suspends).
     */
    fun sessionPaused() {
    }

    /**
     * Called when the debug session is resumed from a paused state (e.g., when the user clicks Resume button).
     */
    fun sessionResumed() {
    }

    /**
     * Called when the debug session is stopped completely (e.g., when the debugged process terminates or
     * the user stops debugging).
     */
    fun sessionStopped() {
    }

    /**
     * Called when the current stack frame in the debugger is changed.
     * Is not called when user views a different stack frame from the call stack.
     * Is not called when another thread is selected (each thread has its own call stack).
     */
    fun stackFrameChanged() {
    }
}

private class UniqueIdGenerator {
    private val idsByObject = WeakHashMap<Any, String>()
    private val usedIds = ConcurrentHashMap.newKeySet<String>()
    private var collisionCounter = 0L

    @Synchronized
    fun getUniqueId(obj: Any): String {
        idsByObject[obj]?.let { return it }

        val baseId = System.identityHashCode(obj).toString()
        var uniqueId = baseId

        // Handle collisions by appending a counter
        while (!usedIds.add(uniqueId)) {
            uniqueId = "$baseId-${++collisionCounter}"
        }

        idsByObject[obj] = uniqueId
        return uniqueId
    }
}


/**
 * Maps a [PyFrameAccessor] to the corresponding [IPluginPyFrameAccessor].
 */
@Service(Service.Level.PROJECT)
class FrameAccessorManager(project: Project): Disposable {
    private val sharedExecutor = project.service<SharedExecutorService>()
    private val myIsDisposed = AtomicBoolean(false)

    private class MyMapping(val original: PyFrameAccessor, val wrapper: PluginPyFrameAccessor) {
        val listeners = CopyOnWriteArrayList<IFrameAccessorListener>()
        val listenToOriginal = AtomicBoolean(false)
    }

    private val myMappings = ConcurrentHashMap<String, MyMapping>()
    private val myUniqueIdGenerator = UniqueIdGenerator()

    override fun dispose() {
        if (myIsDisposed.compareAndSet(false, true)) {
            myMappings.clear()
        }
    }

    fun resolve(frameAccessor: PyFrameAccessor): IPluginPyFrameAccessor {
        val uniqueId = myUniqueIdGenerator.getUniqueId(frameAccessor)
        return myMappings.computeIfAbsent(uniqueId) {
            MyMapping(frameAccessor, PluginPyFrameAccessor(frameAccessor, uniqueId))
        }.wrapper
    }

    fun registerLifecycleListener(
        frameAccessor: IPluginPyFrameAccessor,
        listener: IFrameAccessorListener,
        parent: Disposable,
    ) {
        val mapping = getMapping(frameAccessor)
        mapping.listeners.add(listener)
        Disposer.register(parent) { mapping.listeners.remove(listener) }

        if (mapping.listenToOriginal.compareAndSet(false, true)) {
            mapping.wrapper.shared.errorCausedByStoppedSessionDetectedCallback.getAndSet {
                notifySessionStoppedAndCleanup(mapping.wrapper)
            }

            if (mapping.original is IPyDebugProcess) {
                mapping.original.session.addSessionListener(MyDebugSessionListener(mapping.wrapper), this)
            } else {
                // Most of the [PyFrameAccessor] implementations call `sessionStopped(null)`:
                // - PyDebugProcess (but it is a IPyDebugProcess)
                // - JupyterDebugProcess (but it is a IPyDebugProcess)
                //
                // PyFrameListener.TOPIC can't be used because we wouldn't know
                // which one was stopped. As a workaround we register a listener to each
                // [PyFrameAccessor] to be able to identify which one was stopped.
                mapping.original.addFrameListener(MyFrameListener(mapping.wrapper))
            }
        }
    }

    private fun getMapping(frameAccessor: IPluginPyFrameAccessor): MyMapping {
        val uniqueId = frameAccessor.getUniqueId()
        return myMappings[uniqueId] ?: throw IllegalStateException("No mapping exists for $frameAccessor")
    }

    private fun notifySessionStoppedAndCleanup(frameAccessor: PluginPyFrameAccessor) {
        frameAccessor.shared.setSessionStopped()
        myMappings.remove(frameAccessor.getUniqueId())?.listeners?.let { listenersToNotify ->
            listenersToNotify.forEach { listener ->
                listener.sessionStopped()
            }
        }
    }

    private abstract inner class MyBaseListener(protected val frameAccessor: PluginPyFrameAccessor) {
        init {
            updateSyntheticIdentifierSupportedFlag()
        }

        protected fun updateSyntheticIdentifierSupportedFlag() {
            sharedExecutor.execute {
                frameAccessor.updateSyntheticIdentifierSupportedFlag()
            }
        }
    }

    private inner class MyFrameListener(frameAccessor: PluginPyFrameAccessor)
        : MyBaseListener(frameAccessor), PyFrameListener {
        override fun frameChanged() {
            updateSyntheticIdentifierSupportedFlag()
            getMapping(frameAccessor).listeners.forEach { it.stackFrameChanged() }
        }

        override fun sessionStopped(communication: PyFrameAccessor?) {
            notifySessionStoppedAndCleanup(frameAccessor)
        }
    }

    private inner class MyDebugSessionListener(frameAccessor: PluginPyFrameAccessor)
        :MyBaseListener(frameAccessor), XDebugSessionListener {
        override fun sessionPaused() {
            getMapping(frameAccessor).listeners.forEach { it.sessionPaused() }
        }

        override fun beforeSessionResume() {
            getMapping(frameAccessor).listeners.forEach { it.sessionResumed() }
        }

        override fun sessionStopped() {
            notifySessionStoppedAndCleanup(frameAccessor)
        }

        override fun stackFrameChanged() {
            updateSyntheticIdentifierSupportedFlag()
            getMapping(frameAccessor).listeners.forEach { it.stackFrameChanged() }
        }
    }
}
