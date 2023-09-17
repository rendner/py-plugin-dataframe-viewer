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
import java.nio.file.Files
import java.nio.file.Path

/**
 * Exports test data, used by unit-tests.
 *
 * @param baseExportDir the base directory for the JSON files generated from the test cases.
 */
class TestCaseExporter(private val baseExportDir: Path) {

    private var exportCounter = 0

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
     * - the expected JSON file (extracted from the original DataFrame)
     * - the JSON file for each chunk
     * - the table structure info (fingerprint is cleared)
     */
    fun export(testCase: TestCaseExportData) {
        val patchedStyler = createPatchedStyler(testCase)
        val tableStructure = patchedStyler.evaluateTableStructure().copy(fingerprint = "")
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
            // create new patched styler instances for the expected results
            // otherwise css styles are duplicated in the generated output
            // (some pandas versions don't do a proper cleanup before generating the output)
            writeExpectedJsonResultToFile(createPatchedStyler(testCase), it)
            writeChunksToFile(patchedStyler, it, testCase, tableStructure)
        }
    }

    private fun createPatchedStyler(testCase: TestCaseExportData): IPyPatchedStylerRef {
        return PythonCodeBridge.createPatchedStyler(
            testCase.createStylerFunc.evaluator,
            "${testCase.createStylerFunc.refExpr}()",
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun writeExpectedJsonResultToFile(
        patchedStyler: IPyPatchedStylerRef,
        exportDir: Path,
    ) {
        val result = json.encodeToString(patchedStyler.evaluateComputeUnpatchedHTMLPropsTable())
        Files.newBufferedWriter(TestCasePath.resolveExpectedResultFile(exportDir)).use {
            it.write(result)
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
            val result = evaluator.evaluateHTMLProps(chunk, chunk.firstColumn > 0, chunk.firstRow > 0)
            Files.newBufferedWriter(
                TestCasePath.resolveChunkResultFile(exportDir, chunk.firstRow, chunk.firstColumn)
            ).use {
                it.write(json.encodeToString(result))
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun writeTestCasePropertiesToFile(exportDir: Path, properties: TestCaseProperties) {
        Files.newBufferedWriter(
            TestCasePath.resolveTestCasePropertiesFile(exportDir)
        ).use {
            it.write(
                json.encodeToString(
                    TestCaseProperties(properties.rowsPerChunk, properties.colsPerChunk, properties.tableStructure)
                )
            )
        }
    }
}