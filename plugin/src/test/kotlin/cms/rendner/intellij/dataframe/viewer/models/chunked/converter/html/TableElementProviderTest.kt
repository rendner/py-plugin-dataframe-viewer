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
package cms.rendner.intellij.dataframe.viewer.models.chunked.converter.html

import org.assertj.core.api.Assertions.assertThat
import org.jsoup.Jsoup
import org.jsoup.select.NodeTraversor
import org.junit.jupiter.api.Test
import utils.measureIt

internal class TableElementProviderTest {

    @Test
    fun testProviderWithEmptyTable() {
        test("empty_table.html") { provider ->
            assertThat(provider).isNotNull
            assertThat(provider.headerRows()).isEmpty()
            assertThat(provider.bodyRows()).isEmpty()
        }
    }

    @Test
    fun testHeaderRowsWithNonEmptyTable() {
        test("example_30_rows_with_styles.html") { provider ->
            assertThat(provider).isNotNull
            assertThat(provider.headerRows().size).isOne

            val headerRow = provider.headerRows().first()
            assertThat(headerRow.element.tagName()).isEqualTo("tr")
            assertThat(headerRow.headers.size).isEqualTo(9)
            headerRow.headers.forEach { h -> assertThat(h.tagName()).isEqualTo("th") }
        }
    }

    @Test
    fun testBodyRowsWithNonEmptyTable() {
        test("example_30_rows_with_styles.html") { provider ->
            assertThat(provider).isNotNull
            assertThat(provider.bodyRows().size).isEqualTo(30)

            provider.bodyRows().forEach { bodyRow ->
                assertThat(bodyRow.element.tagName()).isEqualTo("tr")

                assertThat(bodyRow.headers.size).isOne
                bodyRow.headers.forEach { h -> assertThat(h.tagName()).isEqualTo("th") }

                assertThat(bodyRow.data.size).isEqualTo(8)
                bodyRow.data.forEach { d -> assertThat(d.tagName()).isEqualTo("td") }
            }
        }
    }

    private fun test(filePath: String, testBlock: (provider: HtmlTableElementProvider) -> Unit) {
        val fileContent = javaClass.getResource("/html/css/$filePath")!!.readText()
        Jsoup.parse(fileContent).selectFirst("table").let {
            assertThat(it).isNotNull
            val filter = RowsOwnerNodeFilter()
            NodeTraversor.filter(filter, it)
            val caller = Thread.currentThread().stackTrace[2].methodName
            measureIt(caller) {
                testBlock(HtmlTableElementProvider(filter.headerRowsOwner, filter.bodyRowsOwner))
            }
        }
    }
}