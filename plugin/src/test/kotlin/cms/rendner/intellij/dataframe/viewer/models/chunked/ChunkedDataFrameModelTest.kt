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
package cms.rendner.intellij.dataframe.viewer.models.chunked

import cms.rendner.intellij.dataframe.viewer.models.HeaderLabel
import cms.rendner.intellij.dataframe.viewer.models.IHeaderLabel
import cms.rendner.intellij.dataframe.viewer.models.LegendHeaders
import cms.rendner.intellij.dataframe.viewer.models.StringValue
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.IChunkDataLoader
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.IChunkDataResultHandler
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.LoadRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test

internal class ChunkedDataFrameModelTest {

    private val chunkSize = ChunkSize(2, 2)

    private lateinit var model: ChunkedDataFrameModel
    private lateinit var dataProvider: ChunkDataProvider


    private fun setup(tableStructure: TableStructure, loaderDisposeCallback: (() -> Unit)? = null) {
        dataProvider = ChunkDataProvider(chunkSize, ChunkDataAnswerBuilder())
        model = ChunkedDataFrameModel(tableStructure, FakeChunkLoader(dataProvider, loaderDisposeCallback))
    }

    @Test
    fun doesCallDisposeOnLoader() {
        var loaderDisposed = false
        setup(createTableStructure()) { loaderDisposed = true }

        model.dispose()

        assertThat(loaderDisposed).isEqualTo(true)
    }

    @Test
    fun doesNotFetchChunkIfValueForIndexIsRequested() {
        setup(createTableStructure())

        model.getIndexDataModel().getValueAt(0)

        // only "model.getValueDataModel().getValueAt(r, c)" fetches data
        assertThat(dataProvider.recordedRequests.size).isEqualTo(0)
    }

    @Test
    fun doesFetchChunkIfValueIsRequested() {
        setup(createTableStructure())

        model.getValueDataModel().getValueAt(0, 0)

        assertThat(dataProvider.recordedRequests.size).isEqualTo(1)
    }

    @Test
    fun doesNotRefetchChunkIfValueIsRequestedTwice() {
        setup(createTableStructure())

        model.getValueDataModel().getValueAt(0, 0)
        model.getValueDataModel().getValueAt(0, 0)

        assertThat(dataProvider.recordedRequests.size).isEqualTo(1)
    }

    @Test
    fun doesNotRefetchChunkIfValueFromSameChunkIsRequested() {
        setup(createTableStructure())

        model.getValueDataModel().getValueAt(0, 0)
        model.getValueDataModel().getValueAt(chunkSize.rows - 1, chunkSize.columns - 1)

        assertThat(dataProvider.recordedRequests.size).isEqualTo(1)
    }

    @Test
    fun doesFetchHeadersIfNotHidden() {
        setup(createTableStructure())

        model.getValueDataModel().getValueAt(0, 0)

        val loadRequest = dataProvider.recordedRequests.first()
        assertThat(loadRequest.excludeRowHeaders).isFalse
        assertThat(loadRequest.excludeColumnHeaders).isFalse
    }

    @Test
    fun doesNotFetchRowHeadersIfRowHeaderIsHidden() {
        setup(createTableStructure(hideRowHeader = true))

        model.getValueDataModel().getValueAt(0, 0)

        val loadRequest = dataProvider.recordedRequests.first()
        assertThat(loadRequest.excludeRowHeaders).isTrue
        assertThat(loadRequest.excludeColumnHeaders).isFalse
    }

    @Test
    fun doesNotFetchColumnHeadersIfColumnHeaderIsHidden() {
        setup(createTableStructure(hideColumnHeader = true))

        model.getValueDataModel().getValueAt(0, 0)

        val loadRequest = dataProvider.recordedRequests.first()
        assertThat(loadRequest.excludeRowHeaders).isFalse
        assertThat(loadRequest.excludeColumnHeaders).isTrue
    }

    @Test
    fun doesNotFetchHeadersIfAllHeadersAreHidden() {
        setup(createTableStructure(hideRowHeader = true, hideColumnHeader = true))

        model.getValueDataModel().getValueAt(0, 0)

        val loadRequest = dataProvider.recordedRequests.first()
        assertThat(loadRequest.excludeRowHeaders).isTrue
        assertThat(loadRequest.excludeColumnHeaders).isTrue
    }

