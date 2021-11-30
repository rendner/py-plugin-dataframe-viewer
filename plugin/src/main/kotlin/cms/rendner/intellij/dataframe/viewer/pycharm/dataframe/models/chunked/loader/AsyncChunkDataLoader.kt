package cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.loader

import cms.rendner.intellij.dataframe.viewer.notifications.UserNotifier
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.ChunkData
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.ChunkValues
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.IChunkEvaluator
import cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.exceptions.EvaluateException
import cms.rendner.intellij.dataframe.viewer.pycharm.extensions.isDisconnectException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ConcurrencyUtil
import java.util.*

/**
 * Fetches data of a pandas DataFrame chunkwise.
 * If the DataFrame becomes unreachable (disconnected debugger) all pending requests are dropped
 * without notification.
 */
class AsyncChunkDataLoader(
    chunkEvaluator: IChunkEvaluator,
    waitingQueueSize: Int,
    private var notifier: UserNotifier? = null
) : AbstractChunkDataLoader(
    chunkEvaluator
) {

    companion object {
        private val logger = Logger.getInstance(AsyncChunkDataLoader::class.java)
    }

    private var isAliveFlag = true
    private var activeRequest: LoadRequest? = null
    private val myMaxRequestedChunks: Int = waitingQueueSize
    private val myRequestedChunks: Deque<LoadRequest> = ArrayDeque()

    // http://gafter.blogspot.com/2006/11/thread-pool-puzzler.html
    private val myExecutorService = ConcurrencyUtil.newSingleThreadExecutor(AsyncChunkDataLoader::class.java.simpleName)

    override fun isAlive() = isAliveFlag

    override fun addToLoadingQueue(request: LoadRequest) {
        if(!isAliveFlag) return
        when {
            request == activeRequest -> return
            myRequestedChunks.isEmpty() -> myRequestedChunks.add(request)
            myRequestedChunks.firstOrNull() == request -> return
            else -> {
                myRequestedChunks.removeIf { r -> r.chunkCoordinates == request.chunkCoordinates }
                myRequestedChunks.addFirst(request)
            }
        }
        if (myRequestedChunks.size >= myMaxRequestedChunks) {
            myRequestedChunks.pollLast()
        }

        fetchNextChunk()
    }

    override fun dispose() {
        super.dispose()
        try {
            myExecutorService.shutdownNow()
        } catch (ignore: SecurityException) {
        }
        myRequestedChunks.clear()
        activeRequest = null
        notifier = null
    }

    override fun handleChunkData(loadRequest: LoadRequest, chunkData: ChunkData) {
        ApplicationManager.getApplication().invokeLater {
            myResultHandler?.onChunkLoaded(loadRequest, chunkData)
        }
    }

    override fun handleStyledValues(loadRequest: LoadRequest, chunkValues: ChunkValues) {
        ApplicationManager.getApplication().invokeLater {
            activeRequest = null
            myResultHandler?.onStyledValues(loadRequest, chunkValues)
            fetchNextChunk()
        }
    }

    override fun handleError(loadRequest: LoadRequest, throwable:Throwable) {
        ApplicationManager.getApplication().invokeLater {
            activeRequest = null

            if(isAlive()) {
                logger.warn("Fetching data for chunk '${loadRequest.chunkCoordinates}' failed.", throwable)

                if (throwable is EvaluateException && throwable.cause?.isDisconnectException() == true) {
                    isAliveFlag = false
                    myRequestedChunks.clear()
                    logger.info("Debugger disconnected, setting 'isAlive' to false.")
                }

                notifier?.error("Loading chunk failed: ${throwable.localizedMessage}")
                myResultHandler?.onError(loadRequest, throwable)
                fetchNextChunk()
            }
        }
    }

    private fun fetchNextChunk() {
        if (activeRequest == null && myRequestedChunks.isNotEmpty()) {
            val loadRequest = myRequestedChunks.pollFirst()
            activeRequest = loadRequest
            myExecutorService.execute(createFetchChunkTask(loadRequest))
        }
    }
}