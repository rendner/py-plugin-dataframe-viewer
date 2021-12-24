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
package cms.rendner.intellij.dataframe.viewer.python.exporter

import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkSize
import cms.rendner.intellij.dataframe.viewer.python.debugger.PluginPyValue
import java.nio.file.Path

open class ExportTask(
    private val baseExportDir: Path,
    private val exportDataValue: PluginPyValue,
) {
    fun run() {
        val exportData = convertExportValue(exportDataValue)
        val exportDir = baseExportDir.resolve("pandas${exportData.pandasMajorMinorVersion}")
        println("exportDir: $exportDir")
        val testCaseExporter = TestCaseExporter(exportDir)
        val testCaseIterator = EvaluateElementWiseListIterator(exportData.testCases)

        while (testCaseIterator.hasNext()) {
            testCaseExporter.export(convertTestCaseValue(testCaseIterator.next()))
        }
    }

    private fun convertExportValue(exportDataDict: PluginPyValue): ExportData {
        val evaluator = exportDataDict.evaluator
        exportDataDict.pythonRefEvalExpr.let {
            val testCases = evaluator.evaluate("$it['test_cases']", true)
            val pandasVersion = evaluator.evaluate("$it['pandas_version']")
            val versionParts = pandasVersion.value!!.split(".")
            val majorMinor = "${versionParts[0]}.${versionParts[1]}"
            return ExportData(testCases, majorMinor)
        }
    }

    private fun convertTestCaseValue(exportTestCaseDict: PluginPyValue): TestCaseExportData {
        val evaluator = exportTestCaseDict.evaluator
        return exportTestCaseDict.pythonRefEvalExpr.let {
            val styler = evaluator.evaluate("$it['styler']")
            val chunkSize = evaluator.evaluate("str($it['chunk_size'][0]) + ':' + str($it['chunk_size'][1])")
            val exportDir = evaluator.evaluate("$it['export_dir']")
            TestCaseExportData(
                styler,
                chunkSize.value!!.split(":").let { parts -> ChunkSize(parts[0].toInt(), parts[1].toInt()) },
                exportDir.value!!
            )
        }
    }

    private class EvaluateElementWiseListIterator(
        private val testCases: PluginPyValue,
    ) : Iterator<PluginPyValue> {

        private var lastFetchedEntryIndex: Int = -1
        private val size: Int = testCases.evaluator.evaluate("len(${testCases.pythonRefEvalExpr})").value!!.toInt()

        override fun hasNext(): Boolean {
            return lastFetchedEntryIndex + 1 < size
        }

        override fun next(): PluginPyValue {
            lastFetchedEntryIndex++
            return testCases.evaluator.evaluate("${testCases.pythonRefEvalExpr}[$lastFetchedEntryIndex]")
        }
    }
}