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

import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkRegion
import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkSize
import cms.rendner.intellij.dataframe.viewer.models.chunked.TableStructure
import cms.rendner.intellij.dataframe.viewer.models.chunked.evaluator.AllAtOnceEvaluator
import cms.rendner.intellij.dataframe.viewer.models.chunked.evaluator.ChunkEvaluator
import cms.rendner.intellij.dataframe.viewer.python.bridge.IPyPatchedStylerRef
import cms.rendner.intellij.dataframe.viewer.python.bridge.PythonCodeBridge
import org.jsoup.Jsoup
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.regex.Pattern

class TestCaseExporter(private val baseExportDir: Path) {

    private var exportCounter = 0
    private val pythonBridge = PythonCodeBridge()
    private val removeTrailingSpacesPattern = Pattern.compile("\\p{Blank}+$", Pattern.MULTILINE)

    fun export(testCase: TestCaseExportData) {
        val patchedStyler = pythonBridge.createPatchedStyler(testCase.styler)
        try {
            val tableStructure = patchedStyler.evaluateTableStructure()
            if (tableStructure.rowsCount > 200) {
                throw IllegalArgumentException("DataFrame has to many rows (${tableStructure.rowsCount}), can't generate test data from it. Please use a DataFrame with max 200 rows.")
            }

            println("export test case ${++exportCounter}: ${testCase.exportDirectoryPath}")

            baseExportDir.resolve(testCase.exportDirectoryPath).let {
                TestCasePath.createRequiredDirectories(it)
                writePropertiesFile(testCase, it, tableStructure)
                writeExpectedResultToFile(patchedStyler, it)
                writeChunksToFile(patchedStyler, it, testCase, tableStructure)
            }
        } finally {
            patchedStyler.dispose()
        }
    }

    private fun writeExpectedResultToFile(
        patchedStyler: IPyPatchedStylerRef,
        exportDir: Path,
    ) {
        val evaluator = AllAtOnceEvaluator(patchedStyler)
        val result = evaluator.evaluate()
        Files.newBufferedWriter(TestCasePath.resolveExpectedResultFile(exportDir)).use {
            it.write(prettifyHtmlAndReplaceRandomTableId(result))
        }
    }

    private fun writeChunksToFile(
        patchedStyler: IPyPatchedStylerRef,
        exportDir: Path,
        exportData: TestCaseExportData,
        tableStructure: TableStructure
    ) {
        val chunkSize = exportData.exportChunkSize
        val evaluator = ChunkEvaluator(patchedStyler)
        for (row in 0 until tableStructure.rowsCount step chunkSize.rows) {
            for (column in 0 until tableStructure.columnsCount step chunkSize.columns) {
                val result = evaluator.evaluate(toChunkRegion(row, column, chunkSize), column > 0, row > 0)
                Files.newBufferedWriter(TestCasePath.resolveChunkResultFile(exportDir, row, column)).use {
                    it.write(prettifyHtmlAndReplaceRandomTableId(result))
                }
            }
        }
    }

    private fun toChunkRegion(firstRow: Int, firstColumn: Int, chunkSize: ChunkSize): ChunkRegion {
        return ChunkRegion(
            firstRow,
            firstColumn,
            firstRow + chunkSize.rows - 1,
            firstColumn + chunkSize.columns - 1,
        )
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