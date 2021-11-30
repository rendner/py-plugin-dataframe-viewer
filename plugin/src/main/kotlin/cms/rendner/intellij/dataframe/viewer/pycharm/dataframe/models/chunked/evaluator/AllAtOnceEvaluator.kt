package cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.evaluator

import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.ChunkSize
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.ChunkCoordinates
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.IChunkEvaluator
import cms.rendner.intellij.dataframe.viewer.pycharm.injector.PyPatchedStylerRef

class AllAtOnceEvaluator(
    private val patchedStyler: PyPatchedStylerRef,
    override val chunkSize: ChunkSize
) : IChunkEvaluator {

    fun evaluate(): String {
        return patchedStyler.evaluateRenderUnpatched()
    }
    override fun evaluate(chunkCoordinates: ChunkCoordinates, excludeRowHeaders: Boolean, excludeColumnHeaders: Boolean): String {
        return evaluate()
    }
}