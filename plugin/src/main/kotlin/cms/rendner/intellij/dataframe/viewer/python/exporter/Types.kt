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
import cms.rendner.intellij.dataframe.viewer.models.chunked.TableStructure
import cms.rendner.intellij.dataframe.viewer.python.debugger.PluginPyValue
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path


/**
 * Describes a test case to export.
 *
 * @param createStylerFunc a parameterless function to create a styled DataFrame for export
 * @param exportChunkSize the size of the chunks used to export smaller chunks
 * @param exportDirectoryPath the name of the test case as directory name
 */
data class TestCaseExportData(
    val createStylerFunc: PluginPyValue,
    val exportChunkSize: ChunkSize,
    val exportDirectoryPath: String
)

/**
 * Additional information about an exported DataFrame.
 *
 * @param rowsPerChunk number of rows per chunk, used during the export of a styled DataFrame.
 * @param colsPerChunk number of columns per chunk, used during the export a styled DataFrame.
 * @param tableStructure describes the structure of the exported DataFrame.
 */
@Serializable
class TestCaseProperties(
    val rowsPerChunk: Int,
    val colsPerChunk: Int,
    val tableStructure: TableStructure,
)

/**
 * Test case path resolver.
 */
class TestCasePath {
    companion object {
        fun resolveChunkResultFile(testCaseDir: Path, row: Int, column: Int): Path {
            return testCaseDir.resolve("chunks/r${row}_c${column}.json")
        }

        fun resolveTestCasePropertiesFile(testCaseDir: Path): Path {
            return testCaseDir.resolve("testCaseProperties.json")
        }

        fun resolveExpectedResultFile(testCaseDir: Path): Path {
            return testCaseDir.resolve("expected.json")
        }

        fun createRequiredDirectories(testCaseDir: Path) {
            testCaseDir.resolve("chunks").let {
                if (Files.notExists(it)) {
                    Files.createDirectories(it)
                }
            }
        }
    }
}