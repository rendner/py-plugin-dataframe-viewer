package cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.loader

import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.ChunkData
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.ChunkValues
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.IChunkEvaluator
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.converter.ChunkConverter
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.converter.IChunkConverter
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

abstract class AbstractChunkDataLoader(
    chunkEvaluator: IChunkEvaluator
) : IChunkDataLoader {

    protected var myResultHandler: IChunkDataResultHandler? = null
    private val myChunkEvaluator = chunkEvaluator

    override fun isAlive() = true

    override val chunkSize = chunkEvaluator.chunkSize

    override fun setResultHandler(resultHandler: IChunkDataResultHandler) {
        myResultHandler = resultHandler
    }

    override fun dispose() {
    }

    protected open fun createChunkConverter(document: Document): IChunkConverter {
        return ChunkConverter(document)
    }

    protected fun createFetchChunkTask(loadRequest: LoadRequest): Runnable {
        return Runnable {
            val currentThread = Thread.currentThread()
            try {
                val evaluatedChunk = myChunkEvaluator.evaluate(
                    loadRequest.chunkCoordinates,
                    loadRequest.excludeRowHeaders,
                    loadRequest.excludeColumnHeaders
                )
                if (currentThread.isInterrupted) return@Runnable

                val document = Jsoup.parse(evaluatedChunk)
                if (currentThread.isInterrupted) return@Runnable

                val converter = createChunkConverter(document)

                val chunkData = converter.convertText(loadRequest.excludeRowHeaders, loadRequest.excludeColumnHeaders)
                if (currentThread.isInterrupted) return@Runnable
                handleChunkData(loadRequest, chunkData)

                val styledValues = converter.mergeWithStyles(chunkData.values)
                if (currentThread.isInterrupted) return@Runnable
                handleStyledValues(loadRequest, styledValues)
            } catch (e: Throwable) {
                handleError(loadRequest, e)
            }
        }
    }

    protected abstract fun handleChunkData(loadRequest: LoadRequest, chunkData: ChunkData)
    protected abstract fun handleStyledValues(loadRequest: LoadRequest, chunkValues: ChunkValues)
    protected abstract fun handleError(loadRequest: LoadRequest, throwable: Throwable)
}