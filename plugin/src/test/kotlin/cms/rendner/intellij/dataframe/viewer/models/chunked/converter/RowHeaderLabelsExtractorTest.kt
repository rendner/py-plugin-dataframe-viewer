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
package cms.rendner.intellij.dataframe.viewer.models.chunked.converter

import cms.rendner.intellij.dataframe.viewer.models.HeaderLabel
import cms.rendner.intellij.dataframe.viewer.models.IHeaderLabel
import cms.rendner.intellij.dataframe.viewer.models.LeveledHeaderLabel
import cms.rendner.intellij.dataframe.viewer.models.chunked.converter.html.TableRowsProvider
import org.assertj.core.api.Assertions
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test

internal class RowHeaderLabelsExtractorTest {

    @Test
    fun defaultRow() {
        val actual = extract(
            """
        <tbody> 
            <tr> 
                <th class="row_heading level0 row0">1</th>
                <td class="data">unused</td> 
            </tr> 
            <tr> 
                <th class="row_heading level0 row1">2</th>
                <td class="data">unused</td> 
            </tr> 
            <tr> 
                <th class="row_heading level0 row2">3</th>
                <td class="data">unused</td> 
            </tr> 
            <tr> 
                <th class="row_heading level0 row3">4</th>
                <td class="data">unused</td> 
            </tr> 
        </tbody>
        """
        )

        val expected = listOf(
            HeaderLabel("1"),
            HeaderLabel("2"),
            HeaderLabel("3"),
            HeaderLabel("4"),
        )
        Assertions.assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun leveledRow() {
        val actual = extract(
            """
        <tbody> 
            <tr> 
                <th class="row_heading level0 row0" rowspan="2">2013</th> 
                <th class="row_heading level1 row0">1</th>
                <td class="data">unused</td> 
            </tr> 
            <tr> 
                <th class="row_heading level1 row1">2</th>
                <td class="data">unused</td> 
            </tr> 
            <tr> 
                <th class="row_heading level0 row2" rowspan="2">2014</th> 
                <th class="row_heading level1 row2">1</th>
                <td class="data">unused</td> 
            </tr> 
            <tr> 
                <th class="row_heading level1 row3">2</th>
                <td class="data">unused</td> 
            </tr> 
        </tbody>
        """
        )

        val expected = listOf(
            LeveledHeaderLabel("1", listOf("2013")),
            LeveledHeaderLabel("2", listOf("2013")),
            LeveledHeaderLabel("1", listOf("2014")),
            LeveledHeaderLabel("2", listOf("2014")),
        )
        Assertions.assertThat(actual).isEqualTo(expected)
    }

    private fun extract(tbody: String): List<IHeaderLabel> {
        return RowHeaderLabelsExtractor().extract(
            TableRowsProvider(
                Jsoup.parse("<table>$tbody</table>").selectFirst("table")
            ).bodyRows
        )
    }
}