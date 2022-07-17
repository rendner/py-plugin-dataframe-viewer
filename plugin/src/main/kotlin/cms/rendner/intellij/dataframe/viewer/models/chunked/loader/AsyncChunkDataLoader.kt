/*
 * Copyright 2022 cms.rendner (Daniel Schmidt)
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
import cms.rendner.intellij.dataframe.viewer.models.chunked.validator.ChunkValidator
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import cms.rendner.intellij.dataframe.viewer.shutdownExecutorSilently
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import java.util.*
import java.util.concurrent.CompletionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Loader for loading chunks of a pandas DataFrame asynchronously.
 *
 * The implementation is not thread safe and should only be called from a single thread.
 * This behavior is on purpose because this class is used by a swing component.
 *
 * It is guaranteed that all the methods provided by the [IChunkDataResultHandler] are called
 * from the event dispatch thread (EDT).
 *
 * @param chunkEvaluator the evaluator to fetch the HTML data for a chunk of the pandas DataFrame
 * @param loadNewDataStructure flag to switch between old and new data structure.
 * The old one is an HTML string which has to be parsed to extract the element and style information.
 * The new one is an object which describes the required HTML properties - it is easier to process.
 * @param chunkValidator the validator to validate the generated HTML data for a chunk
 * @param errorHandler the error handler. All errors during the data fetching are forwarded to this handler.
 */
class AsyncChunkDataLoader(
    chunkEvaluator: IChunkEvaluator,
    loadNewDataStructure: Boolean,
    chunkValidator: ChunkValidator?,
    private val errorHandler: IChunkDataLoaderErrorHandler,
) : AbstractChunkDataLoader(
    chunkEvaluator,
    loadNewDataStructure,
    chunkValidator,
) {

    companion object {
        private val logger = Logger.getInstance(AsyncChunkDataLoader::class.java)
    }

    private var myIsAliveFlag = true
    private var myActiveRequest: LoadRequest? = null
    private val myPendingRequests: Deque<LoadRequest> = ArrayDeque()
    private val myExecutorService = Executors.newFixedThreadPool(2)
    private var myMaxWaitingRequests: Int = 4

    private var mySortCriteria: SortCriteria = SortCriteria()
    private var mySortCriteriaChanged: Boolean = false

    override fun isAlive() = myIsAliveFlag

    override fun setSortCriteria(sortCriteria: SortCriteria) {
        if (sortCriteria != mySortCriteria) {
            mySortCriteria = sortCriteria
            mySortCriteriaChanged = true
        }
    }

    /**
     * Adds a load request to an internal waiting queue.
     * The added requests are processed according to the LIFO principle (last-in-first-out).
     * Entries of the waiting queue are processed as soon as the next chunk can be fetched.
     *
     * Since the internal waiting queue has a limited capacity, the oldest waiting request
     * is rejected as this limit exceeds to free space for the new one.
     *
     * Adding a load request with the same [ChunkRegion] as a previous added one:
     * - before the previous one is processed, will replace the old one
     * - which is currently processed, will not add the new one
     * - which was already processed in the past, will add the new one
     *
     */
    override fun loadChunk(request: LoadRequest) {
        if (!myIsAliveFlag) return
        if (myResultHandler == null) return
        when {
            !mySortCriteriaChanged && request.chunkRegion == myActiveRequest?.chunkRegion -> return
            myPendingRequests.isEmpty() -> myPendingRequests.add(request)
            myPendingRequests.firstOrNull() == request -> return
            else -> {
                myPendingRequests.removeIf { r -> r.chunkRegion == request.chunkRegion }
                myPendingRequests.addFirst(request)
            }
        }
        if (myPendingRequests.size > myMaxWaitingRequests) {
            myResultHandler?.onRequestRejected(myPendingRequests.pollLast(), IChunkDataResultHandler.RejectReason.TOO_MANY_PENDING_REQUESTS)
        }

        fetchNextChunk()
    }

    override fun dispose() {
        shutdownExecutorSilently(myExecutorService, 0, TimeUnit.SECONDS)
        myIsAliveFlag = false
        myResultHandler = null
        myPendingRequests.clear()
        myActiveRequest = null
    }

    override fun handleChunkData(ctx: LoadChunkContext, chunkData: ChunkData) {
        ApplicationManager.getApplication().invokeLater {
            if (mySortCriteriaChanged && ctx.sortCriteria == mySortCriteria) {
                mySortCriteriaChanged = false
            }
            myResultHandler?.let { resultHandler ->
                if (ctx.sortCriteria == null && !mySortCriteriaChanged || ctx.sortCriteria == mySortCriteria) {
                    resultHandler.onChunkLoaded(ctx.request, chunkData)
                } else {
                    resultHandler.onRequestRejected(
                        ctx.request,
                        IChunkDataResultHandler.RejectReason.SORT_CRITERIA_CHANGED
                    )
                }
            }
        }
    }

    override fun handleStyledValues(ctx: LoadChunkContext, chunkValues: ChunkValues) {
        ApplicationManager.getApplication().invokeLater {
            myResultHandler?.let { resultHandler ->
                if (ctx.sortCriteria == null && !mySortCriteriaChanged || ctx.sortCriteria == mySortCriteria) {
                    resultHandler.onStyledValuesProcessed(ctx.request, chunkValues)
                }
                // "handleStyledValues" will be removed in a feature release - therefore it is OK to not call the
                // "request rejected" method if sort criteria was changed - the requester clears all cached values
                // anyway on sort criteria change
            }
        }
    }

    private fun loadRequestDone() {
        ApplicationManager.getApplication().invokeLater {
            myActiveRequest = null
            if (myIsAliveFlag) {
                fetchNextChunk()
            }
        }
    }

    private fun fetchNextChunk() {
        if (myActiveRequest == null && myPendingRequests.isNotEmpty()) {
            myPendingRequests.pollFirst().let {
                myActiveRequest = it

                val ctx = LoadChunkContext(it, if (mySortCriteriaChanged) mySortCriteria else null)

                submitFetchChunkTask(ctx, myExecutorService).whenComplete { _, throwable ->
                    if (throwable != null) {
                        val unwrapped = if (throwable is CompletionException) throwable.cause!! else throwable
                        handleFetchTaskError(it, unwrapped)
                    }
                    loadRequestDone()
                }
            }
        }
    }

    private fun handleFetchTaskError(loadRequest: LoadRequest, throwable: Throwable) {
        errorHandler.handleChunkDataError(loadRequest.chunkRegion, throwable)
        ApplicationManager.getApplication().invokeLater {
            if (myIsAliveFlag) {
                if (throwable is EvaluateException && throwable.cause?.isDisconnectException() == true) {
                    myIsAliveFlag = false
                    logger.info("Debugger disconnected, setting 'isAlive' to false.")
                }
                myResultHandler?.onError(loadRequest, throwable)
            }
        }
    }
}