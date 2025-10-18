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

import cms.rendner.intellij.dataframe.viewer.notifications.ErrorNotification
import cms.rendner.intellij.dataframe.viewer.shutdownExecutorSilently
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class SharedExecutorService(private var project: Project): Executor, Disposable {

    private var myExecutor: ExecutorService? = null
    private val taskQueue = LinkedBlockingQueue<Runnable>()
    private val isDisposed = AtomicBoolean(false)

    @Synchronized
    override fun dispose() {
        if (isDisposed.compareAndSet(false, true)) {
            taskQueue.clear()
            myExecutor?.let {
                myExecutor = null
                shutdownExecutorSilently(it, 0, TimeUnit.SECONDS)
            }
        }
    }

    @Synchronized
    override fun execute(task: Runnable) {
        if (isDisposed.get()) return

        /*
         * Race condition without @Synchronized on execute():
         *
         * ┌─────────────────────────────────────┬──────────────────────────────────────────┐
         * │ Thread A (execute)                  │ Thread B (processTasks)                  │
         * ├─────────────────────────────────────┼──────────────────────────────────────────┤
         * │ execute(task1)                      │                                          │
         * │   if (isDisposed.get()) return      │                                          │
         * │   taskQueue.offer(task1)            │                                          │
         * │                                     │ poll() → gets task1                      │
         * │                                     │ task1.run()                              │
         * │                                     │ poll(20s) → null (timeout)               │
         * │                                     │ taskQueue.isEmpty() → true               │
         * │                                     │ terminateExecutorIfQueueIsEmpty()        │
         * │                                     │   synchronized {                         │
         * │                                     │     shutdown executor                    │
         * │                                     │     myExecutor = null                    │
         * │                                     │   }                                      │
         * │   ensureExecutorRunning()           │                                          │
         * │     synchronized {                  │                                          │
         * │       if (myExecutor == null) ✓     │                                          │
         * │       create new executor           │                                          │
         * │     }                               │                                          │
         * └─────────────────────────────────────┴──────────────────────────────────────────┘
         *
         * Problem: Task was already consumed before executor was ensured to be running.
         * The new executor is created but has no tasks to process.
         */
        taskQueue.offer(task)
        ensureExecutorRunning()
    }

    private fun processTasks() {
        while (!isDisposed.get()) {
            try {
                val task = taskQueue.poll(20, TimeUnit.SECONDS)
                if (task != null) {
                    task.run()
                } else {
                    terminateExecutorIfQueueIsEmpty()
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (e: Exception) {
                runInEdt {
                    ErrorNotification(
                        "[SharedExecutor] Error while processing task",
                        e.message ?: "Unknown error",
                        e,
                    ).notify(project)
                }
            }
        }
    }

    @Synchronized
    private fun ensureExecutorRunning() {
        if (isDisposed.get()) return

        if (myExecutor == null || myExecutor?.isShutdown == true) {
            myExecutor = Executors.newSingleThreadExecutor().apply {
                submit(::processTasks)
            }
        }
    }

    @Synchronized
    private fun terminateExecutorIfQueueIsEmpty() {
        if (!taskQueue.isEmpty()) return
        myExecutor?.let {
            myExecutor = null
            shutdownExecutorSilently(it, 0, TimeUnit.SECONDS)
        }
    }
}
