/*
 * Copyright 2021 cms.rendner (Daniel Schmidt)
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
import cms.rendner.intellij.dataframe.viewer.models.chunked.helper.ComputedCSSChunkConverter
import cms.rendner.intellij.dataframe.viewer.models.chunked.helper.createHTMLChunksEvaluator
import cms.rendner.intellij.dataframe.viewer.models.chunked.helper.createHTMLFileEvaluator
import cms.rendner.intellij.dataframe.viewer.python.exporter.TestCasePath
import org.assertj.core.api.Assertions.assertThat
import java.awt.Color
import java.nio.file.Path
import java.util.*

private val DEFAULT_COMPUTED_BACKGROUND_COLOR: Color? = null
private val DEFAULT_COMPUTED_TEXT_COLOR: Color = Color.BLACK

/**
 * Compares the calculated CSS values with CSS values calculated by a browser.
 *
 * These tests ensure:
 * - computed CSS values match with the ones computed by a web browser
 */
internal class CSSValidationTest : BaseResourceValidationTest("css-validation") {

    override fun testCollectedTestCase(
        testCase: TestCase,
        properties: Properties,
        tableStructure: TableStructure,
        chunkSize: ChunkSize,
    ) {
        val preComputedCSSModel = createPreComputedCSSModel(testCase.dir, tableStructure)
        val singleChunkModel = createSingleChunkModel(testCase.dir, tableStructure)
        val chunkedModel = createChunkedModel(testCase.dir, tableStructure, chunkSize)

        assertModels(testCase, singleChunkModel, preComputedCSSModel, "single-chunk")
        assertModels(testCase, chunkedModel, preComputedCSSModel, "chunked")
    }

    private fun assertModels(
        testCase: TestCase,
        actual: IDataFrameModel,
        expected: IDataFrameModel,
        actualErrorImageSuffix: String,
    ) {
        try {
            assertStylesOfValueModel(
                actual.getValueDataModel(),
                expected.getValueDataModel()
            )
        } catch (e: Throwable) {
            writeErrorImage(expected, testCase, "expected")
            writeErrorImage(actual, testCase, actualErrorImageSuffix)
            throw e
        }
    }

    private fun assertStylesOfValueModel(actual: ITableValueDataModel, expected: ITableValueDataModel) {
        for (r in 0 until actual.rowCount) {
            for (c in 0 until actual.columnCount) {
                val actualStyle = extractStyleFromActualValue(actual.getValueAt(r, c))
                val expectedStyle = extractStyleFromComputedValue(expected.getValueAt(r, c))
                assertThat(actualStyle)
                    .withFailMessage(
                        "Styles at row:$r column:$c don't match.\n\texp: <%s>\n\tact: <%s>",
                        expectedStyle,
                        actualStyle,
                    )
                    .isEqualTo(expectedStyle)
            }
        }
    }

    private fun extractStyleFromActualValue(value: Value): ExtractedStyles {
        /*
        We can't detect, for the (pre)computed css, if a color value was explicit set by css
        or if the current value is the default value of the browser used to generate the (pre)computed
        css. Therefore, in case of absence the default values of the browser are used.
         */
        return when (value) {
            is StringValue -> {
                ExtractedStyles(
                    DEFAULT_COMPUTED_TEXT_COLOR,
                    DEFAULT_COMPUTED_BACKGROUND_COLOR,
                )
            }
            is StyledValue -> {
                ExtractedStyles(
                    useFallbackIfNull(value.styles.textColor, DEFAULT_COMPUTED_TEXT_COLOR),
                    useFallbackIfNull(value.styles.backgroundColor, DEFAULT_COMPUTED_BACKGROUND_COLOR),
                    value.styles.textAlign,
                )
            }
        }
    }

    private fun extractStyleFromComputedValue(value: Value): ExtractedStyles {
        return when (value) {
            is StringValue -> {
                ExtractedStyles(
                    DEFAULT_COMPUTED_TEXT_COLOR,
                    DEFAULT_COMPUTED_BACKGROUND_COLOR,
                )
            }
            is StyledValue -> {
                ExtractedStyles(
                    value.styles.textColor,
                    value.styles.backgroundColor,
                    value.styles.textAlign,
                )
            }
        }
    }

    private fun useFallbackIfNull(color: Color?, fallback: Color?): Color? {
        return if (color === null) fallback else color
    }

    private data class ExtractedStyles(
        val textColor: Color? = null,
        val backgroundColor: Color? = null,
        val textAlign: TextAlign? = null,
    )

    private fun createSingleChunkModel(testCaseDir: Path, tableStructure: TableStructure): IDataFrameModel {
        return createDataFrameModel(
            tableStructure,
            BlockingChunkDataLoader(
                createHTMLFileEvaluator(
                    TestCasePath.resolveExpectedResultFile(testCaseDir),
                    ChunkSize(tableStructure.rowsCount, tableStructure.columnsCount)
                )
            )
        )
    }

    private fun createPreComputedCSSModel(
        testCaseDir: Path,
        tableStructure: TableStructure,
    ): IDataFrameModel {
        return createDataFrameModel(
            tableStructure,
            BlockingChunkDataLoader(
                createHTMLFileEvaluator(
                    TestCasePath.resolveComputedCSSFile(testCaseDir),
                    ChunkSize(tableStructure.rowsCount, tableStructure.columnsCount)
                )
            ) { document -> ComputedCSSChunkConverter(document) }
        )
    }

    private fun createChunkedModel(
        testCaseDir: Path,
        tableStructure: TableStructure,
        chunkSize: ChunkSize
    ): IDataFrameModel {
        return createDataFrameModel(
            tableStructure,
            BlockingChunkDataLoader(
                createHTMLChunksEvaluator(testCaseDir, chunkSize)
            )
        )
    }
}