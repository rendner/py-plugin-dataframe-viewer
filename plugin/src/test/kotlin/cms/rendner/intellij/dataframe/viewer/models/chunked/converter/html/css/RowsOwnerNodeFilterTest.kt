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
package cms.rendner.intellij.dataframe.viewer.models.chunked.converter.html.css

import cms.rendner.intellij.dataframe.viewer.models.chunked.converter.html.RowsOwnerNodeFilter
import org.assertj.core.api.Assertions.assertThat
import org.jsoup.Jsoup
import org.jsoup.select.NodeTraversor
import org.junit.jupiter.api.Test

internal class RowsOwnerNodeFilterTest {

    @Test
    fun htmlWithoutTableShouldHaveNoOwners() {
        val filter = getPreparedFilter("""<div/>""")

        assertThat(filter.headerRowsOwner).isNull()
        assertThat(filter.bodyRowsOwner).isNull()
    }

    @Test
    fun emptyTableShouldHaveNoOwners() {
        val filter = getPreparedFilter("""<table/>""")

        assertThat(filter.headerRowsOwner).isNull()
        assertThat(filter.bodyRowsOwner).isNull()
    }

    @Test
    fun tableWithHeaderShouldHaveHeaderRowsOwner() {
        val filter = getPreparedFilter(
            """
            <table>
                <thead/>
            </table>
            """
        )

        assertThat(filter.headerRowsOwner)
            .extracting { it!!.tagName() }
            .isEqualTo("thead")
    }

    @Test
    fun tableWithBodyShouldHaveBodyRowsOwner() {
        val filter = getPreparedFilter(
            """
            <table>
                <tbody/>
            </table>
            """
        )

        assertThat(filter.bodyRowsOwner)
            .extracting { it!!.tagName() }
            .isEqualTo("tbody")
    }

    @Test
    fun tableWithMultipleBodiesShouldHaveFirstBodyAsBodyRowsOwner() {
        val filter = getPreparedFilter(
            """
            <table>
                <tbody id="first"/>
                <tbody id="second"/>
            </table>
            """
        )

        assertThat(filter.bodyRowsOwner)
            .extracting { it!!.id() }
            .isEqualTo("first")
    }

    @Test
    fun tableWithHeaderAndBodyShouldHaveHeaderRowsOwnerAndBodyRowsOwner() {
        val filter = getPreparedFilter(
            """
            <table>
                <thead/>
                <tbody/>
            </table>
            """
        )

        assertThat(filter.headerRowsOwner)
            .extracting { it!!.tagName() }
            .isEqualTo("thead")

        assertThat(filter.bodyRowsOwner)
            .extracting { it!!.tagName() }
            .isEqualTo("tbody")
    }

    @Test
    fun tableWithHeaderAfterBodyShouldHaveOnlyBodyRowsOwner() {
        val filter = getPreparedFilter(
            """
            <table>
                <tbody/>
                <thead/>
            </table>
            """
        )

        assertThat(filter.headerRowsOwner).isNull()

        assertThat(filter.bodyRowsOwner)
            .extracting { it!!.tagName() }
            .isEqualTo("tbody")
    }

    @Test
    fun tableWithRowsButWithoutHeaderAndBodyShouldHaveBodyRowsOwner() {
        val filter = getPreparedFilter(
            """
            <table>
                <tr/>
                <tr/>
            </table>
            """
        )

        assertThat(filter.headerRowsOwner).isNull()

        assertThat(filter.bodyRowsOwner)
            .extracting { it!!.tagName() }
            .isEqualTo("tbody")
    }

    private fun getPreparedFilter(html: String): RowsOwnerNodeFilter {
        return RowsOwnerNodeFilter().also {
            NodeTraversor.filter(
                it,
                Jsoup.parseBodyFragment(html).selectFirst("table")
            )
        }
    }
}