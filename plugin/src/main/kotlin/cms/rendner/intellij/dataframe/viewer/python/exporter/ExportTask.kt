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
package cms.rendner.intellij.dataframe.viewer.python.exporter

import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkSize
import cms.rendner.intellij.dataframe.viewer.python.bridge.PandasVersion
import cms.rendner.intellij.dataframe.viewer.python.debugger.PluginPyValue
import java.nio.file.Path

/**
 * Iterates over the available test data, provided by [exportDataValue] and saves the HTML files
 * for each one in the directory specified by [rootExportDir].
 *
 * @param rootExportDir the root directory for the files generated from the test cases.
 * @param exportDataValue provides a list of test cases.
 */
class ExportTask(
    private val rootExportDir: Path,
    private val exportDataValue: PluginPyValue,
) {
    fun run() {
        val exportData = convertExportValue(exportDataValue)
        val baseExportDir = exportData.resolveBaseExportDir(rootExportDir)
        println("baseExportDir: $baseExportDir")
        val testCaseExporter = TestCaseExporter(baseExportDir)
        val testCaseIterator = EvaluateElementWiseListIterator(exportData.testCases)

        while (testCaseIterator.hasNext()) {
            testCaseExporter.export(convertTestCaseValue(testCaseIterator.next()))
        }
    }

    private fun convertExportValue(exportDataDict: PluginPyValue): ExportData {
        val evaluator = exportDataDict.evaluator
        exportDataDict.refExpr.let {
            return ExportData(
                evaluator.evaluate("$it['test_cases']", true),
                PandasVersion.fromString(evaluator.evaluate("$it['pandas_version']").forcedValue),
            )
        }
    }

    private fun convertTestCaseValue(exportTestCaseDict: PluginPyValue): TestCaseExportData {
        val evaluator = exportTestCaseDict.evaluator
        return exportTestCaseDict.refExpr.let {
            val createStylerFunc = evaluator.evaluate("$it['create_styler']")
            val chunkSize = evaluator.evaluate("$it['chunk_size']").forcedValue.removeSurrounding("(", ")").split(", ")
            val exportDir = evaluator.evaluate("$it['export_dir']").forcedValue

            TestCaseExportData(
                createStylerFunc,
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