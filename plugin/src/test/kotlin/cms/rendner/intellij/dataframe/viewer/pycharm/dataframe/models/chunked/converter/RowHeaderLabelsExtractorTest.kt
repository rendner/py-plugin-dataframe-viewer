package cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.converter

import cms.rendner.intellij.dataframe.viewer.core.component.models.HeaderLabel
import cms.rendner.intellij.dataframe.viewer.core.component.models.IHeaderLabel
import cms.rendner.intellij.dataframe.viewer.core.component.models.LeveledHeaderLabel
import cms.rendner.intellij.dataframe.viewer.core.html.HtmlTableElementProvider
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
            HtmlTableElementProvider(
                bodyRowsOwner = Jsoup.parse("<table>$tbody</table>").selectFirst("tbody")
            ).bodyRows()
        )
    }
}