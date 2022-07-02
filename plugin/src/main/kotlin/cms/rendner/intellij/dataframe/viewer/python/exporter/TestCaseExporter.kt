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
package cms.rendner.intellij.dataframe.viewer.python.exporter

import cms.rendner.intellij.dataframe.viewer.models.chunked.TableStructure
import cms.rendner.intellij.dataframe.viewer.models.chunked.evaluator.ChunkEvaluator
import cms.rendner.intellij.dataframe.viewer.models.chunked.utils.iterateDataFrame
import cms.rendner.intellij.dataframe.viewer.python.bridge.IPyPatchedStylerRef
import cms.rendner.intellij.dataframe.viewer.python.bridge.PythonCodeBridge
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern

/**
 * Exports test data, used by unit-tests.
 *
 * @param baseExportDir the base directory for the HTML files generated from the test cases.
 */
class TestCaseExporter(private val baseExportDir: Path) {

    private var exportCounter = 0
    private val pythonBridge = PythonCodeBridge()
    private val removeTrailingSpacesPattern = Pattern.compile("\\p{Blank}+$", Pattern.MULTILINE)

    private val json: Json by lazy {
        Json { ignoreUnknownKeys = true; prettyPrint = true }
    }

    /**
     * Extracts information from a test case and persist these data in the [baseExportDir].
     *
     * For each test case a subdirectory is created inside of [baseExportDir]. Therefore,
     * each test case should have a unique name specified via [TestCaseExportData.exportDirectoryPath].
     *
     * The following data is extracted and stored:
     * - the expected HTML file (extracted from the original DataFrame)
     * - the HTML file from multiple chunks
     * - the table structure info
     */
    fun export(testCase: TestCaseExportData) {
        val patchedStyler = pythonBridge.createPatchedStyler(testCase.styler)
        try {
            val tableStructure = patchedStyler.evaluateTableStructure()
            if (tableStructure.rowsCount > 200) {
                throw IllegalArgumentException("DataFrame has to many rows (${tableStructure.rowsCount}), can't generate test data from it. Please use a DataFrame with max 200 rows.")
            }

            println("export test case ${++exportCounter}: ${testCase.exportDirectoryPath}")

            val testCaseProperties = TestCaseProperties(
                testCase.exportChunkSize.rows,
                testCase.exportChunkSize.columns,
                tableStructure
            )

            baseExportDir.resolve(testCase.exportDirectoryPath).let {
                TestCasePath.createRequiredDirectories(it)
                writeTestCasePropertiesToFile(it, testCaseProperties)
                writeExpectedResultToFile(patchedStyler, it)
                writeChunksToFile(patchedStyler, it, testCase, tableStructure)
            }
        } finally {
            patchedStyler.dispose()
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun writeExpectedResultToFile(
        patchedStyler: IPyPatchedStylerRef,
        exportDir: Path,
    ) {
        val jsonResult = json.encodeToString(patchedStyler.evaluateComputeUnpatchedHTMLPropsTable())
        Files.newBufferedWriter(TestCasePath.resolveExpectedResultFile(exportDir, "json")).use {
            it.write(jsonResult)
        }
        val result = patchedStyler.evaluateRenderUnpatched()
        Files.newBufferedWriter(TestCasePath.resolveExpectedResultFile(exportDir, "html")).use {
            it.write(prettifyHtmlAndReplaceRandomTableId(result))
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun writeChunksToFile(
        patchedStyler: IPyPatchedStylerRef,
        exportDir: Path,
        exportData: TestCaseExportData,
        tableStructure: TableStructure
    ) {
        val chunkSize = exportData.exportChunkSize
        val evaluator = ChunkEvaluator(patchedStyler)

        iterateDataFrame(tableStructure.rowsCount, tableStructure.columnsCount, chunkSize).forEach { chunk ->
            val result = evaluator.evaluate(chunk, chunk.firstColumn > 0, chunk.firstRow > 0)
            Files.newBufferedWriter(
                TestCasePath.resolveChunkResultFile(exportDir, chunk.firstRow, chunk.firstColumn, "html")
            ).use {
                it.write(prettifyHtmlAndReplaceRandomTableId(result))
            }

            val result2 = evaluator.evaluateHTMLProps(chunk, chunk.firstColumn > 0, chunk.firstRow > 0)
            Files.newBufferedWriter(
                TestCasePath.resolveChunkResultFile(exportDir, chunk.firstRow, chunk.firstColumn, "json")
            ).use {
                it.write(json.encodeToString(result2))
            }
        }
    }

    private fun prettifyHtmlAndReplaceRandomTableId(html: String): String {
        // The exported table has a random id, if not a static one was specified on the styler.
        // That id is used for the table elements and for the css styles which style the table elements.
        // To have a stable output the id is always replaced with a static one.
        val document = Jsoup.parse(html)
        val tableId = document.selectFirst("table").id()
        var prettified = document.outerHtml()
        // fix for jsoup #1689 (Pretty print leaves extra space at end of many lines)
        prettified = removeTrailingSpacesPattern.matcher(prettified).replaceAll("")
        return prettified.replace(tableId, "static_id")
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun writeTestCasePropertiesToFile(exportDir: Path, testCaseProperties: TestCaseProperties) {
        Files.newBufferedWriter(
            TestCasePath.resolveTestCasePropertiesFile(exportDir)
        ).use {
            it.write(json.encodeToString(testCaseProperties))
        }
    }
}