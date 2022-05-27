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
package cms.rendner.intellij.dataframe.viewer.models.chunked.utils

import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkRegion
import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkSize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class IterateDataFrameTest {

    @Test
    fun negativeRowsInFrame_shouldThrowException() {
        assertFailsWith<IllegalArgumentException> {
            iterateDataFrame(-100, 100, ChunkSize(10, 10)).firstOrNull()
        }
    }

    @Test
    fun negativeColumnsInFrame_shouldThrowException() {
        assertFailsWith<IllegalArgumentException> {
            iterateDataFrame(100, -100, ChunkSize(10, 10)).firstOrNull()
        }
    }

    @Test
    fun negativeChunkSizeRows_shouldThrowException() {
        assertFailsWith<IllegalArgumentException> {
            iterateDataFrame(100, 100, ChunkSize(-10, 10)).firstOrNull()
        }
    }

    @Test
    fun negativeChunkSizeColumns_shouldThrowException() {
        assertFailsWith<IllegalArgumentException> {
            iterateDataFrame(100, 100, ChunkSize(10, -10)).firstOrNull()
        }
    }

    @Test
    fun zeroChunkSize_shouldThrowException() {
        assertFailsWith<IllegalArgumentException> {
            iterateDataFrame(100, 100, ChunkSize(0, 0)).firstOrNull()
        }
    }

    @Test
    fun emptyFrame_shouldYieldNoElements() {
        assertThat(
            iterateDataFrame(0, 0, ChunkSize(10, 10)).firstOrNull()
        ).isNull()
    }

    @Test
    fun nonEmptyFrame_shouldYieldRegionsWithCorrectChunkSize() {
        assertThat(
            iterateDataFrame(20, 20, ChunkSize(15, 15)).toList()
        ).isEqualTo(
            listOf(
                ChunkRegion(firstRow=0, firstColumn=0, numberOfRows=15, numberOfColumns=15),
                ChunkRegion(firstRow=0, firstColumn=15, numberOfRows=15, numberOfColumns=5),
                ChunkRegion(firstRow=15, firstColumn=0, numberOfRows=5, numberOfColumns=15),
                ChunkRegion(firstRow=15, firstColumn=15, numberOfRows=5, numberOfColumns=5)
            )
        )
    }
}