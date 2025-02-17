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
package cms.rendner.intellij.dataframe.viewer.models.chunked.loader

import cms.rendner.intellij.dataframe.viewer.models.chunked.*
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.converter.ChunkDataConverter
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import cms.rendner.intellij.dataframe.viewer.shutdownExecutorSilently
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.Logger
import java.util.*
import java.util.concurrent.*

/**
 * Loader for loading chunks of a Python DataFrame asynchronously.
 *
 * The implementation is not thread safe and should only be called from a single thread.
 * This behavior is on purpose because this class is used by a swing component.
 *
 * It is guaranteed that for all registered [IModelDataLoader.IResultHandler] the methods are called
 * from the event dispatch thread (EDT).
 *
 * @param chunkEvaluator the evaluator to fetch the HTML data for a chunk of the Python DataFrame
 */
class AsyncModelDataLoader(
    private val chunkEvaluator: IChunkEvaluator,
): AbstractModelDataLoader(), IModelDataLoader {

    companion object {
        private val logger = Logger.getInstance(AsyncModelDataLoader::class.java)
    }

    private var myIsAliveFlag = true
    private var myPendingChunk: ChunkRegion? = null
    private val myRequestedChunks: Deque<ChunkRegion> = ArrayDeque()
    private val myExecutorService = Executors.newSingleThreadExecutor()
    private var myMaxWaitingRequests: Int = 4

    private var mySorting: SortCriteria = SortCriteria()
    private var mySortingIsDirty: Boolean = false

    override fun isAlive() = myIsAliveFlag

    override fun dispose() {
        super.dispose()
        myIsAliveFlag = false
        myRequestedChunks.clear()
        myPendingChunk = null
        shutdownExecutorSilently(myExecutorService, 0, TimeUnit.SECONDS)
    }

    override fun setSorting(sorting: SortCriteria) {
        if (sorting != mySorting) {
            mySorting = sorting
            mySortingIsDirty = true
        }
    }

    override fun loadColumnStatistics(columnIndex: Int) {
        // load immediately, without waiting queue
        myExecutorService.execute(createFetchColumnStatisticsTask(columnIndex))
    }

    /**
     * Adds a load request to an internal waiting queue.
     * Load requests are ignored if this loader is already disposed.
     *
     * Added requests are processed according to the LIFO principle (last-in-first-out).
     * Entries of the waiting queue are processed as soon as the next chunk can be fetched.
     *
     * Since the internal waiting queue has a limited capacity, the oldest waiting request
     * is rejected as this limit exceeds to free space for the new one.
     */
    override fun loadChunk(chunk: ChunkRegion) {
        if (!myIsAliveFlag) return
        when {
            !mySortingIsDirty && chunk == myPendingChunk -> return
            myRequestedChunks.isEmpty() -> myRequestedChunks.add(chunk)
            myRequestedChunks.firstOrNull() == chunk -> return
            else -> {
                myRequestedChunks.remove(chunk)
                myRequestedChunks.addFirst(chunk)
            }
        }
        if (myRequestedChunks.size > myMaxWaitingRequests) {
            notifyChunkDataRejected(
                myRequestedChunks.pollLast(),
                IModelDataLoader.IResultHandler.RejectReason.TOO_MANY_PENDING_REQUESTS,
            )
        }

        fetchNextChunk()
    }

    private fun fetchNextChunk() {
        if (myPendingChunk == null) {
            val chunk = myRequestedChunks.pollFirst() ?: return
            myExecutorService.execute(createFetchChunkTask(chunk, if (mySortingIsDirty) mySorting else null))
        }
    }

    private fun updateAliveFlag(throwable: Throwable) {
        if (myIsAliveFlag) {
            if (throwable is EvaluateException && throwable.isCausedByDisconnectException()) {
                myIsAliveFlag = false
                logger.info("Debugger disconnected, setting 'isAlive' to false.")
            }
        }
    }

    private fun createFetchChunkTask(chunk: ChunkRegion, newSorting: SortCriteria?): Runnable {
        val request = createLoadRequestFor(chunk)
        myPendingChunk = chunk
        return Runnable {
            try {
                if (Thread.currentThread().isInterrupted) return@Runnable
                val bridgeChunkData = chunkEvaluator.evaluateChunkData(
                    request.chunkRegion,
                    request.withRowHeaders,
                    newSorting,
                )

                if (Thread.currentThread().isInterrupted) return@Runnable
                val chunkData = ChunkDataConverter.convert(
                    bridgeChunkData,
                    request.withRowHeaders,
                )

                if (Thread.currentThread().isInterrupted) return@Runnable
                runInEdt {
                    if (mySortingIsDirty && newSorting == mySorting) {
                        // new sortCriteria was applied and fetched data belongs to requested sort state
                        mySortingIsDirty = false
                    }

                    if (mySortingIsDirty) {
                        notifyChunkDataRejected(
                            request.chunkRegion,
                            IModelDataLoader.IResultHandler.RejectReason.SORT_CRITERIA_CHANGED,
                        )
                    } else {
                        notifyChunkDataSuccess(request.chunkRegion, chunkData)
                    }

                    // start next queued request
                    myPendingChunk = null
                    if (myIsAliveFlag) {
                        fetchNextChunk()
                    }
                }
            } catch (throwable: Throwable) {
                logger.info("Failed to fetch chunk data ${request.chunkRegion}", throwable)
                if (throwable is InterruptedException) Thread.currentThread().interrupt()

                runInEdt {
                    updateAliveFlag(throwable)
                    notifyChunkDataFailure(request.chunkRegion, throwable)
                }
            }
        }
    }

    private fun createFetchColumnStatisticsTask(columnIndex: Int): Runnable {
        return Runnable {
            try {
                if (Thread.currentThread().isInterrupted) return@Runnable
                val statistics = chunkEvaluator.evaluateColumnStatistics(columnIndex)

                if (Thread.currentThread().isInterrupted) return@Runnable
                runInEdt {
                    notifyColumnStatisticsSuccess(columnIndex, statistics)
                }
            } catch (throwable: Throwable) {
                logger.info("Failed to fetch column statistics for column at index $columnIndex", throwable)
                if (throwable is InterruptedException) Thread.currentThread().interrupt()

                runInEdt {
                    updateAliveFlag(throwable)
                    notifyColumnStatisticsFailure(columnIndex, throwable)
                }
            }
        }
    }
}