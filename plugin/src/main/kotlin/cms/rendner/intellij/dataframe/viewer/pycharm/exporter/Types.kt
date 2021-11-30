package cms.rendner.intellij.dataframe.viewer.pycharm.exporter

import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.ChunkSize
import com.jetbrains.python.debugger.PyDebugValue
import java.nio.file.Files
import java.nio.file.Path

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