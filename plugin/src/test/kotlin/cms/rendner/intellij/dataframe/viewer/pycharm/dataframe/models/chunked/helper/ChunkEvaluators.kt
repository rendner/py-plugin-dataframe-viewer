package cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.helper

import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.ChunkSize
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.ChunkCoordinates
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.IChunkEvaluator
import cms.rendner.intellij.dataframe.viewer.pycharm.exporter.TestCasePath
import java.nio.file.Files
import java.nio.file.Path


fun createHTMLFileEvaluator(
    filePath: Path,
    chunkSize: ChunkSize
): IChunkEvaluator {
    return HTMLFileEvaluator(filePath, chunkSize)
}

fun createHTMLChunksEvaluator(
    testCaseDir: Path,
    chunkSize: ChunkSize
): IChunkEvaluator {
    return HTMLChunkFileEvaluator(testCaseDir, chunkSize)
}

private class HTMLChunkFileEvaluator(
    private val testCaseDir: Path,
    override val chunkSize: ChunkSize
) : IChunkEvaluator {

    override fun evaluate(
        chunkCoordinates: ChunkCoordinates,
        excludeRowHeaders: Boolean,
        excludeColumnHeaders: Boolean
    ): String {
        val file = chunkCoordinates.let {
            TestCasePath.resolveChunkResultFile(testCaseDir, it.indexOfFirstRow, it.indexOfFirstColumn)
        }
        return Files.newBufferedReader(file).use {
            it.readText()
        }
    }
}

private class HTMLFileEvaluator(
    private val filePath: Path,
    override val chunkSize: ChunkSize
) : IChunkEvaluator {

    override fun evaluate(
        chunkCoordinates: ChunkCoordinates,
        excludeRowHeaders: Boolean,
        excludeColumnHeaders: Boolean
    ): String {
        return Files.newBufferedReader(filePath).use {
            it.readText()
        }
    }
}