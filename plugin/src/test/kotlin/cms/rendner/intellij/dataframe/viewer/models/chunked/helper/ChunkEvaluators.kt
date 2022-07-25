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
import cms.rendner.intellij.dataframe.viewer.models.chunked.SortCriteria
import cms.rendner.intellij.dataframe.viewer.python.bridge.HTMLPropsTable
import cms.rendner.intellij.dataframe.viewer.python.exporter.TestCasePath
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

private val json: Json by lazy {
    Json { ignoreUnknownKeys = true }
}

fun createExpectedFileEvaluator(
    filePath: Path,
): IChunkEvaluator {
    return ExpectedFileEvaluator(filePath)
}

fun createChunkFileEvaluator(
    testCaseDir: Path,
): IChunkEvaluator {
    return ChunkFileEvaluator(testCaseDir)
}

private class ChunkFileEvaluator(
    private val testCaseDir: Path,
) : IChunkEvaluator {
    override fun setSortCriteria(sortCriteria: SortCriteria) {
        NotImplementedError("Sorting isn't support by this implementation.")
    }

    override fun evaluate(
        chunkRegion: ChunkRegion,
        excludeRowHeaders: Boolean,
        excludeColumnHeaders: Boolean
    ): String {
        val file = chunkRegion.let {
            TestCasePath.resolveChunkResultFile(testCaseDir, it.firstRow, it.firstColumn, "html")
        }
        return Files.newBufferedReader(file).use {
            it.readText()
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun evaluateHTMLProps(
        chunkRegion: ChunkRegion,
        excludeRowHeaders: Boolean,
        excludeColumnHeaders: Boolean
    ): HTMLPropsTable {
        val file = chunkRegion.let {
            TestCasePath.resolveChunkResultFile(testCaseDir, it.firstRow, it.firstColumn, "json")
        }
        return Files.newBufferedReader(file).use {
            json.decodeFromString(it.readText())
        }
    }
}

private class ExpectedFileEvaluator(
    private val filePath: Path,
) : IChunkEvaluator {
    override fun setSortCriteria(sortCriteria: SortCriteria) {
        NotImplementedError("Sorting isn't support by this implementation.")
    }

    override fun evaluate(
        chunkRegion: ChunkRegion,
        excludeRowHeaders: Boolean,
        excludeColumnHeaders: Boolean
    ): String {
        return Files.newBufferedReader(filePath).use {
            it.readText()
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun evaluateHTMLProps(
        chunkRegion: ChunkRegion,
        excludeRowHeaders: Boolean,
        excludeColumnHeaders: Boolean
    ): HTMLPropsTable {
        return Files.newBufferedReader(filePath).use {
            json.decodeFromString(it.readText())
        }
    }
}