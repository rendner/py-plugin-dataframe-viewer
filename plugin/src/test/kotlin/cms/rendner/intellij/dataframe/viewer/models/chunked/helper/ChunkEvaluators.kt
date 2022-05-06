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
package cms.rendner.intellij.dataframe.viewer.models.chunked.helper

import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkRegion
import cms.rendner.intellij.dataframe.viewer.models.chunked.IChunkEvaluator
import cms.rendner.intellij.dataframe.viewer.python.exporter.TestCasePath
import java.nio.file.Files
import java.nio.file.Path


fun createHTMLFileEvaluator(
    filePath: Path,
): IChunkEvaluator {
    return HTMLFileEvaluator(filePath)
}

fun createHTMLChunksEvaluator(
    testCaseDir: Path,
): IChunkEvaluator {
    return HTMLChunkFileEvaluator(testCaseDir)
}

private class HTMLChunkFileEvaluator(
    private val testCaseDir: Path,
) : IChunkEvaluator {

    override fun evaluate(
        chunkRegion: ChunkRegion,
        excludeRowHeaders: Boolean,
        excludeColumnHeaders: Boolean
    ): String {
        val file = chunkRegion.let {
            TestCasePath.resolveChunkResultFile(testCaseDir, it.firstRow, it.firstColumn)
        }
        return Files.newBufferedReader(file).use {
            it.readText()
        }
    }
}

private class HTMLFileEvaluator(
    private val filePath: Path,
) : IChunkEvaluator {

    override fun evaluate(
        chunkRegion: ChunkRegion,
        excludeRowHeaders: Boolean,
        excludeColumnHeaders: Boolean
    ): String {
        return Files.newBufferedReader(filePath).use {
            it.readText()
        }
    }
}