package cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.helper

import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.ChunkData
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.ChunkValues
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.IChunkEvaluator
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.converter.IChunkConverter
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.loader.AbstractChunkDataLoader
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.loader.LoadRequest
import org.jsoup.nodes.Document

internal class BlockingChunkDataLoader(
    chunkEvaluator: IChunkEvaluator,
    private val chunkConverterFactory: ((document: Document) -> IChunkConverter)? = null,
) : AbstractChunkDataLoader(
    chunkEvaluator
) {
    override fun addToLoadingQueue(request: LoadRequest) {
        createFetchChunkTask(request).run()
    }

    override fun handleChunkData(loadRequest: LoadRequest, chunkData: ChunkData) {
        myResultHandler?.onChunkLoaded(loadRequest, chunkData)
    }

    override fun handleStyledValues(loadRequest: LoadRequest, chunkValues: ChunkValues) {
        myResultHandler?.onStyledValues(loadRequest, chunkValues)
    }

    override fun handleError(loadRequest: LoadRequest, throwable: Throwable) {
        myResultHandler?.onError(loadRequest, throwable)
        // re-throw unexpected error
        throw throwable
    }

    override fun createChunkConverter(document: Document): IChunkConverter {
        return chunkConverterFactory?.let { it(document) } ?: super.createChunkConverter(document)
    }
}