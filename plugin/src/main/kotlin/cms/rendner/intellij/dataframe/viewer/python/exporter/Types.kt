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
import java.nio.file.Files
import java.nio.file.Path

/**
 * A set of test cases to export.
 *
 * A testcase is a Python dict with at least the following keys:
 * - styler: the styled DataFrame to export
 * - chunk_size: the size of the chunks used to export smaller chunks
 * - export_dir: the name of the test case as directory name
 *
 * @param testCases a Python ref to a list of test cases.
 * @param pandasMajorMinorVersion the major and minor version, of pandas used by the test cases, separated by a ".".
 */
data class ExportData(val testCases: PluginPyValue, val pandasMajorMinorVersion: String)

/**
 * Describes a test case to export.
 *
 * @param styler the styled DataFrame to export
 * @param exportChunkSize the size of the chunks used to export smaller chunks
 * @param exportDirectoryPath the name of the test case as directory name
 */
data class TestCaseExportData(
    val styler: PluginPyValue,
    val exportChunkSize: ChunkSize,
    val exportDirectoryPath: String
)

/**
 * Test case path resolver.
 */
class TestCasePath {
    companion object {
        fun resolveChunkResultFile(testCaseDir: Path, row: Int, column: Int): Path {
            return testCaseDir.resolve("chunks/r${row}_c${column}.html")
        }

        fun resolveTestPropertiesFile(testCaseDir: Path): Path {
            return testCaseDir.resolve("test.properties")
        }

        fun resolveExpectedResultFile(testCaseDir: Path): Path {
            return testCaseDir.resolve("expected.html")
        }

        fun resolveComputedCSSFile(testCaseDir: Path): Path {
            return testCaseDir.resolve("expected.css-html")
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