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

import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkData
import cms.rendner.intellij.dataframe.viewer.models.chunked.ChunkValues
import cms.rendner.intellij.dataframe.viewer.models.chunked.converter.css.IStyleComputer
import cms.rendner.intellij.dataframe.viewer.models.chunked.converter.css.StyleComputer
import cms.rendner.intellij.dataframe.viewer.models.chunked.converter.exceptions.ConvertException
import cms.rendner.intellij.dataframe.viewer.models.chunked.converter.html.TableRowsProvider
import org.jsoup.nodes.Document

/**
 * Abstract class for extracting data from a html [org.jsoup.nodes.Document] and converting into chunk values.
 *
 * The [document] contains a <table /> tag and maybe a <style /> tag which contains the css style definitions.
 * Extracting the headers and cell values and calculating the styling may take some time before this information
 * can be presented to the user.
 * To reduce this waiting time the extracting of the styled chunk values is split up into two separate parts.
 * The extracted text data, which contains the un-styled values, can already be displayed to the user before
 * starting to extract and compute the css.
 */
abstract class AbstractChunkConverter(
    private val document: Document
) {
    /**
     * Extracts the text for headers and cells.
     *
     * @param excludeRowHeader if result should not include the headers of the rows
     * @param excludeColumnHeader if result should not include the headers of the columns
     * @return the extracted values, cells and headers, of the chunk
     */
    abstract fun extractData(excludeRowHeader: Boolean, excludeColumnHeader: Boolean): ChunkData

    /**
     * Extracts and computes the css to calculate styled values.
     *
     * @param values the already extracted values.
     * @return [values] if no  <style /> tag is present, otherwise the styled values
     */
    abstract fun mergeWithStyles(values: ChunkValues): ChunkValues

    protected fun createTableRowsProvider(): TableRowsProvider {
        val table = document.selectFirst("table")
            ?: throw ConvertException("No table-tag found in html document.", document.body().text())
        return TableRowsProvider(table)
    }

    protected open fun createTableStyleComputer(): IStyleComputer {
        return StyleComputer(document)
    }
}