/*
 * Copyright 2023 cms.rendner (Daniel Schmidt)
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
package cms.rendner.intellij.dataframe.viewer.models.chunked

import cms.rendner.intellij.dataframe.viewer.models.chunked.helper.TableModelFactory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test

internal class ChunkedDataFrameModelTest {

    private val chunkSize = ChunkSize(2, 2)
    private val tableModelFactory = TableModelFactory(chunkSize)
    private lateinit var model: TableModelFactory.RecordingModel

    private fun setup(tableStructure: TableStructure, frameColumnOrgIndexList: List<Int>? = null) {
        model = tableModelFactory.createModel(tableStructure, frameColumnOrgIndexList).apply {
            enableDataFetching(true)
        }
    }

    @Test
    fun doesNotFetchIfDataFetchingIsDisabled() {
        setup(tableModelFactory.createTableStructure())

        model.getValueDataModel().enableDataFetching(false)
        model.getIndexDataModel().enableDataFetching(false)

        model.getValueDataModel().getValueAt(0, 0)
        model.getIndexDataModel().getValueAt(0)

        assertThat(model.recordedLoadRequests.size).isEqualTo(0)
    }

    @Test
    fun doesNotFetchChunkIfValueForIndexIsRequested() {
        setup(tableModelFactory.createTableStructure())

        model.getIndexDataModel().getValueAt(0)

        // only "model.getValueDataModel().getValueAt(r, c)" fetches data
        assertThat(model.recordedLoadRequests.size).isEqualTo(0)
    }

    @Test
    fun doesFetchChunkIfValueIsRequested() {
        setup(tableModelFactory.createTableStructure())

        model.getValueDataModel().getValueAt(0, 0)

        assertThat(model.recordedLoadRequests.size).isEqualTo(1)
    }

    @Test
    fun doesNotRefetchChunkIfValueIsRequestedTwice() {
        setup(tableModelFactory.createTableStructure())

        model.getValueDataModel().getValueAt(0, 0)
        model.getValueDataModel().getValueAt(0, 0)

        assertThat(model.recordedLoadRequests.size).isEqualTo(1)
    }

    @Test
    fun doesNotRefetchChunkIfValueFromSameChunkIsRequested() {
        setup(tableModelFactory.createTableStructure())

        model.getValueDataModel().getValueAt(0, 0)
        model.getValueDataModel().getValueAt(chunkSize.rows - 1, chunkSize.columns - 1)

        assertThat(model.recordedLoadRequests.size).isEqualTo(1)
    }

    @Test
    fun doesFetchHeadersIfNotHidden() {
        setup(tableModelFactory.createTableStructure())

        model.getValueDataModel().getValueAt(0, 0)

        val loadRequest = model.recordedLoadRequests.first()
        assertThat(loadRequest.excludeRowHeaders).isFalse
        assertThat(loadRequest.excludeColumnHeaders).isFalse
    }

    @Test
    fun doesThrowIndexOutOfBoundsExceptionForInvalidIndices() {
        setup(
            tableModelFactory.createTableStructure(
                rowCount = 0,
                columnCount = 0,
            )
        )

        assertThatExceptionOfType(IndexOutOfBoundsException::class.java).isThrownBy {
            model.getValueDataModel().getValueAt(1, 1)
        }
        assertThatExceptionOfType(IndexOutOfBoundsException::class.java).isThrownBy {
            model.getValueDataModel().getColumnHeaderAt(1)
        }

        assertThatExceptionOfType(IndexOutOfBoundsException::class.java).isThrownBy {
            model.getIndexDataModel().getValueAt(1)
        }
    }

    @Test
    fun doesOnlyLoadHeadersWhenHeadersAreNotAlreadyLoaded() {
        val tableStructure = tableModelFactory.createTableStructure()
        setup(tableStructure)

        // fetch all values
        val valueModel = model.getValueDataModel()
        for (r in 0 until tableStructure.rowsCount) {
            for (c in 0 until tableStructure.columnsCount) {
                valueModel.getValueAt(r, c)
            }
        }

        model.recordedLoadRequests.let { requests ->
            assertThat(requests).isNotEmpty

            requests.forEach {
                if (it.chunkRegion.firstRow > 0) {
                    assertThat(it.excludeColumnHeaders).isTrue
                } else {
                    assertThat(it.excludeColumnHeaders).isFalse
                }

                if (it.chunkRegion.firstColumn > 0) {
                    assertThat(it.excludeRowHeaders).isTrue
                } else {
                    assertThat(it.excludeRowHeaders).isFalse
                }
            }
        }
    }

    @Test
    fun doesIncludeHeadersIfNoOtherChunkHasLoadedHeadersBefore() {
        val tableStructure = tableModelFactory.createTableStructure()
        setup(tableStructure)

        // load only the last chunk
        model.getValueDataModel().getValueAt(
            tableStructure.rowsCount - 1,
            tableStructure.columnsCount - 1
        )

        assertThat(model.recordedLoadRequests).hasSize(1)
        model.recordedLoadRequests.first().let {
            assertThat(it.excludeRowHeaders).isFalse
            assertThat(it.excludeColumnHeaders).isFalse
        }
    }
}