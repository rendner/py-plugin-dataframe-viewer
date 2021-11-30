package cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.evaluator

import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.ChunkSize
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.ChunkCoordinates
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.IChunkEvaluator
import cms.rendner.intellij.dataframe.viewer.pycharm.injector.PyPatchedStylerRef

/**
 * @param patchedStyler the styler from which the chunk is fetched.
 * @param chunkSize the size of the chunk. It is safe to provide a size with more rows/columns
 * as the DataFrame on which the [patchedStyler] operates.
 */
class ChunkEvaluator(
    private val patchedStyler: PyPatchedStylerRef,
    override val chunkSize: ChunkSize
) : IChunkEvaluator {

    override fun evaluate(chunkCoordinates: ChunkCoordinates, excludeRowHeaders: Boolean, excludeColumnHeaders: Boolean): String {
        return patchedStyler.evaluateRenderChunk(
            chunkCoordinates.indexOfFirstRow,
            chunkCoordinates.indexOfFirstColumn,
            chunkCoordinates.indexOfFirstRow + chunkSize.rows,
            chunkCoordinates.indexOfFirstColumn + chunkSize.columns,
            excludeRowHeaders,
            excludeColumnHeaders
        )
    }
}