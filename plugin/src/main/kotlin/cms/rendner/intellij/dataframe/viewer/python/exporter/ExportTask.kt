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

import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkSize
import cms.rendner.intellij.dataframe.viewer.python.debugger.PluginPyValue
import java.nio.file.Path

/**
 * Iterates over the available test data, provided by [exportDataValue] and saves the HTML files
 * for each one in the directory specified by [baseExportDir].
 *
 * @param baseExportDir the base directory for the HTML files generated from the test cases.
 * @param exportDataValue provides a list of test cases.
 */
class ExportTask(
    private val baseExportDir: Path,
    private val exportDataValue: PluginPyValue,
) {
    fun run() {
        try {
            val exportData = convertExportValue(exportDataValue)
            val exportDir = baseExportDir.resolve("pandas_${exportData.pandasMajorMinorVersion}")
            println("exportDir: $exportDir")
            val testCaseExporter = TestCaseExporter(exportDir)
            val testCaseIterator = EvaluateElementWiseListIterator(exportData.testCases)

            while (testCaseIterator.hasNext()) {
                testCaseExporter.export(convertTestCaseValue(testCaseIterator.next()))
            }
        } catch (e: Throwable) {
            println("ExportTask::run failed: ${e.stackTraceToString()}")
        }
    }

    private fun convertExportValue(exportDataDict: PluginPyValue): ExportData {
        val evaluator = exportDataDict.evaluator
        exportDataDict.refExpr.let {
            val testCases = evaluator.evaluate("$it['test_cases']", true)
            val pandasVersion = evaluator.evaluate("$it['pandas_version']")
            val versionParts = pandasVersion.forcedValue.split(".")
            val majorMinor = "${versionParts[0]}.${versionParts[1]}"
            return ExportData(testCases, majorMinor)
        }
    }

    private fun convertTestCaseValue(exportTestCaseDict: PluginPyValue): TestCaseExportData {
        val evaluator = exportTestCaseDict.evaluator
        return exportTestCaseDict.refExpr.let {
            val styler = evaluator.evaluate("$it['styler']")
            val chunkSize = evaluator.evaluate("$it['chunk_size']").forcedValue.removeSurrounding("(", ")").split(", ")
            val exportDir = evaluator.evaluate("$it['export_dir']").forcedValue

            TestCaseExportData(
                styler,
                ChunkSize(chunkSize[0].toInt(), chunkSize[1].toInt()),
                exportDir,
            )
        }
    }

    private class EvaluateElementWiseListIterator(
        private val testCases: PluginPyValue,
    ) : Iterator<PluginPyValue> {

        private var lastFetchedEntryIndex: Int = -1
        private val size: Int = testCases.evaluator.evaluate("len(${testCases.refExpr})").forcedValue.toInt()

        override fun hasNext(): Boolean {
            return lastFetchedEntryIndex + 1 < size
        }

        override fun next(): PluginPyValue {
            lastFetchedEntryIndex++
            return testCases.evaluator.evaluate("${testCases.refExpr}[$lastFetchedEntryIndex]")
        }
    }
}