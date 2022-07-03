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
package cms.rendner.intellij.dataframe.viewer.models.chunked

import cms.rendner.intellij.dataframe.viewer.SystemPropertyEnum
import cms.rendner.intellij.dataframe.viewer.models.IDataFrameModel
import cms.rendner.intellij.dataframe.viewer.models.chunked.helper.BlockingChunkDataLoader
import cms.rendner.intellij.dataframe.viewer.models.chunked.helper.DataFrameTableImageWriter
import cms.rendner.intellij.dataframe.viewer.models.chunked.helper.createChunkFileEvaluator
import cms.rendner.intellij.dataframe.viewer.models.chunked.helper.createExpectedFileEvaluator
import cms.rendner.intellij.dataframe.viewer.models.chunked.loader.IChunkDataLoader
import cms.rendner.intellij.dataframe.viewer.models.chunked.utils.iterateDataFrame
import cms.rendner.intellij.dataframe.viewer.python.exporter.TestCasePath
import cms.rendner.intellij.dataframe.viewer.python.exporter.TestCaseProperties
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import com.intellij.util.io.exists
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

data class ResourceTestContext(
    val loadNewDataStructure: Boolean,
    val expectedFileExtension: String,
)

/**
 * Requires:
 * Generated test data in the folder "test/resources/generated".
 * Please read the instruction about how to generate test resources (docs/GENERATE_PLUGIN_TEST_DATA_AUTOMATED.md).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal abstract class BaseResourceValidationTest(errorImageSubDirName: String) {
    private val testCaseDir = Paths.get(System.getProperty(SystemPropertyEnum.EXPORT_TEST_DATA_DIR.key))
    private val testErrorImageDir = Paths.get(
        System.getProperty(SystemPropertyEnum.EXPORT_TEST_ERROR_IMAGE_DIR.key),
        errorImageSubDirName,
    )

    private val contextList = listOf(
        ResourceTestContext(false,  "html"),
        ResourceTestContext(true,  "json"),
    )

    protected val json: Json by lazy {
        Json { ignoreUnknownKeys = true; prettyPrint = true }
    }

    data class TestCase(val dir: Path, val name: String, val context: ResourceTestContext) : Comparable<TestCase> {
        override fun toString() = "$name (${context.expectedFileExtension})"
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
    private fun getTestCases() = TestCaseCollector().collect(testCaseDir, contextList)
    //.filter { it.name.startsWith("pandas_1.1/") }
    //.filter { it.name.startsWith("pandas_1.3/hide_columns/columns") }

    @ParameterizedTest(name = "case: {0}")
    @MethodSource("getTestCases")
    fun testCollectedTestCases(testCase: TestCase) {
        val testCaseProperties = readTestCaseProperties(testCase.dir)
        testCollectedTestCase(
            testCase,
            testCaseProperties.tableStructure,
            ChunkSize(testCaseProperties.rowsPerChunk, testCaseProperties.colsPerChunk),
        )
    }

    abstract fun testCollectedTestCase(
        testCase: TestCase,
        tableStructure: TableStructure,
        chunkSize: ChunkSize,
    )

    fun writeErrorImage(model: IDataFrameModel, testCase: TestCase, imageNameSuffix: String) {
        DataFrameTableImageWriter.writeImage(
            model,
            testErrorImageDir.resolve("${testCase.name}_${imageNameSuffix}.png")
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun readTestCaseProperties(testCaseDir: Path): TestCaseProperties {
        return Files.newBufferedReader(TestCasePath.resolveTestCasePropertiesFile(testCaseDir)).use {
            json.decodeFromString(it.readText())
        }
    }

    private fun createDataFrameModel(
        tableStructure: TableStructure,
        chunkLoader: IChunkDataLoader,
        chunkSize: ChunkSize,
    ): IDataFrameModel {
        return ChunkedDataFrameModel(tableStructure, chunkLoader, chunkSize).also {
            preloadTableValues(it, tableStructure, chunkSize)
        }
    }

    protected fun createExpectedModel(testCase: TestCase, tableStructure: TableStructure): IDataFrameModel {
        val ctx = testCase.context
        return createDataFrameModel(
            tableStructure,
            BlockingChunkDataLoader(
                createExpectedFileEvaluator(
                    TestCasePath.resolveExpectedResultFile(
                        testCase.dir,
                        ctx.expectedFileExtension
                    )
                ),
                testCase.context.loadNewDataStructure,
            ),
            ChunkSize(tableStructure.rowsCount, tableStructure.columnsCount),
        )
    }

    protected fun createChunkedModel(
        testCase: TestCase,
        tableStructure: TableStructure,
        chunkSize: ChunkSize
    ): IDataFrameModel {
        return createDataFrameModel(
            tableStructure,
            BlockingChunkDataLoader(
                createChunkFileEvaluator(testCase.dir),
                testCase.context.loadNewDataStructure,
            ),
            chunkSize,
        )
    }

    private fun preloadTableValues(
        dateFrameModel: IDataFrameModel,
        tableStructure: TableStructure,
        chunkSize: ChunkSize
    ) {
        val valueModel = dateFrameModel.getValueDataModel()
        if (valueModel.columnCount == 0 && valueModel.rowCount == 0) return

        iterateDataFrame(tableStructure.rowsCount, tableStructure.columnsCount, chunkSize).forEach {
            valueModel.getValueAt(it.firstRow, it.firstColumn)
        }
    }

    class TestCaseCollector {

        fun collect(baseDir: Path, contextList: List<ResourceTestContext>): List<TestCase> {
            val finder = MyTestCaseVisitor(baseDir, contextList)
            Files.walkFileTree(baseDir, finder)
            return finder.testCases.sortedWith(naturalOrder())
        }

        private class MyTestCaseVisitor(baseDir: Path, private val contextList: List<ResourceTestContext>) : SimpleFileVisitor<Path>() {
            val testCases = mutableListOf<TestCase>()
            val baseDirNameCount = baseDir.nameCount
            override fun preVisitDirectory(dir: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                if (dir != null && Files.exists(TestCasePath.resolveTestCasePropertiesFile(dir))) {
                    contextList.forEach {
                        testCases.add(
                            TestCase(
                                dir,
                                dir.subpath(baseDirNameCount, dir.nameCount).toString(),
                                it,
                            )
                        )
                    }
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }
        }
    }
}