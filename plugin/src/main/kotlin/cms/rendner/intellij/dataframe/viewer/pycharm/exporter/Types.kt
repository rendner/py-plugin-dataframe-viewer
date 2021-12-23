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
import com.jetbrains.python.debugger.PyDebugValue
import java.nio.file.Files
import java.nio.file.Path

data class ExportData(val testCases: PyDebugValue, val pandasMajorMinorVersion: String)

data class TestCaseExportData(
    val styler: PyDebugValue,
    val exportChunkSize: ChunkSize,
    val exportDirectoryPath: String
)

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
                if(Files.notExists(it)) {
                    Files.createDirectories(it)
                }
            }
        }
    }
}