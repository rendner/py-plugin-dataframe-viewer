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
package cms.rendner.intellij.dataframe.viewer.pycharm.exporter

import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.ChunkSize
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.TableStructure
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.ChunkCoordinates
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.evaluator.AllAtOnceEvaluator
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.evaluator.ChunkEvaluator
import cms.rendner.intellij.dataframe.viewer.pycharm.injector.PluginPythonCodeBridge
import cms.rendner.intellij.dataframe.viewer.pycharm.injector.PyPatchedStylerRef
import org.jsoup.Jsoup
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

class TestCaseExporter(private val baseExportDir: Path) {

    private var exportCounter = 0
    private val pythonBridge = PluginPythonCodeBridge()

    fun export(testCase: TestCaseExportData) {
        val patchedStyler = pythonBridge.createPatchedStyler(testCase.styler)
        try {
            val tableStructure = patchedStyler.evaluateTableStructure()
            if (tableStructure.visibleRowsCount > 200) {
                throw IllegalArgumentException("DataFrame has to many rows (${tableStructure.visibleRowsCount}), can't generate test data from it. Please use a DataFrame with max 200 rows.")
            }

            println("export test case ${++exportCounter}: ${testCase.exportDirectoryPath}")

            baseExportDir.resolve(testCase.exportDirectoryPath).let {
                TestCasePath.createRequiredDirectories(it)
                writePropertiesFile(testCase, it, tableStructure)
                writeExpectedResultToFile(patchedStyler, it, tableStructure)
                writeChunksToFile(patchedStyler, it, testCase, tableStructure)
            }
        } finally {
            patchedStyler.dispose()
        }
    }

    private fun writeExpectedResultToFile(
        patchedStyler: PyPatchedStylerRef,
        exportDir: Path,
        tableStructure: TableStructure
    ) {
        val evaluator =
            AllAtOnceEvaluator(patchedStyler, ChunkSize(tableStructure.visibleRowsCount, tableStructure.visibleColumnsCount))
        val result = evaluator.evaluate()
        Files.newBufferedWriter(TestCasePath.resolveExpectedResultFile(exportDir)).use {
            it.write(prettifyHtmlAndReplaceRandomTableId(result))
        }
    }

    private fun writeChunksToFile(
        patchedStyler: PyPatchedStylerRef,
        exportDir: Path,
        exportData: TestCaseExportData,
        tableStructure: TableStructure
    ) {
        val chunkSize = exportData.exportChunkSize
        val evaluator = ChunkEvaluator(patchedStyler, chunkSize)
        for (row in 0 until tableStructure.visibleRowsCount step chunkSize.rows) {
            for (column in 0 until tableStructure.visibleColumnsCount step chunkSize.columns) {
                val result = evaluator.evaluate(ChunkCoordinates(row, column), column > 0, row > 0)
                Files.newBufferedWriter(TestCasePath.resolveChunkResultFile(exportDir, row, column)).use {
                    it.write(prettifyHtmlAndReplaceRandomTableId(result))
                }
            }
        }
    }

    private fun prettifyHtmlAndReplaceRandomTableId(html: String): String {
        // The exported table has a random id, if not a static one was specified on the styler.
        // That id is used for the table elements and for the css styles which style the table elements.
        // To have a stable output the id is always replaced with a static one.
        val document = Jsoup.parse(html)
        val tableId = document.selectFirst("table").id()
        val prettified = document.toString()
        return prettified.replace(tableId, "static_id")
    }

    private fun writePropertiesFile(exportData: TestCaseExportData, exportDir: Path, tableStructure: TableStructure) {
        // [StripFirstLineStream] omits the first line which is normally a comment containing the actual date
        // "comments" has to be "null" otherwise the [StripFirstLineStream] doesn't work as expected, because
        // the comments would be written before the date comment
        StripFirstLineStream(
            Files.newOutputStream(
                TestCasePath.resolveTestPropertiesFile(exportDir)
            )
        ).use {
            Properties().apply {
                setProperty("rowsCount", tableStructure.rowsCount.toString())
                setProperty("columnsCount", tableStructure.columnsCount.toString())
                setProperty("visibleRowsCount", tableStructure.visibleRowsCount.toString())
                setProperty("visibleColumnsCount", tableStructure.visibleColumnsCount.toString())
                setProperty("rowLevelsCount", tableStructure.rowLevelsCount.toString())
                setProperty("columnLevelsCount", tableStructure.columnLevelsCount.toString())
                setProperty("hideRowHeader", tableStructure.hideRowHeader.toString())
                setProperty("hideColumnHeader", tableStructure.hideColumnHeader.toString())
                setProperty("rowsPerChunk", exportData.exportChunkSize.rows.toString())
                setProperty("columnsPerChunk", exportData.exportChunkSize.columns.toString())
                store(it, null)
            }
        }
    }

    private class StripFirstLineStream(out: OutputStream) : FilterOutputStream(out) {
        private var firstLineSkipped = false
        private val newLineValue = '\n'.toInt()

        @Throws(IOException::class)
        override fun write(b: Int) {
            if (firstLineSkipped) {
                super.write(b)
            } else if (b == newLineValue) {
                firstLineSkipped = true
            }
        }
    }
}