package cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked

import cms.rendner.intellij.dataframe.viewer.core.component.models.IDataFrameModel
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.ChunkSize
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.TableStructure
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.helper.DataFrameTableImageWriter
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.loader.IChunkDataLoader
import cms.rendner.intellij.dataframe.viewer.pycharm.exporter.TestCasePath
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import com.intellij.util.io.exists
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*

/**
 * Requires:
 * Generated test data in the folder "test/resources/generated".
 * Please read the instruction about how to generate test resources (docs/plugin-test-data.md).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal abstract class BaseResourceValidationTest(errorImageSubDirName: String) {

    private val testCaseDir = Paths.get(System.getProperty("cms.rendner.dataframe.renderer.export.test.data.dir"))
    private val testErrorImageDir = Paths.get(
        System.getProperty("cms.rendner.dataframe.renderer.export.test.error.image.dir"),
        errorImageSubDirName,
    )

    data class TestCase(val dir: Path, val name: String) : Comparable<TestCase> {
        override fun toString() = name
        override fun compareTo(other: TestCase): Int {
            return name.compareTo(other.name)
        }
    }

    @BeforeAll
    fun cleanupErrorImages() {
        if (testErrorImageDir.exists()) {
            testErrorImageDir.delete(true)
        }
        testErrorImageDir.createDirectories()
    }

    @Suppress("unused")
    private fun getTestCases() = TestCaseCollector().collect(testCaseDir)
    //.filter { it.name.startsWith("pandas1.1/") }
    //.filter { it.name.startsWith("pandas1.3/hide_columns/columns") }

    @ParameterizedTest(name = "case: {0}")
    @MethodSource("getTestCases")
    fun testCollectedTestCases(testCase: TestCase) {
        val properties = readTestProperties(testCase.dir)
        val tableStructure = createTableStructure(properties)
        val chunkSize = createChunkSize(properties)
        testCollectedTestCase(
            testCase,
            properties,
            tableStructure,
            chunkSize,
        )
    }

    abstract fun testCollectedTestCase(
        testCase: TestCase,
        properties: Properties,
        tableStructure: TableStructure,
        chunkSize: ChunkSize,
    )

    fun writeErrorImage(model: IDataFrameModel, testCase: TestCase, imageNameSuffix: String) {
        DataFrameTableImageWriter.writeImage(
            model,
            testErrorImageDir.resolve("${testCase.name}_${imageNameSuffix}.png")
        )
    }

    private fun readTestProperties(testCaseDir: Path): Properties {
        return Files.newInputStream(TestCasePath.resolveTestPropertiesFile(testCaseDir)).use {
            Properties().apply { load(it) }
        }
    }

    private fun createTableStructure(properties: Properties): TableStructure {
        return TableStructure(
            rowsCount = properties.getProperty("rowsCount").toInt(),
            columnsCount = properties.getProperty("columnsCount").toInt(),
            visibleRowsCount = properties.getProperty("visibleRowsCount").toInt(),
            visibleColumnsCount = properties.getProperty("visibleColumnsCount").toInt(),
            rowLevelsCount = properties.getProperty("rowLevelsCount").toInt(),
            columnLevelsCount = properties.getProperty("columnLevelsCount").toInt(),
            hideRowHeader = properties.getProperty("hideRowHeader")!!.toBoolean(),
            hideColumnHeader = properties.getProperty("hideColumnHeader")!!.toBoolean()
        )
    }

    private fun createChunkSize(properties: Properties): ChunkSize {
        return ChunkSize(
            properties.getProperty("rowsPerChunk").toInt(),
            properties.getProperty("columnsPerChunk").toInt()
        )
    }

    protected fun createDataFrameModel(
        tableStructure: TableStructure,
        chunkLoader: IChunkDataLoader
    ): IDataFrameModel {
        val tableModel = ChunkedDataFrameModel(
            tableStructure,
            chunkLoader
        )
        preloadTableValues(
            tableModel,
            tableStructure,
            chunkLoader.chunkSize
        )
        return tableModel
    }

    private fun preloadTableValues(
        dateFrameModel: IDataFrameModel,
        tableStructure: TableStructure,
        chunkSize: ChunkSize
    ) {
        val valueModel = dateFrameModel.getValueDataModel()
        if (valueModel.columnCount == 0 && valueModel.rowCount == 0) return
        for (row in 0 until tableStructure.visibleRowsCount step chunkSize.rows) {
            for (column in 0 until tableStructure.visibleColumnsCount step chunkSize.columns) {
                valueModel.getValueAt(row, column)
            }
        }
    }

    class TestCaseCollector {

        fun collect(baseDir: Path): List<TestCase> {
            val finder = MyTestCaseVisitor(baseDir)
            Files.walkFileTree(baseDir, finder)
            return finder.testCases.sortedWith(naturalOrder())
        }

        private class MyTestCaseVisitor(baseDir: Path) : FileVisitor<Path> {
            val testCases = mutableListOf<TestCase>()
            val baseDirNameCount = baseDir.nameCount
            override fun preVisitDirectory(dir: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                if (dir != null && Files.exists(TestCasePath.resolveTestPropertiesFile(dir))) {
                    testCases.add(
                        TestCase(
                            dir,
                            dir.subpath(baseDirNameCount, dir.nameCount).toString()
                        )
                    )
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path?, exc: IOException?): FileVisitResult {
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path?, exc: IOException?): FileVisitResult {
                return FileVisitResult.CONTINUE
            }
        }
    }
}