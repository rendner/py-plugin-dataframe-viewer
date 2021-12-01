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
package cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.converter

import cms.rendner.intellij.dataframe.viewer.core.component.models.HeaderLabel
import cms.rendner.intellij.dataframe.viewer.core.component.models.LegendHeaders
import cms.rendner.intellij.dataframe.viewer.core.component.models.LeveledHeaderLabel
import cms.rendner.intellij.dataframe.viewer.core.html.HtmlTableElementProvider
import org.assertj.core.api.Assertions.assertThat
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test

internal class ColumnHeaderLabelsExtractorTest {

    @Test
    fun hierarchicalMultiIndex() {
        val actual = extract(
            """
        <thead>
            <tr>
                <th class="blank"></th>
                <th class="index_name level0">subject</th>
                <th class="col_heading level0 col0" colspan="2">Bob</th>
                <th class="col_heading level0 col2" colspan="2">Alice</th>
            </tr>
            <tr>
                <th class="blank"></th>
                <th class="index_name level1">type</th>
                <th class="col_heading level1 col0">HR</th>
                <th class="col_heading level1 col1">Temp</th>
                <th class="col_heading level1 col2">HR</th>
                <th class="col_heading level1 col3">Temp</th>
            </tr>
            <tr>
                <th class="index_name level0">year</th>
                <th class="index_name level1">visit</th>
                <th class="blank"></th>
                <th class="blank"></th>
                <th class="blank"></th>
                <th class="blank"></th>
            </tr>
        </thead>
        """
        )

        val expected = ColumnHeaderLabelsExtractor.Result(
            LegendHeaders(
                LeveledHeaderLabel("visit", listOf("year")),
                LeveledHeaderLabel("type", listOf("subject"))
            ),
            listOf(
                LeveledHeaderLabel("HR", listOf("Bob")),
                LeveledHeaderLabel("Temp", listOf("Bob")),
                LeveledHeaderLabel("HR", listOf("Alice")),
                LeveledHeaderLabel("Temp", listOf("Alice")),
            )
        )
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun leveledColumns() {
        val actual = extract(
            """
        <thead> 
            <tr> 
                <th class="blank level0"></th> 
                <th class="col_heading level0 col0" colspan="2">A</th> 
                <th class="col_heading level0 col2" colspan="2">B</th> 
                <th class="col_heading level0 col4">C</th> 
            </tr> 
            <tr> 
                <th class="blank level1"></th> 
                <th class="col_heading level1 col0">col_0</th> 
                <th class="col_heading level1 col1">col_1</th> 
                <th class="col_heading level1 col2">col_2</th> 
                <th class="col_heading level1 col3">col_3</th> 
                <th class="col_heading level1 col4">col_4</th> 
            </tr>
        </thead>
        """
        )

        val expected = ColumnHeaderLabelsExtractor.Result(
            LegendHeaders(),
            listOf(
                LeveledHeaderLabel("col_0", listOf("A")),
                LeveledHeaderLabel("col_1", listOf("A")),
                LeveledHeaderLabel("col_2", listOf("B")),
                LeveledHeaderLabel("col_3", listOf("B")),
                LeveledHeaderLabel("col_4", listOf("C")),
            )
        )
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun defaultColumnsWithLeveledIndex() {
        val actual = extract(
            """
        <thead> 
            <tr> 
                <th class="blank"></th> 
                <th class="blank level0"></th> 
                <th class="col_heading level0 col0">col_0</th> 
                <th class="col_heading level0 col1">col_1</th> 
                <th class="col_heading level0 col2">col_2</th> 
                <th class="col_heading level0 col3">col_3</th> 
                <th class="col_heading level0 col4">col_4</th> 
            </tr> 
            <tr> 
                <th class="index_name level0">char</th> 
                <th class="index_name level1">color</th> 
                <th class="blank"></th> 
                <th class="blank"></th> 
                <th class="blank"></th> 
                <th class="blank"></th> 
                <th class="blank"></th> 
            </tr>
        </thead>
        """
        )

        val expected = ColumnHeaderLabelsExtractor.Result(
            LegendHeaders(
                row = LeveledHeaderLabel("color", listOf("char"))
            ),
            listOf(
                HeaderLabel("col_0"),
                HeaderLabel("col_1"),
                HeaderLabel("col_2"),
                HeaderLabel("col_3"),
                HeaderLabel("col_4"),
            )
        )
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun leveledColumnsWithLeveledIndex() {
        val actual = extract(
            """
        <thead> 
            <tr> 
                <th class="blank"></th> 
                <th class="blank level0"></th> 
                <th class="col_heading level0 col0" colspan="2">A</th> 
                <th class="col_heading level0 col2" colspan="2">B</th> 
                <th class="col_heading level0 col4">C</th> 
            </tr> 
            <tr> 
                <th class="blank"></th> 
                <th class="blank level1"></th> 
                <th class="col_heading level1 col0">col_0</th> 
                <th class="col_heading level1 col1">col_1</th> 
                <th class="col_heading level1 col2">col_2</th> 
                <th class="col_heading level1 col3">col_3</th> 
                <th class="col_heading level1 col4">col_4</th> 
            </tr> 
            <tr> 
                <th class="index_name level0">char</th> 
                <th class="index_name level1">color</th> 
                <th class="blank"></th> 
                <th class="blank"></th> 
                <th class="blank"></th> 
                <th class="blank"></th> 
                <th class="blank"></th> 
            </tr>
        </thead>
        """
        )

        val expected = ColumnHeaderLabelsExtractor.Result(
            LegendHeaders(
                row = LeveledHeaderLabel("color", listOf("char"))
            ),
            listOf(
                LeveledHeaderLabel("col_0", listOf("A")),
                LeveledHeaderLabel("col_1", listOf("A")),
                LeveledHeaderLabel("col_2", listOf("B")),
                LeveledHeaderLabel("col_3", listOf("B")),
                LeveledHeaderLabel("col_4", listOf("C")),
            )
        )
        assertThat(actual).isEqualTo(expected)
    }

    private fun extract(thead: String): ColumnHeaderLabelsExtractor.Result {
        return ColumnHeaderLabelsExtractor().extract(
            HtmlTableElementProvider(
                headerRowsOwner = Jsoup.parse("<table>$thead</table>").selectFirst("thead")
            ).headerRows()
        )
    }
}