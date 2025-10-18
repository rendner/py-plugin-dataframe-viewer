/*
 * Copyright 2021-2025 cms.rendner (Daniel Schmidt)
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
package cms.rendner.intellij.dataframe.viewer.components.table.models.chunked

import cms.rendner.intellij.dataframe.viewer.components.table.fixtures.AsyncDataFrameModelFixture
import cms.rendner.intellij.dataframe.viewer.components.table.fixtures.DataFrameTableFixture
import cms.rendner.intellij.dataframe.viewer.python.bridge.ChunkDataRequest
import cms.rendner.intellij.dataframe.viewer.python.bridge.ChunkRegion
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class AsyncDataFrameModelTest {

    private fun setupFixture(sortable: Boolean = true): AsyncDataFrameModelFixture {
        // The table component is required for the test.
        // The model monitors the visible viewport of the table component
        // to decide which chunks should be loaded.
        return DataFrameTableFixture(sortable)
    }

    private fun sleep(milliseconds: Long = 42L) {
        Thread.sleep(milliseconds)
    }

    @Test
    fun doesFetchChunkIfValueIsRequested() {
        val fixture = setupFixture()

        fixture.dataFrameModel.getValuesDataModel().getValueAt(0, 0)
        sleep()

        val regionSlot = slot<ChunkRegion>()
        verify { fixture.mockTableSource.computeChunkData(capture(regionSlot), any()) }

        assertThat(regionSlot.isCaptured).isTrue()
    }

    @Test
    fun doesNotRefetchChunkIfValueIsRequestedTwice() {
        val fixture = setupFixture()

        fixture.dataFrameModel.getValuesDataModel().getValueAt(0, 0)
        sleep()
        fixture.dataFrameModel.getValuesDataModel().getValueAt(0, 0)
        sleep()

        verify(exactly = 1) { fixture.mockTableSource.computeChunkData(any(), any()) }
    }

    @Test
    fun doesNotRefetchChunkIfValueFromSameChunkIsRequested() {
        val fixture = setupFixture()

        fixture.dataFrameModel.getValuesDataModel().getValueAt(0, 0)
        sleep()
        fixture.dataFrameModel.getValuesDataModel().getValueAt(CHUNK_SIZE.rows - 1, CHUNK_SIZE.columns - 1)
        sleep()

        verify(exactly = 1) { fixture.mockTableSource.computeChunkData(any(), any()) }
    }

    @Test
    fun doesFetchRowHeadersIfNotHidden() {
        val fixture = setupFixture()

        fixture.dataFrameModel.getValuesDataModel().getValueAt(0, 0)
        sleep()

        val requestSlot = slot<ChunkDataRequest>()
        verify (exactly = 1) { fixture.mockTableSource.computeChunkData(any(), capture(requestSlot)) }

        assertThat(requestSlot.captured.withRowHeaders).isTrue()
    }

    @Test
    fun doesOnlyLoadHeadersWhenHeadersAreNotAlreadyLoaded() {
        val fixture = setupFixture()

        // fetch all values
        val valueModel = fixture.dataFrameModel.getValuesDataModel()
        for (r in 0 until fixture.tableStructure.rowsCount) {
            for (c in 0 until fixture.tableStructure.columnsCount) {
                valueModel.getValueAt(r, c)
            }
        }
        sleep()

        val capturedRegions = mutableListOf<ChunkRegion>()
        val capturedRequests = mutableListOf<ChunkDataRequest>()
        verify { fixture.mockTableSource.computeChunkData(capture(capturedRegions), capture(capturedRequests)) }

        assertThat(capturedRequests).isNotEmpty
        capturedRequests.forEachIndexed { index, request ->
            val region = capturedRegions[index]
            if (region.firstColumn == 0) {
                assertThat(request.withRowHeaders)
                    .withFailMessage("Expected withRowHeaders=true when firstColumn=0 for region $region")
                    .isTrue()
            } else {
                assertThat(request.withRowHeaders)
                    .withFailMessage("Expected withRowHeaders=false when firstColumn!=0 for region $region")
                    .isFalse()
            }
        }
    }

    @Test
    fun doesIncludeHeadersIfNoOtherChunkHasLoadedHeadersBefore() {
        val fixture = setupFixture()

        // load only the last chunk
        fixture.dataFrameModel.getValuesDataModel().getValueAt(
            fixture.tableStructure.rowsCount - 1,
            fixture.tableStructure.columnsCount - 1
        )
        sleep()

        val capturedRequest = slot<ChunkDataRequest>()
        verify (exactly = 1) { fixture.mockTableSource.computeChunkData(any(), capture(capturedRequest)) }

        assertThat(capturedRequest.captured.withRowHeaders).isTrue()
    }
}