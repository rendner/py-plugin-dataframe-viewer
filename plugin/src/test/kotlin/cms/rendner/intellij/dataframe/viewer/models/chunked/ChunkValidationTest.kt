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

import cms.rendner.intellij.dataframe.viewer.models.*
import cms.rendner.intellij.dataframe.viewer.models.chunked.helper.BlockingChunkDataLoader
import cms.rendner.intellij.dataframe.viewer.models.chunked.helper.createHTMLChunksEvaluator
import cms.rendner.intellij.dataframe.viewer.models.chunked.helper.createHTMLFileEvaluator
import cms.rendner.intellij.dataframe.viewer.python.exporter.TestCasePath
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Path

/**
 * Compares the [IDataFrameModel] created from combined HTML chunks against the [IDataFrameModel] created from
 * an unchunked HTML.
 *
 * These tests ensure:
 * - combined chunks result in the same visual output as using the unchunked HTML
 *      - correctness of computed CSS values can't be verified by this test
 * - HTML parser can parse the generated html
 * - chunks are combined in the correct order
 */
internal class ChunkValidationTest : BaseResourceValidationTest("chunk-validation") {

    override fun testCollectedTestCase(
        testCase: TestCase,
        tableStructure: TableStructure,
        chunkSize: ChunkSize,
    ) {
        val expectedModel = createExpectedModel(testCase.dir, tableStructure)
        val chunkedModel = createChunkedModel(testCase.dir, tableStructure, chunkSize)

        try {
            assertHeadersFromValueDataModel(chunkedModel.getValueDataModel(), tableStructure)
            assertHeadersFromIndexDataModel(chunkedModel.getIndexDataModel(), tableStructure)
            assertTableModels(chunkedModel, expectedModel)
        } catch (e: Throwable) {
            writeErrorImage(expectedModel, testCase, "expected")
            writeErrorImage(chunkedModel, testCase, "chunked")
            throw e
        }
    }

    private fun assertTableModels(actual: IDataFrameModel, expected: IDataFrameModel) {
        assertTableModels(actual.getValueDataModel(), expected.getValueDataModel())
        assertTableModels(actual.getIndexDataModel(), expected.getIndexDataModel())
    }

    private fun assertHeadersFromValueDataModel(dataModel: ITableValueDataModel, tableStructure: TableStructure) {
        if (tableStructure.columnLevelsCount > 1) {
            for (c in 0 until dataModel.columnCount) {
                dataModel.getColumnHeaderAt(c).let {
                    assertThat(it)
                        .isInstanceOf(LeveledHeaderLabel::class.java)

                    assertThat((it as LeveledHeaderLabel).leadingLevels.size)
                        .withFailMessage("columnHeaderLabel is not leveled at: $c")
                        .isEqualTo(tableStructure.columnLevelsCount - 1)
                }
            }
        } else {
            for (c in 0 until dataModel.columnCount) {
                assertThat(dataModel.getColumnHeaderAt(c))
                    .withFailMessage("columnHeaderLabel is leveled at: $c")
                    .isNotInstanceOf(LeveledHeaderLabel::class.java)
            }
        }
    }

    private fun assertHeadersFromIndexDataModel(dataModel: ITableIndexDataModel, tableStructure: TableStructure) {
        if (tableStructure.hideRowHeader) {
            assertThat(dataModel.columnCount).isZero
        } else {
            assertThat(dataModel.columnCount).isOne
            if (tableStructure.rowLevelsCount > 1) {
                dataModel.getColumnHeader().let {
                    assertThat(it).isInstanceOf(LeveledHeaderLabel::class.java)
                    assertThat((it as LeveledHeaderLabel).leadingLevels.size)
                        .withFailMessage("columnHeaderLabel is not leveled")
                        .isEqualTo(tableStructure.rowLevelsCount - 1)
                }
                for (r in 0 until dataModel.rowCount) {
                    dataModel.getValueAt(r).let {
                        assertThat(it).isInstanceOf(LeveledHeaderLabel::class.java)
                        assertThat((it as LeveledHeaderLabel).leadingLevels.size)
                            .withFailMessage("rowHeaderLabel is not leveled at: $r")
                            .isEqualTo(tableStructure.rowLevelsCount - 1)
                    }
                }
            } else {
                assertThat(dataModel.getColumnHeader()).isNotInstanceOf(LeveledHeaderLabel::class.java)
                for (r in 0 until dataModel.rowCount) {
                    assertThat(dataModel.getValueAt(r))
                        .withFailMessage("rowHeaderLabel is leveled at: $r")
                        .isNotInstanceOf(LeveledHeaderLabel::class.java)
                }
            }
        }
    }

    private fun assertTableModels(actual: ITableDataModel, expected: ITableDataModel) {
        assertThat(actual.rowCount).isEqualTo(expected.rowCount)
        assertThat(actual.rowCount).isEqualTo(expected.rowCount)
        assertThat(actual.columnCount).isEqualTo(expected.columnCount)
        assertThat(actual.getLegendHeaders()).isEqualTo(expected.getLegendHeaders())
        assertThat(actual.isLeveled()).isEqualTo(expected.isLeveled())
        assertThat(actual.shouldHideHeaders()).isEqualTo(expected.shouldHideHeaders())
        assertThat(actual.getLegendHeader()).isEqualTo(expected.getLegendHeader())

        for (c in 0 until expected.columnCount) {
            assertThat(actual.getColumnName(c))
                .withFailMessage("columnNames at: $c don't match")
                .isEqualTo(expected.getColumnName(c))
        }

        for (r in 0 until expected.rowCount) {
            for (c in 0 until expected.columnCount) {
                val actualValue = actual.getValueAt(r, c)
                val expectedValue = expected.getValueAt(r, c)
                assertThat(actualValue)
                    .withFailMessage(
                        "CellValues at row:$r column:$c don't match.\n\texp: <%s>\n\tact: <%s>",
                        expectedValue,
                        actualValue,
                    )
                    .isEqualTo(expectedValue)
            }
        }
    }

    private fun createExpectedModel(testCaseDir: Path, tableStructure: TableStructure): IDataFrameModel {
        return createDataFrameModel(
            tableStructure,
            BlockingChunkDataLoader(
                createHTMLFileEvaluator(TestCasePath.resolveExpectedResultFile(testCaseDir))
            ),
            ChunkSize(tableStructure.rowsCount, tableStructure.columnsCount),
        )
    }

    private fun createChunkedModel(
        testCaseDir: Path,
        tableStructure: TableStructure,
        chunkSize: ChunkSize
    ): IDataFrameModel {
        return createDataFrameModel(
            tableStructure,
            BlockingChunkDataLoader(createHTMLChunksEvaluator(testCaseDir)),
            chunkSize,
        )
    }
}