    @Test
    fun doesThrowIndexOutOfBoundsExceptionForInvalidIndices() {
        setup(
            createTableStructure(
                hideRowHeader = false,
                hideColumnHeader = false,
                visibleRowCount = 0,
                visibleColumnCount = 0,
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
        val tableStructure = createTableStructure()
        setup(tableStructure)

        // fetch all values
        val valueModel = model.getValueDataModel()
        for (r in 0 until tableStructure.visibleRowsCount) {
            for (c in 0 until tableStructure.visibleColumnsCount) {
                valueModel.getValueAt(r, c)
            }
        }

        dataProvider.recordedRequests.let { requests ->
            assertThat(requests).isNotEmpty

            requests.forEach {
                if (it.chunkCoordinates.indexOfFirstRow > 0) {
                    assertThat(it.excludeColumnHeaders).isTrue
                } else {
                    assertThat(it.excludeColumnHeaders).isFalse
                }

                if (it.chunkCoordinates.indexOfFirstColumn > 0) {
                    assertThat(it.excludeRowHeaders).isTrue
                } else {
                    assertThat(it.excludeRowHeaders).isFalse
                }
            }
        }
    }

    @Test
    fun doesIncludeHeadersIfNoOtherChunkHasLoadedHeadersBefore() {
        val tableStructure = createTableStructure()
        setup(tableStructure)

        // load only the last chunk
        model.getValueDataModel().getValueAt(
            tableStructure.visibleRowsCount - 1,
            tableStructure.visibleColumnsCount - 1
        )

        assertThat(dataProvider.recordedRequests).hasSize(1)
        dataProvider.recordedRequests.first().let {
            assertThat(it.excludeRowHeaders).isFalse
            assertThat(it.excludeColumnHeaders).isFalse
        }
    }

    @Test
    fun doesExcludeHeadersIfConfigured() {
        val tableStructure = createTableStructure(hideRowHeader = true, hideColumnHeader = true)
        setup(tableStructure)

        // fetch all values
        val valueModel = model.getValueDataModel()
        for (r in 0 until tableStructure.visibleRowsCount) {
            for (c in 0 until tableStructure.visibleColumnsCount) {
                valueModel.getValueAt(r, c)
            }
        }

        assertThat(dataProvider.recordedRequests).isNotEmpty
        dataProvider.recordedRequests.forEach {
            assertThat(it.excludeRowHeaders).isTrue
            assertThat(it.excludeColumnHeaders).isTrue
        }
    }

    private fun createTableStructure(
        hideRowHeader: Boolean = false,
        hideColumnHeader: Boolean = false,
        visibleRowCount: Int = chunkSize.rows * 2,
        visibleColumnCount: Int = chunkSize.columns * 2,
    ): TableStructure {
        return TableStructure(
            visibleRowCount * 2,
            visibleColumnCount * 2,
            visibleRowCount,
            visibleColumnCount,
            1,
            1,
            hideRowHeader,
            hideColumnHeader,
        )
    }

    private class FakeChunkLoader(
        val chunkDataProvider: ChunkDataProvider,
        val disposeCallback: (() -> Unit)?
    ) : IChunkDataLoader {
        private var resultHandler: IChunkDataResultHandler? = null

        override fun addToLoadingQueue(request: LoadRequest) {
            chunkDataProvider.getData(request)?.let {
                resultHandler?.onChunkLoaded(request, it)
            }
        }

        override fun setResultHandler(resultHandler: IChunkDataResultHandler) {
            this.resultHandler = resultHandler
        }

        override fun isAlive() = true
        override val chunkSize = chunkDataProvider.chunkSize
        override fun dispose() {
            disposeCallback?.let { it() }
        }
    }

    private class ChunkDataProvider(
        val chunkSize: ChunkSize,
        private val answerBuilder: ChunkDataAnswerBuilder? = null
    ) {
        val recordedRequests: MutableList<LoadRequest> = mutableListOf()
        fun getData(request: LoadRequest): ChunkData? {
            recordedRequests.add(request)
            return answerBuilder?.createAnswer(chunkSize, request)
        }
    }

    private class ChunkDataAnswerBuilder {

        fun createAnswer(chunkSize: ChunkSize, request: LoadRequest): ChunkData {
            return ChunkData(
                ChunkHeaderLabels(
                    LegendHeaders(),
                    createHeaderLabels(if (request.excludeColumnHeaders) 0 else chunkSize.columns),
                    createHeaderLabels(if (request.excludeRowHeaders) 0 else chunkSize.rows)
                ),
                createValues(chunkSize)
            )
        }

        private fun createValues(chunkSize: ChunkSize): ChunkValues {
            val value = StringValue("col")
            val row = ChunkValuesRow(List(chunkSize.columns) { value })
            return ChunkValues(List(chunkSize.rows) { row })
        }

        private fun createHeaderLabels(size: Int): List<IHeaderLabel> {
            return if (size == 0) {
                emptyList()
            } else {
                val header = HeaderLabel()
                return List(size) { header }
            }
        }
    }
}