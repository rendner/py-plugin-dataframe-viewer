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
import cms.rendner.intellij.dataframe.viewer.models.chunked.converter.css.CSSValueConverter
import cms.rendner.intellij.dataframe.viewer.python.exporter.TestCasePath
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import org.assertj.core.api.Assertions.assertThat
import java.awt.Color
import java.nio.file.Files
import javax.swing.table.AbstractTableModel

// browser returns "rgba(0, 0, 0, 0)" as background color - the "CSSValueConverter" returns null for invisible colors
private val DEFAULT_COMPUTED_BACKGROUND_COLOR: Color? = null
private val DEFAULT_COMPUTED_TD_TEXT_COLOR: Color = Color.BLACK
private val DEFAULT_COMPUTED_TD_TEXT_ALIGN: TextAlign = TextAlign.left

/**
 * Compares the calculated CSS values with CSS values calculated by a browser.
 *
 * These tests ensure:
 * - computed CSS values match with the ones computed by a web browser
 */
internal class CSSValidationTest : BaseResourceValidationTest("css-validation") {

    override fun testCollectedTestCase(
        testCase: TestCase,
        tableStructure: TableStructure,
        chunkSize: ChunkSize,
    ) {
        val computedStylesModel = createExpectedStyledModel(testCase, tableStructure)
        val singleChunkModel = createExpectedModel(testCase, tableStructure)
        val chunkedModel = createChunkedModel(testCase, tableStructure, chunkSize)

        assertStyledValues(testCase, singleChunkModel, computedStylesModel, "single-chunk")
        assertStyledValues(testCase, chunkedModel, computedStylesModel, "chunked")
    }

    private fun assertStyledValues(
        testCase: TestCase,
        actual: IDataFrameModel,
        computedStylesModel: MyStyledModel,
        actualErrorImageSuffix: String,
    ) {
        try {
            assertStylesOfValueModel(actual.getValueDataModel(), computedStylesModel.getValueDataModel())
        } catch (e: Throwable) {
            writeErrorImage(computedStylesModel, testCase, "expected")
            writeErrorImage(actual, testCase, actualErrorImageSuffix)
            throw e
        }
    }

    private fun assertStylesOfValueModel(actual: ITableValueDataModel, expected: MyStyledValueDataModel) {
        for (r in 0 until actual.rowCount) {
            for (c in 0 until actual.columnCount) {
                val actualStyle = extractActualStyleProperties(actual.getValueAt(r, c))
                val expectedStyle = expected.getValueAt(r, c).styles
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

    private fun extractActualStyleProperties(value: Value): StyleProperties {
        /*
        We can't detect, for the (pre)computed css, if a color value was explicit set by css
        or if the current value is the default value of the browser used to generate the (pre)computed
        css. Therefore, in case of absence the default values of the browser are used.
         */
        return when (value) {
            is StringValue -> {
                StyleProperties(
                    DEFAULT_COMPUTED_TD_TEXT_COLOR,
                    DEFAULT_COMPUTED_BACKGROUND_COLOR,
                    DEFAULT_COMPUTED_TD_TEXT_ALIGN,
                )
            }
            is StyledValue -> {
                StyleProperties(
                    value.styles.textColor ?: DEFAULT_COMPUTED_TD_TEXT_COLOR,
                    value.styles.backgroundColor ?: DEFAULT_COMPUTED_BACKGROUND_COLOR,
                    value.styles.textAlign ?: DEFAULT_COMPUTED_TD_TEXT_ALIGN,
                )
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun createExpectedStyledModel(
        testCase: TestCase,
        tableStructure: TableStructure
    ): MyStyledModel {
        val cssTable: CSSTable = json.decodeFromString(
            Files.newBufferedReader(TestCasePath.resolveComputedCSSFile(testCase.dir)).use {
                it.readText()
            }
        )
        return MyStyledModel(cssTable, tableStructure)
    }

    private class MyStyledModel(table: CSSTable, tableStructure: TableStructure) : IDataFrameModel {
        private val myValueModel = MyStyledValueDataModel(table, tableStructure)
        private val myIndexModel = MyIndexDataModel(table, tableStructure)

        override fun getValueDataModel(): MyStyledValueDataModel = myValueModel
        override fun getIndexDataModel(): ITableIndexDataModel = myIndexModel
        override fun dispose() {}
    }

    private class MyStyledValueDataModel(
        private val table: CSSTable,
        private val tableStructure: TableStructure,
    ) : AbstractTableModel(), ITableValueDataModel {

        private val valueConverter = CSSValueConverter()

        override fun getValueAt(rowIndex: Int, columnIndex: Int): StyledValue {
            val values = table.body.children[rowIndex].children.filter { it.type == "td" }
            return toValue(values[columnIndex])
        }

        override fun getColumnHeaderAt(columnIndex: Int): IHeaderLabel {
            if (tableStructure.hideColumnHeader) {
                return HeaderLabel()
            }
            return HeaderLabel(table.head.children[0].children[columnIndex].text)
        }

        override fun isLeveled(): Boolean = false
        override fun shouldHideHeaders(): Boolean = false
        override fun getColumnName(column: Int): String = getColumnHeaderAt(column).text()
        override fun getLegendHeader(): IHeaderLabel = HeaderLabel()
        override fun getLegendHeaders(): LegendHeaders = LegendHeaders()
        override fun getRowCount(): Int = tableStructure.rowsCount
        override fun getColumnCount(): Int = tableStructure.columnsCount
        private fun toValue(element: CSSTableElement): StyledValue {
            return StyledValue(
                element.text,
                StyleProperties(
                    valueConverter.convertColorValue(element.styles.color),
                    valueConverter.convertColorValue(element.styles.backgroundColor),
                    valueConverter.convertTextAlign(element.styles.textAlign),
                )
            )
        }
    }

    private class MyIndexDataModel(
        private val table: CSSTable,
        private val tableStructure: TableStructure,
    ) : AbstractTableModel(), ITableIndexDataModel {
        override fun getRowCount() = tableStructure.rowsCount
        override fun getColumnCount() = if (tableStructure.hideRowHeader) 0 else 1
        override fun getValueAt(rowIndex: Int): IHeaderLabel {
            val headers = table.body.children[rowIndex].children.filter { it.type == "th" }
            return if (headers.isNotEmpty()) HeaderLabel(headers[0].text) else HeaderLabel()
        }

        override fun getColumnName(): String = ""
        override fun getColumnName(columnIndex: Int): String = ""
        override fun getColumnHeader(): IHeaderLabel = HeaderLabel()
        override fun isLeveled(): Boolean = false
        override fun shouldHideHeaders(): Boolean = false
        override fun getLegendHeader(): IHeaderLabel = HeaderLabel()
        override fun getLegendHeaders(): LegendHeaders = LegendHeaders()
    }

    @Serializable
    private data class CSSTable(
        val styles: CSSTableStyles,
        val head: CSSTableRowContainer,
        val body: CSSTableRowContainer,
    )

    @Serializable
    private data class CSSTableRowContainer(
        val styles: CSSTableStyles,
        val children: List<CSSTableRow>,
    )

    @Serializable
    private data class CSSTableRow(
        val styles: CSSTableStyles,
        val children: List<CSSTableElement>,
    )

    @Serializable
    private data class CSSTableElement(
        val styles: CSSTableStyles,
        val text: String,
        val type: String,
    )

    @Serializable
    private data class CSSTableStyles(
        val backgroundColor: String? = null,
        val color: String? = null,
        val textAlign: String? = null,
    )
}