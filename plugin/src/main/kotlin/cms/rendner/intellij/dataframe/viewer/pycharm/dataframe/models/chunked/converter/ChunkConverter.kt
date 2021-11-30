package cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.converter

import cms.rendner.intellij.dataframe.viewer.core.component.models.StringValue
import cms.rendner.intellij.dataframe.viewer.core.component.models.StyledValue
import cms.rendner.intellij.dataframe.viewer.core.html.HtmlTableElementProvider
import cms.rendner.intellij.dataframe.viewer.core.html.RowsOwnerNodeFilter
import cms.rendner.intellij.dataframe.viewer.core.html.TableBodyRow
import cms.rendner.intellij.dataframe.viewer.core.html.css.IStyleComputer
import cms.rendner.intellij.dataframe.viewer.core.html.css.RulesExtractor
import cms.rendner.intellij.dataframe.viewer.core.html.css.StyleComputer
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.ChunkData
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.ChunkHeaderLabels
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.ChunkValues
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.ChunkValuesRow
import cms.rendner.intellij.dataframe.viewer.pycharm.dataframe.models.chunked.converter.exceptions.ConvertException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.SmartList
import com.steadystate.css.parser.CSSOMParser
import com.steadystate.css.parser.SACParserCSS3
import org.jsoup.nodes.Document
import org.jsoup.select.NodeTraversor

/**
 * The converter uses [SmartList] where possible to reduce the memory footprint.
 */
open class ChunkConverter(
    private val document: Document
) : IChunkConverter {

    companion object {
        private val logger = Logger.getInstance(ChunkConverter::class.java)
    }

    private val tableElementProvider = createTableElementProvider(document)
    private val rowColumnTranslator:RowColTranslator

    init {
        rowColumnTranslator = createIndexTranslator(document)
    }

    override fun convertText(excludeRowHeader: Boolean, excludeColumnHeader: Boolean): ChunkData {
        return tableElementProvider.let { elementProvider ->
            val bodyRows = elementProvider.bodyRows()

            val columnHeaderLabels = if (excludeColumnHeader) {
                ColumnHeaderLabelsExtractor.EMPTY_RESULT
            } else ColumnHeaderLabelsExtractor().extract(elementProvider.headerRows())

            val rowHeaderLabels = if (excludeRowHeader) {
                emptyList()
            } else RowHeaderLabelsExtractor().extract(bodyRows)

            ChunkData(
                ChunkHeaderLabels(
                    columnHeaderLabels.legend,
                    columnHeaderLabels.columns,
                    rowHeaderLabels
                ),
                ChunkValues(SmartList(bodyRows.map { row -> ChunkValuesRow(row.data.map { StringValue(it.text()) }) }))
            )
        }
    }

    override fun mergeWithStyles(values: ChunkValues): ChunkValues {
        return tableElementProvider.let {
            val styleComputer = createTableStyleComputer(document)
            ChunkValues(
                SmartList(it.bodyRows().zip(values.rows) { tableRow, unstyledRow ->
                    createStyledRow(tableRow, unstyledRow, styleComputer)
                })
            )
        }
    }

    private fun createStyledRow(
        bodyRow: TableBodyRow,
        unstyledRow: ChunkValuesRow,
        styleComputer: IStyleComputer
    ): ChunkValuesRow {
        return ChunkValuesRow(
            SmartList(bodyRow.data.zip(unstyledRow.values) { element, unstyledValue ->
                try {
                    styleComputer.computeStyle(element).let {
                        if(it.isEmpty()) unstyledValue
                        else StyledValue(unstyledValue.text(), it)
                    }
                 } catch (throwable: Throwable) {
                    logger.error("Styles couldn't be computed.", throwable)
                    unstyledValue
                }
            })
        )
    }

    private fun createTableElementProvider(document: Document): HtmlTableElementProvider {
        val table = document.selectFirst("table")
            ?: throw ConvertException("No table-tag found in html document.", document.body().text())
        return table.let {
            val filter = RowsOwnerNodeFilter()
            NodeTraversor.filter(filter, it)
            HtmlTableElementProvider(filter.headerRowsOwner, filter.bodyRowsOwner)
        }
    }

    private fun createIndexTranslator(document: Document): RowColTranslator {
        val metaTags = document.selectFirst("head").select("meta")

        var rowTranslator: IndexTranslator? = null
        var columnTranslator: IndexTranslator? = null
        for (metaTag in metaTags) {
            val name = metaTag.attr("name")

            if(name == "row_indexer") {
                rowTranslator = convertToIndexTranslator(metaTag.attr("content"))
            }
            else if(name == "col_indexer") {
                columnTranslator = convertToIndexTranslator(metaTag.attr("content"))
            }
        }

        return RowColTranslator(rowTranslator ?: NOOPTranslator(), columnTranslator ?: NOOPTranslator())
    }

    private fun convertToIndexTranslator(content: String): IndexTranslator {
        return if( content.startsWith("[")) {
            val strings =  content.subSequence(1, content.length - 1)
                .split(" ")
                .filter { it.isNotEmpty() }
                .map{ it.trim() } // to remove newlines
            SequenceIndex(IntArray(strings.size) { strings[it].toInt() })
        } else {
            OffsetIndex(content.toInt())
        }
    }

    protected open fun createTableStyleComputer(document: Document): IStyleComputer {
        // todo: __prio_2__ fix row/col indices (recalculate indices - offset or indexOffsetProvider are stored in the meta-html tags)
        /*

        todo: first add visual tests (export data) which demonstrate the problem

        The user can define custom css styles for a cell by using 'class="col_heading level0 col1"'
        At the moment we don't adjust these indexes when we fetch chunks - therefore the calculated css should not match
         with the expected result.
        -> Adjust the indices as we do on python side.
         */
        val parser = CSSOMParser(SACParserCSS3())
        @Suppress("UNNECESSARY_SAFE_CALL")
        val ruleSets = document.selectFirst("style")?.let {
            RulesExtractor(parser).extract(it)
        } ?: emptyList()

        return StyleComputer(parser, ruleSets, document)
    }
}