package cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.loader

import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.ChunkSize
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.ChunkCoordinates
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.ChunkData
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.ChunkValues
import com.intellij.openapi.Disposable

interface IChunkDataResultHandler {
    fun onChunkLoaded(request: LoadRequest, chunkData: ChunkData)
    fun onStyledValues(request: LoadRequest, chunkValues: ChunkValues)
    fun onError(request: LoadRequest, throwable: Throwable)
}

interface IChunkDataLoader : Disposable {
    /**
     * Adds a load request to the data loader.
     * If the same chunk is requested multiple times, before the result is passed to the registered
     * [IChunkDataResultHandler], the chunk will be only loaded once.
     */
    fun addToLoadingQueue(request: LoadRequest)
    fun setResultHandler(resultHandler: IChunkDataResultHandler)
    fun isAlive(): Boolean
    val chunkSize: ChunkSize
}

data class LoadRequest(
    val chunkCoordinates: ChunkCoordinates,
    val excludeRowHeaders: Boolean,
    val excludeColumnHeaders: Boolean
)