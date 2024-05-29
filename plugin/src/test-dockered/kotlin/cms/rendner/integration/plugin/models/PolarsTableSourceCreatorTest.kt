/*
 * Copyright 2021-2024 cms.rendner (Daniel Schmidt)
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
package cms.rendner.integration.plugin.models

import cms.rendner.intellij.dataframe.viewer.components.filter.FilterInputState
import cms.rendner.intellij.dataframe.viewer.models.chunked.TableSourceCreator
import cms.rendner.intellij.dataframe.viewer.python.bridge.CreateTableSourceErrorKind
import cms.rendner.intellij.dataframe.viewer.python.bridge.providers.PolarsCodeProvider
import cms.rendner.junit.RequiresPolars
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@RequiresPolars
internal class PolarsTableSourceCreatorTest : AbstractTableSourceCreatorTest(PolarsCodeProvider()) {

    @Test
    fun shouldFilterDataForSyntheticIdentifier() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSourceInfo = createDataSourceInfo(debuggerApi, "df")
            val creator = MyTestCreator(debuggerApi.evaluator)
            creator.create(
                TableSourceCreator.Request(
                    dataSourceInfo,
                    false,
                    null,
                    FilterInputState("_df.filter(pl.col('col_0').is_between(1, 3))", true),
                )
            )

            assertThat(creator.result).isNotNull
            creator.result!!.let {
                assertThat(it.tableStructure.rowsCount).isLessThan(it.tableStructure.orgRowsCount)
            }
        }
    }

    @Test
    fun shouldReportFailureIfFilterEvaluationFails() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSourceInfo = createDataSourceInfo(debuggerApi, "df")

            val creator = MyTestCreator(debuggerApi.evaluator)
            creator.create(
                TableSourceCreator.Request(
                    dataSourceInfo,
                    false,
                    null,
                    FilterInputState("xyz"),
                )
            )

            assertThat(creator.result).isNull()
            assertThat(creator.failure?.errorKind).isEqualTo(CreateTableSourceErrorKind.FILTER_FRAME_EVAL_FAILED)
        }
    }

    @Test
    fun shouldReportFailureIfFilterEvaluatesToWrongType() {
        createPythonDebuggerWithCodeSnippet(createDataFrameSnippet()) { debuggerApi ->

            val dataSourceInfo = createDataSourceInfo(debuggerApi, "df")

            val creator = MyTestCreator(debuggerApi.evaluator)
            creator.create(
                TableSourceCreator.Request(
                    dataSourceInfo,
                    false,
                    null,
                    FilterInputState("123"),
                )
            )

            assertThat(creator.result).isNull()
            assertThat(creator.failure?.errorKind).isEqualTo(CreateTableSourceErrorKind.FILTER_FRAME_OF_WRONG_TYPE)
        }
    }


    override fun createDataFrameSnippet() = """
    |import polars as pl
    |
    |df = pl.from_dict({
    |    "col_0": [0, 1],
    |    "col_1": [2, 3],
    |})
    |
    |breakpoint()
""".trimMargin()
}