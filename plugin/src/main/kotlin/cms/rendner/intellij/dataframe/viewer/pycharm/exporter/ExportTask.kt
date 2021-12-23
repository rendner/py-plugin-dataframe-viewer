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
import cms.rendner.intellij.dataframe.viewer.pycharm.evaluator.IValueEvaluator
import com.jetbrains.python.debugger.PyDebugValue
import java.nio.file.Path

open class ExportTask(
    private val baseExportDir: Path,
    private val exportDataValue: PyDebugValue,
    private val evaluator: IValueEvaluator,
) {
    fun run() {
        val exportData = convertExportValue(exportDataValue, evaluator)
        val exportDir = baseExportDir.resolve("pandas${exportData.pandasMajorMinorVersion}")
        println("exportDir: $exportDir")
        val testCaseExporter = TestCaseExporter(exportDir)
        val testCaseIterator = EvaluateElementWiseListIterator(exportData.testCases, evaluator)

        while (testCaseIterator.hasNext()) {
            testCaseExporter.export(convertTestCaseValue(testCaseIterator.next(), evaluator))
        }
    }

    private fun convertExportValue(exportDataDict: PyDebugValue, evaluator: IValueEvaluator): ExportData {
        exportDataDict.evaluationExpression.let {
            val testCases = evaluator.evaluate("$it['test_cases']", true)
            val pandasVersion = evaluator.evaluate("$it['pandas_version']")
            val versionParts = pandasVersion.value!!.split(".")
            val majorMinor = "${versionParts[0]}.${versionParts[1]}"
            return ExportData(testCases, majorMinor)
        }
    }

    private fun convertTestCaseValue(exportTestCaseDict: PyDebugValue, evaluator: IValueEvaluator): TestCaseExportData {
        return exportTestCaseDict.evaluationExpression.let {
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
        private val testCases: PyDebugValue,
        private val evaluator: IValueEvaluator,
    ) : Iterator<PyDebugValue> {

        private var lastFetchedEntryIndex: Int = -1
        private val size: Int = evaluator.evaluate("len(${testCases.tempName})").value!!.toInt()

        override fun hasNext(): Boolean {
            return lastFetchedEntryIndex + 1 < size
        }

        override fun next(): PyDebugValue {
            lastFetchedEntryIndex++
            return evaluator.evaluate("${testCases.tempName}[$lastFetchedEntryIndex]")
        }
    }
}