package cms.rendner.intellij.dataframe.core.html.css

import cms.rendner.intellij.dataframe.viewer.core.html.RowsOwnerNodeFilter
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
    fun tableWithMultipleBodiesShouldHaveFisrtBodyAsBodyRowsOwner() {
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