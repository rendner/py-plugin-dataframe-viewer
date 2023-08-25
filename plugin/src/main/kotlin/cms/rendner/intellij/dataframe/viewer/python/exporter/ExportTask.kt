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
import cms.rendner.intellij.dataframe.viewer.python.debugger.exceptions.EvaluateException
import java.nio.file.Path

/**
 * Iterates over the provided [testCases], and writes the dumped data into json files.
 * The json files are stored into separate dictionaries inside [rootExportDir].
 *
 * @param rootExportDir the directory to store the generated test data.
 * @param testCases a python list of test cases.
 */
class ExportTask(
    private val rootExportDir: Path,
    private val testCases: PluginPyValue,
) {
    fun run() {
        val pandasVersion = try {
            PandasVersion.fromString(testCases.evaluator.evaluate("__import__('pandas').__version__").forcedValue)
        } catch (ex: EvaluateException) {
            throw IllegalStateException("Failed to identify version of pandas.", ex)
        }
        val baseExportDir = rootExportDir.resolve("pandas_${pandasVersion.major}.${pandasVersion.minor}")
        println("baseExportDir: $baseExportDir")
        val testCaseExporter = TestCaseExporter(baseExportDir)
        val testCaseIterator = EvaluateElementWiseListIterator(testCases)
        println("testCases: ${testCaseIterator.size}")

        while (testCaseIterator.hasNext()) {
            testCaseExporter.export(convertTestCaseValue(testCaseIterator.next()))
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
        val size: Int = testCases.evaluator.evaluate("len(${testCases.refExpr})").forcedValue.toInt()

        override fun hasNext(): Boolean {
            return lastFetchedEntryIndex + 1 < size
        }

        override fun next(): PluginPyValue {
            lastFetchedEntryIndex++
            return testCases.evaluator.evaluate("${testCases.refExpr}[$lastFetchedEntryIndex]")
        }
    }
}