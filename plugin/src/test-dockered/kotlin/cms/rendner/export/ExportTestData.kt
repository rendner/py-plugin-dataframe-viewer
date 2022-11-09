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
package cms.rendner.export

import cms.rendner.integration.plugin.AbstractPluginCodeTest
import cms.rendner.intellij.dataframe.viewer.SystemPropertyKey
import cms.rendner.intellij.dataframe.viewer.python.exporter.ExportTask
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Paths

/**
 * The class exports the test-data used by the unit test [ChunkValidationTest].
 */
internal class ExportTestData : AbstractPluginCodeTest() {

    private val exportDir = System.getProperty(SystemPropertyKey.EXPORT_TEST_DATA_DIR)?.let { Paths.get(it) }

    @Test
    fun exportTestDataForUnitTests() {
        assertThat(exportDir).isNotNull
        runPythonDebuggerWithSourceFile("export_data/main.py") { evaluator, _ ->
            ExportTask(exportDir!!, evaluator.evaluate("export_test_data")).run()
        }
    }
}